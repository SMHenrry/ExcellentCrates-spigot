package su.nightexpress.excellentcrates.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.util.Players;

public final class BedrockUtils {

    private BedrockUtils() {

    }

    public static boolean isBedrockPlayer(@NotNull Player player) {
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");

        if (floodgate != null && floodgate.isEnabled()) {
            try {
                return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
            }
            catch (IllegalStateException | LinkageError ignored) {
                // Fall through to NightCore's compatibility check.
            }
        }

        return Players.isBedrock(player);
    }
}
