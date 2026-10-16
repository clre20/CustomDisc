package clre20.customDisc.config;

import clre20.customDisc.CustomDisc;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * config.yml 讀取與自動更新。
 * 插件內建的 config.yml 的 config-version 比玩家的新時：
 * 以新版預設檔為底（保留新版註解），把玩家原本的值填回去，舊檔備份成 config.yml.bak。
 */
public final class PluginConfig {

    private static final String VERSION_KEY = "config-version";

    private final CustomDisc plugin;
    private YamlConfiguration yaml;

    public PluginConfig(CustomDisc plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        YamlConfiguration defaults = loadDefaults();
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);

        int defaultVersion = defaults.getInt(VERSION_KEY, 1);
        int userVersion = user.getInt(VERSION_KEY, 0);
        if (userVersion < defaultVersion) {
            migrate(file, defaults, user, userVersion, defaultVersion);
            user = YamlConfiguration.loadConfiguration(file);
        }

        user.setDefaults(defaults);
        this.yaml = user;
    }

    private YamlConfiguration loadDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    private void migrate(File file, YamlConfiguration defaults, YamlConfiguration user, int from, int to) {
        try {
            Files.copy(file.toPath(), new File(file.getParentFile(), "config.yml.bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            YamlConfiguration merged = loadDefaults();
            for (String key : defaults.getKeys(true)) {
                if (key.equals(VERSION_KEY) || defaults.isConfigurationSection(key)) {
                    continue;
                }
                if (user.contains(key) && !user.isConfigurationSection(key)) {
                    merged.set(key, user.get(key));
                }
            }
            merged.set(VERSION_KEY, to);
            merged.save(file);
            plugin.getLogger().info("config.yml 已從第 " + from + " 版更新到第 " + to + " 版，原本的設定已保留（舊檔備份為 config.yml.bak）");
        } catch (IOException e) {
            plugin.getLogger().warning("config.yml 自動更新失敗：" + e.getMessage());
        }
    }

    public String prefix() {
        return yaml.getString("prefix", "&8[&bC-Disc&8] &r");
    }

    public boolean updateCheckOnStartup() {
        return yaml.getBoolean("update-checker.enabled", true);
    }

    public String packHost() {
        return yaml.getString("resource-pack.host", "0.0.0.0");
    }

    public int packPort() {
        return yaml.getInt("resource-pack.port", 52011);
    }

    public String packPublicUrl() {
        String url = yaml.getString("resource-pack.public-url", "http://127.0.0.1:52011");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public boolean packRequired() {
        return yaml.getBoolean("resource-pack.required", true);
    }

    public String packPrompt() {
        return yaml.getString("resource-pack.prompt", "");
    }

    public String packKickMessage() {
        return yaml.getString("resource-pack.kick-message", "&c必須接受唱片資源包才能進入伺服器");
    }

    public boolean bedrockEnabled() {
        return yaml.getBoolean("bedrock.enabled", true);
    }

    public boolean nativeMode() {
        return yaml.getBoolean("playback.native-mode", true);
    }

    public int chatTimeoutSeconds() {
        return Math.max(5, yaml.getInt("chat-input.timeout", 60));
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
