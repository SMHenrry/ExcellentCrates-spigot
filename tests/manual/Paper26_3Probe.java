import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import su.nightexpress.excellentcrates.util.ItemHelper;
import su.nightexpress.excellentcrates.util.ItemTagMigration;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.ItemTag;
import su.nightexpress.nightcore.util.nbt.NbtUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.math.BigInteger;

/** Run only on a disposable Paper 26.3 server with this fork and NightCore 2.16.6. */
public class Paper26_3Probe extends JavaPlugin {
    private static final String CUSTOM = "{b:1b,s:2s,l:3L,f:1.25f,d:2.5d,bytes:[B;1b,2b],ints:[I;1,2],longs:[L;1L,2L],text:\"minecraft:map_color\"}";
    private static final String ITEM = "{id:\"minecraft:diamond\",count:3,components:{\"minecraft:custom_data\":" + CUSTOM
        + ",\"minecraft:custom_name\":\"Probe\",\"minecraft:tooltip_display\":{hidden_components:[\"minecraft:map_color\",\"minecraft:swing_animation\",\"minecraft:enchantments\"]}}}";

    @Override
    public void onEnable() {
        getServer().getScheduler().runTask(this, () -> {
            try {
                runChecks();
                getLogger().info("COMPATIBILITY CHECKS PASSED");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "COMPATIBILITY CHECKS FAILED", error);
            } finally {
                if (Boolean.getBoolean("crates.probe.stop")) getServer().shutdown();
            }
        });
    }

    private void runChecks() throws Exception {
        for (int version : new int[]{4890, 4903, 5023, 10000, -1}) {
            ItemTag source = new ItemTag(ITEM, version);
            ItemTag migrated = ItemTagMigration.migrate(source);
            check(migrated.getDataVersion() == version, "source data version retained");
            check(migrated.getTag().contains("minecraft:attack_animation"), "attack tooltip hidden");
            check(migrated.getTag().contains("minecraft:interact_animation"), "interaction tooltip hidden");
            check(ItemTagMigration.migrate(migrated) == migrated, "migration is idempotent");
            assertItem(migrated.getItemStack());
        }

        String clean = "{id:\"minecraft:diamond\",count:3,components:{\"minecraft:custom_data\":" + CUSTOM + "}}";
        ItemTag cleanTag = new ItemTag(clean, 5023);
        check(ItemTagMigration.migrate(cleanTag) == cleanTag, "custom strings remain untouched");
        String nested = "{id:\"minecraft:bundle\",count:1,components:{\"minecraft:bundle_contents\":[" + ITEM + "]}}";
        ItemTag bundle = ItemTagMigration.migrate(new ItemTag(nested, 4903));
        ItemStack bundleItem = bundle.getItemStack();
        check(bundleItem != null, "nested bundle decodes");
        check(bundle.getTag().contains("minecraft:attack_animation"), "nested item migrated");
        for (String contents : new String[]{
            "\"minecraft:container\":[{slot:0,item:" + ITEM + "}]",
            "\"minecraft:charged_projectiles\":[" + ITEM + "]",
            "\"minecraft:use_remainder\":" + ITEM}) {
            ItemTag nestedTag = ItemTagMigration.migrate(new ItemTag("{id:\"minecraft:crossbow\",count:1,components:{" + contents + "}}", 4903));
            check(nestedTag.getTag().contains("minecraft:attack_animation"), "nested item migrated: " + contents);
            check(nestedTag.getItemStack() != null, "nested item decodes");
        }
        ItemTag unknown = new ItemTag("{id:\"minecraft:diamond\",count:1,components:{\"minecraft:tooltip_display\":{hidden_components:[\"example:unknown\"]}}}", 4903);
        check(ItemTagMigration.migrate(unknown) == unknown, "unrelated unknown references retained");

        for (String format : new String[]{"modern", "tag", "itemtag"}) {
            FileConfig config = new FileConfig(new File(getDataFolder(), format + ".yml"));
            if (format.equals("modern")) {
                config.set("Item.Provider", "vanilla");
                config.set("Item.Data", new ItemTag(ITEM, 4903));
            } else {
                config.set("Item.Type", "vanilla");
                if (format.equals("tag")) config.set("Item.Tag", new ItemTag(ITEM, 4903));
                else config.set("Item.ItemTag", ITEM);
            }
            assertItem(ItemHelper.read(config, "Item").orElseThrow().getItemStack());
            check(config.getString("Item.Data.Value").contains("minecraft:attack_animation"), "migrated config persisted: " + format);
            assertItem(ItemHelper.read(config, "Item").orElseThrow().getItemStack());
        }

        Object compound = NbtUtil.tagFromString(ITEM);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Class.forName("net.minecraft.nbt.NbtIo").getMethod("write", compound.getClass(), DataOutput.class)
            .invoke(null, compound, new DataOutputStream(bytes));
        assertItem(ItemTagMigration.decompress(new BigInteger(1, bytes.toByteArray()).toString(32)));
    }

    private static void assertItem(ItemStack item) throws Exception {
        check(item != null && item.getAmount() == 3, "item and amount preserved");
        Object result = NbtUtil.tagFromItemStack(item);
        Object components = get(result, "components");
        Object originalComponents = get(NbtUtil.tagFromString(ITEM), "components");
        check(get(components, "minecraft:custom_name").equals(get(originalComponents, "minecraft:custom_name")), "custom name preserved");
        Object custom = get(components, "minecraft:custom_data");
        check(custom.equals(NbtUtil.tagFromString(CUSTOM)), "custom NBT types and values preserved");
        Object tooltip = get(components, "minecraft:tooltip_display");
        String hidden = get(tooltip, "hidden_components").toString();
        check(!hidden.contains("map_color") && !hidden.contains("swing_animation"), "no obsolete hidden references");
        check(hidden.contains("enchantments"), "existing tooltip flag preserved");
    }

    private static Object get(Object tag, String name) throws Exception {
        return tag.getClass().getMethod("get", String.class).invoke(tag, name);
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
