package clre20.customDisc.config;

import clre20.customDisc.CustomDisc;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * config.yml。啟動與重載時自動補上缺少的設定，原本的值保留。
 * 所有讀取都有預設值，設定檔寫錯也不會出錯。
 */
public final class PluginConfig {

    private final CustomDisc plugin;
    private YamlConfiguration yaml = new YamlConfiguration();
    private int lastFilled;

    public PluginConfig(CustomDisc plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlFiles.Loaded loaded = YamlFiles.load(plugin, "config.yml", PluginConfig::migrate,
                (file, reason) -> plugin.getLogger().warning("config.yml: " + reason));
        yaml = loaded.yaml();
        lastFilled = loaded.filled();
        migrateOldDefaults();
    }

    /** 1.1.0 以前的預設值（寫死的網址、和 PMonitor 衝突的 52011 端口）。 */
    private static final String OLD_URL = "http://pg.yucl.qzz.io:52011";

    /**
     * 使用者沒改過的舊預設值換成新的：public-url → auto，同時 port 52011 → 52012。
     * 直接改文字，保留檔案裡的註解。
     */
    private void migrateOldDefaults() {
        if (!OLD_URL.equals(yaml.getString("resource-pack.public-url"))) {
            return;
        }
        java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
        try {
            String text = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            text = text.replace("public-url: \"" + OLD_URL + "\"", "public-url: \"auto\"")
                    .replace("public-url: '" + OLD_URL + "'", "public-url: 'auto'");
            if (yaml.getInt("resource-pack.port") == 52011) {
                text = text.replaceFirst("(?m)^(\\s*port:\\s*)52011\\s*$", "$152012");
            }
            java.nio.file.Files.writeString(file.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
            yaml = YamlFiles.load(plugin, "config.yml", null, (f, r) -> { }).yaml();
            migrated = true;
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("config.yml: " + e.getMessage());
        }
    }

    private boolean migrated;

    /** 這次載入時是否換掉了舊預設值（給後台訊息用）。 */
    public boolean migratedDefaults() {
        boolean m = migrated;
        migrated = false;
        return m;
    }

    /** 1.0.x 的 update-checker.enabled 改名成 check-on-startup。 */
    private static void migrate(YamlConfiguration user, YamlConfiguration merged) {
        if (user.contains("update-checker.enabled") && !user.contains("update-checker.check-on-startup")) {
            merged.set("update-checker.check-on-startup", user.getBoolean("update-checker.enabled", true));
        }
    }

    public int lastFilled() {
        return lastFilled;
    }

    public String prefix() {
        return yaml.getString("prefix", "&8[&bC-Disc&8] &r");
    }

    public boolean updateCheckOnStartup() {
        return yaml.getBoolean("update-checker.check-on-startup", true);
    }

    public boolean updateNotifyOnJoin() {
        return yaml.getBoolean("update-checker.notify-on-join", true);
    }

    public String packHost() {
        return yaml.getString("resource-pack.host", "0.0.0.0");
    }

    public int packPort() {
        return clamp(yaml.getInt("resource-pack.port", 52012), 1, 65535);
    }

    /** 玩家下載網址；回傳 null 代表 auto（用玩家連線時的位址）。 */
    public String packPublicUrl() {
        String url = yaml.getString("resource-pack.public-url", "auto");
        if (url == null || url.isBlank() || url.trim().equalsIgnoreCase("auto")) {
            return null;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** 有裝 HttpHub 時是否改由它提供下載。 */
    public boolean useHttpHub() {
        return yaml.getBoolean("resource-pack.use-httphub", true);
    }

    public boolean packRequired() {
        return yaml.getBoolean("resource-pack.required", true);
    }

    public boolean bedrockEnabled() {
        return yaml.getBoolean("bedrock.enabled", true);
    }

    public boolean nativeMode() {
        return yaml.getBoolean("playback.native-mode", true);
    }

    public int chatTimeoutSeconds() {
        return clamp(yaml.getInt("chat-input.timeout", 60), 5, 600);
    }

    public String cancelWord() {
        return yaml.getString("chat-input.cancel-word", "取消");
    }

    public int defaultDiscVolume() {
        return clamp(yaml.getInt("defaults.disc-volume", 100), 0, 100);
    }

    public int defaultDiscRange() {
        return clamp(yaml.getInt("defaults.disc-range", 64), 1, 512);
    }

    public int defaultHornVolume() {
        return clamp(yaml.getInt("defaults.horn-volume", 100), 0, 100);
    }

    public int defaultHornRange() {
        return clamp(yaml.getInt("defaults.horn-range", 256), 1, 512);
    }

    public int defaultHornCooldown() {
        return clamp(yaml.getInt("defaults.horn-cooldown", 7), 1, 3600);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
