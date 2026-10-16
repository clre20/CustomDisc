package clre20.customDisc.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 基岩版支援的入口。沒裝 Geyser／Floodgate 時所有方法都安全地什麼都不做。
 */
public final class Bedrock {

    private GeyserHook geyser;
    private FloodgateHook floodgate;

    public void enable(Plugin plugin, boolean packEnabled) {
        Logger log = plugin.getLogger();
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                floodgate = new FloodgateHook();
            } catch (Throwable t) {
                log.warning("Floodgate 整合失敗：" + t.getMessage());
            }
        }
        if (packEnabled && Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            try {
                geyser = new GeyserHook(plugin, log);
                log.info("已連接 Geyser，基岩版玩家登入時會自動送出唱片資源包");
            } catch (Throwable t) {
                geyser = null;
                log.warning("Geyser 整合失敗，基岩版玩家不會收到唱片資源包：" + t.getMessage());
            }
        }
    }

    public boolean geyserActive() {
        return geyser != null;
    }

    public void setPack(Path mcpack) {
        if (geyser != null) {
            geyser.setPack(mcpack);
        }
    }

    public boolean isBedrock(Player player) {
        if (floodgate != null && floodgate.isBedrock(player.getUniqueId())) {
            return true;
        }
        return geyser != null && geyser.isBedrock(player.getUniqueId());
    }

    public void disable() {
        if (geyser != null) {
            geyser.close();
            geyser = null;
        }
        floodgate = null;
    }
}
