package clre20.customDisc.pack;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 管理 Java 版與基岩版資源包：產生、用內建 HTTP 伺服器發送、推送給玩家。
 */
public final class PackService implements Listener {

    /** 固定的資源包 ID，和 server.properties 的資源包分開，更新時會取代舊的。 */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("customdisc:java-pack".getBytes(StandardCharsets.UTF_8));

    private final CustomDisc plugin;
    private final PackHttpServer http;
    private volatile PackBuilder.Built javaPack;
    private volatile String bedrockHash;
    /** 目前資源包裡有的音檔 id（試聽用）。 */
    private volatile java.util.Set<String> builtIds = java.util.Set.of();

    public PackService(CustomDisc plugin) {
        this.plugin = plugin;
        this.http = new PackHttpServer(() -> javaPack);
    }

    public File generatedFolder() {
        return new File(plugin.getDataFolder(), "generated");
    }

    public void startHttp() {
        String host = plugin.config().packHost();
        int port = plugin.config().packPort();
        try {
            http.start(host, port);
            plugin.getLogger().info("資源包 HTTP 伺服器已啟動：" + host + ":" + port + "（玩家下載網址 " + plugin.config().packPublicUrl() + "）");
        } catch (IOException e) {
            plugin.getLogger().severe("資源包 HTTP 伺服器無法啟動（端口 " + port + "）：" + e.getMessage()
                    + "。請確認端口沒有被其他程式使用。");
        }
    }

    public void stopHttp() {
        http.stop();
    }

    /**
     * 重新產生資源包。內容有變時推送給線上玩家。
     *
     * @param push 內容有變時是否推送給線上的 Java 版玩家
     * @return 完成時回傳 Java 版資源包內容是否改變
     */
    public CompletableFuture<Boolean> rebuild(boolean push) {
        // 在主執行緒複製資料，打包在背景執行
        List<CustomSound> sounds = new ArrayList<>();
        for (CustomSound s : plugin.store().all()) {
            sounds.add(s.copy());
        }
        boolean bedrock = plugin.config().bedrockEnabled() && plugin.bedrock().geyserActive();
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SoundLibrary library = plugin.library();
                library.scan();
                List<String> warnings = new ArrayList<>();
                Map<CustomSound, SoundLibrary.Entry> resolved = PackBuilder.resolve(sounds, library, warnings::add);

                PackBuilder.Built java = PackBuilder.buildJava(library.entries(), resolved, warnings::add);
                File gen = generatedFolder();
                if (!gen.isDirectory() && !gen.mkdirs()) {
                    throw new IOException("無法建立 " + gen);
                }
                Files.write(new File(gen, "CustomDisc-java.zip").toPath(), java.bytes());

                File mcpack = null;
                if (bedrock) {
                    PackBuilder.Built be = PackBuilder.buildBedrock(library.entries(), resolved, warnings::add);
                    if (!be.sha1Hex().equals(bedrockHash)) {
                        mcpack = writeBedrock(be);
                        bedrockHash = be.sha1Hex();
                    }
                }

                PackBuilder.Built old = javaPack;
                boolean changed = old == null || !old.sha1Hex().equals(java.sha1Hex());
                javaPack = java;
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (SoundLibrary.Entry en : library.entries()) {
                    if (en.valid()) {
                        ids.add(en.id());
                    }
                }
                builtIds = ids;
                File finalMcpack = mcpack;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String w : warnings) {
                        plugin.getLogger().warning(w);
                    }
                    if (finalMcpack != null) {
                        plugin.bedrock().setPack(finalMcpack.toPath());
                    }
                    if (changed && push && old != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            send(p);
                        }
                    }
                    result.complete(changed);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("產生資源包失敗：" + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> result.complete(false));
            }
        });
        return result;
    }

    /** 基岩版包用不同檔名存放，避免 Geyser 正在傳送舊檔時被覆蓋。 */
    private File writeBedrock(PackBuilder.Built be) throws IOException {
        File dir = new File(generatedFolder(), "bedrock");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("無法建立 " + dir);
        }
        File target = new File(dir, "CustomDisc-" + be.sha1Hex().substring(0, 12) + ".mcpack");
        Files.write(target.toPath(), be.bytes());
        File[] old = dir.listFiles((d, n) -> n.endsWith(".mcpack") && !n.equals(target.getName()));
        if (old != null) {
            for (File f : old) {
                // 保留一小段時間再刪，正在下載的玩家才不會中斷
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> f.delete(), 20L * 120);
            }
        }
        return target;
    }

    /** 這個音檔是否已經在玩家目前的資源包裡（新加入的音檔要 reload 後才有）。 */
    public boolean contains(String id) {
        return builtIds.contains(id);
    }

    public String url() {
        PackBuilder.Built pack = javaPack;
        return plugin.config().packPublicUrl() + "/customdisc-" + (pack == null ? "0" : pack.sha1Hex()) + ".zip";
    }

    /** 送資源包給一位玩家（基岩版玩家略過，他們由 Geyser 處理）。 */
    public void send(Player player) {
        PackBuilder.Built pack = javaPack;
        if (pack == null || plugin.bedrock().isBedrock(player)) {
            return;
        }
        player.addResourcePack(PACK_ID, url(), pack.sha1(),
                Text.legacySection(plugin.config().packPrompt()), plugin.config().packRequired());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 稍等一下再送，避免和伺服器本身的資源包擠在同一刻
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case DECLINED -> {
                if (plugin.config().packRequired()) {
                    player.kick(Text.color(plugin.config().packKickMessage()));
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL -> plugin.getLogger().warning(player.getName()
                    + " 下載唱片資源包失敗（" + event.getStatus() + "）。請確認 public-url 外部連得到：" + url());
            default -> {
            }
        }
    }
}
