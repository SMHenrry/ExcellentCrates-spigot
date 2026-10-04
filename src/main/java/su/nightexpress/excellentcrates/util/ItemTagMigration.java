package su.nightexpress.excellentcrates.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.inventory.ItemStack;
import su.nightexpress.nightcore.util.ItemTag;
import su.nightexpress.nightcore.util.Reflex;
import su.nightexpress.nightcore.util.Version;
import su.nightexpress.nightcore.util.nbt.NbtUtil;

import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.math.BigInteger;
import java.util.List;

/** Repairs tooltip references that vanilla's item data fixer leaves behind in 26.3. */
public final class ItemTagMigration {

    private ItemTagMigration() {}

    @NotNull
    public static ItemTag migrate(@NotNull ItemTag source) {
        String text = source.getTag();
        if (!text.contains("map_color") && !text.contains("swing_animation")) return source;

        Object tag = NbtUtil.tagFromString(text);
        if (tag == null || !Access.COMPOUND.isInstance(tag)) return source;

        return migrateStack(tag) ? new ItemTag(tag.toString(), source.getDataVersion()) : source;
    }

    /** Legacy ExcellentCrates Items lists store uncompressed binary NBT as base-32 integers. */
    @Nullable
    public static ItemStack decompress(@NotNull String encoded) {
        if (encoded.isBlank()) return null;
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(new BigInteger(encoded, 32).toByteArray()));
        Object tag = Reflex.invokeMethod(LegacyAccess.READ, null, input);
        if (tag == null) return null;
        migrateStack(tag);
        return NbtUtil.tagToItemStack(tag, Version.MC_1_21.getDataVersion());
    }

    private static boolean migrateStack(Object stack) {
        Object components = get(stack, "components");
        if (components == null) return false;

        boolean changed = false;
        Object hidden = get(get(components, "minecraft:tooltip_display"), "hidden_components");
        if (hidden instanceof List<?> list) {
            // Edit the NBT list directly to preserve numeric types and arbitrary custom data.
            for (int index = list.size() - 1; index >= 0; index--) {
                Object entry = list.get(index);
                if (!Access.STRING.isInstance(entry)) continue;
                String name = (String) Reflex.invokeMethod(Access.STRING_VALUE, entry);
                if ("minecraft:map_color".equals(name) || "map_color".equals(name)
                    || "minecraft:swing_animation".equals(name) || "swing_animation".equals(name)) {
                    list.remove(index);
                    changed = true;
                    if (name.endsWith("swing_animation")) {
                        addHidden(list, "minecraft:attack_animation");
                        addHidden(list, "minecraft:interact_animation");
                    }
                }
            }
        }

        Object container = get(components, "minecraft:container");
        if (container instanceof List<?> list) {
            for (Object entry : list) changed |= migrateStack(get(entry, "item"));
        }
        for (String name : List.of("minecraft:bundle_contents", "minecraft:charged_projectiles")) {
            Object contents = get(components, name);
            if (contents instanceof List<?> list) {
                for (Object item : list) changed |= migrateStack(item);
            }
        }
        changed |= migrateStack(get(components, "minecraft:use_remainder"));
        return changed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addHidden(List list, String name) {
        Object tag = Reflex.invokeMethod(Access.STRING_OF, null, name);
        if (tag == null) throw new IllegalStateException("Could not create tooltip component reference");
        if (!list.contains(tag)) list.add(tag);
    }

    private static Object get(Object compound, String key) {
        return Access.COMPOUND.isInstance(compound) ? Reflex.invokeMethod(Access.GET, compound, key) : null;
    }

    // Paper 26.3 uses Mojang names; keep NMS out of the compile-time dependencies.
    private static final class Access {
        private static final Class<?> COMPOUND = Reflex.safeClass("net.minecraft.nbt", "CompoundTag");
        private static final Class<?> STRING = Reflex.safeClass("net.minecraft.nbt", "StringTag");
        private static final Method GET = Reflex.safeMethod(COMPOUND, "get", String.class);
        private static final Method STRING_VALUE = Reflex.safeMethod(STRING, "value");
        private static final Method STRING_OF = Reflex.safeMethod(STRING, "valueOf", String.class);
    }

    private static final class LegacyAccess {
        private static final Class<?> NBT_IO = Reflex.safeClass("net.minecraft.nbt", "NbtIo");
        private static final Method READ = Reflex.safeMethod(NBT_IO, "read", DataInput.class);
    }
}
