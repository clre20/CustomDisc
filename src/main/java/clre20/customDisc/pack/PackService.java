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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private volatile Set<String> builtIds = Set.of();

    public PackService(CustomDisc plugin) {
        this.plugin = plugin;
        this.http = new PackHttpServer(() -> javaPack);
    }

    public File generatedFolder() {
        return new File(plugin.getDataFolder(), "generated");
    }

    /** 有裝 HttpHub 時由它提供下載（不另外開端口）；否則為 null。 */
    private volatile PackHost hub;

    /**
     * 開始提供資源包下載：config 允許且 HttpHub 已啟用時改用 HttpHub，否則用內建的 HTTP 伺服器。
     * 啟動、/cd reload、HttpHub 啟用或停用時呼叫。
     */
    public void startHttp() {
        if (plugin.config().useHttpHub() && hubEnabled()) {
            PackHost h = hub;
            if (h == null) {
                h = HubPackHost.create(plugin);
            }
            if (h != null) {
                hub = h;
                http.stop();
                h.publish(javaPack);
                plugin.getLogger().info(plugin.messages().console("httphub-used", "port", h.port()));
                if (!h.running()) {
                    plugin.getLogger().warning(plugin.messages().console("httphub-not-running"));
                }
                return;
            }
        }
        closeHub();
        startOwnHttp();
    }

    private static boolean hubEnabled() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("HttpHub");
        return p != null && p.isEnabled();
    }

    private void closeHub() {
        PackHost h = hub;
        hub = null;
        if (h != null) {
            h.close();
        }
    }

    /** HttpHub 在伺服器運作中被啟用或停用時，切換下載方式。 */
    @EventHandler
    public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("HttpHub") && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::startHttp);
        }
    }

    @EventHandler
    public void onPluginDisable(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("HttpHub") && plugin.isEnabled() && hub != null) {
            hub = null;
            plugin.getLogger().info(plugin.messages().console("httphub-lost"));
            Bukkit.getScheduler().runTask(plugin, this::startHttp);
        }
    }

    private void startOwnHttp() {
        String host = plugin.config().packHost();
        int port = plugin.config().packPort();
        try {
            http.start(host, port);
            String url = plugin.config().packPublicUrl();
            plugin.getLogger().info(plugin.messages().console("http-started", "host", host, "port", port,
                    "url", url != null ? url : plugin.messages().console("url-auto")));
        } catch (IOException e) {
            plugin.getLogger().severe(plugin.messages().console("http-failed", "port", port, "reason", e.getMessage()));
        }
    }

    public void stopHttp() {
        closeHub();
        http.stop();
    }

    /** 正在進行的打包；打包中又有新的要求時，等這次結束後只再打包一次。 */
    private CompletableFuture<Boolean> running;
    private CompletableFuture<Boolean> queued;
    private boolean queuedPush;

    /**
     * 重新產生資源包（在主執行緒呼叫）。內容有變時推送給線上玩家。
     *
     * @param push 內容有變時是否推送給線上的 Java 版玩家
     * @return 完成時回傳 Java 版資源包內容是否改變
     */
    public CompletableFuture<Boolean> rebuild(boolean push) {
        if (running != null && !running.isDone()) {
            queuedPush |= push;
            if (queued == null) {
                queued = new CompletableFuture<>();
            }
            return queued;
        }
        running = build(push);
        running.whenComplete((changed, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            CompletableFuture<Boolean> next = queued;
            if (next != null) {
                boolean nextPush = queuedPush;
                queued = null;
                queuedPush = false;
                rebuild(nextPush).whenComplete((c, e) -> next.complete(c != null && c));
            }
        }));
        return running;
    }

    private CompletableFuture<Boolean> build(boolean push) {
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
                PackBuilder.Built old = javaPack;
                boolean changed = old == null || !old.sha1Hex().equals(java.sha1Hex());
                if (changed) {
                    File gen = generatedFolder();
                    if (!gen.isDirectory() && !gen.mkdirs()) {
                        throw new IOException(plugin.messages().console("folder-failed", "path", gen.getPath()));
                    }
                    Files.write(new File(gen, "CustomDisc-java.zip").toPath(), java.bytes());
                }

                File mcpack = null;
                if (bedrock) {
                    PackBuilder.Built be = PackBuilder.buildBedrock(library.entries(), resolved, warnings::add);
                    if (!be.sha1Hex().equals(bedrockHash)) {
                        mcpack = writeBedrock(be);
                        bedrockHash = be.sha1Hex();
                    }
                }

                javaPack = java;
                PackHost h = hub;
                if (h != null && changed) {
                    h.publish(java);
                }
                Set<String> ids = new HashSet<>();
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
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().severe(plugin.messages().console("pack-failed", "reason", e.getMessage()));
                    result.complete(false);
                });
            }
        });
        return result;
    }

    /** 基岩版包用不同檔名存放，避免 Geyser 正在傳送舊檔時被覆蓋。 */
    private File writeBedrock(PackBuilder.Built be) throws IOException {
        File dir = new File(generatedFolder(), "bedrock");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException(plugin.messages().console("folder-failed", "path", dir.getPath()));
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

    /**
     * 這位玩家的下載網址。public-url 是 auto 時，用玩家連線時輸入的位址加上插件的端口；
     * 取不到位址時回傳 null。
     */
    public String url(Player player) {
        PackBuilder.Built pack = javaPack;
        PackHost h = hub;
        if (h != null) {
            return h.url(player, pack);
        }
        String base = plugin.config().packPublicUrl();
        if (base == null) {
            String host = connectHost(player);
            if (host == null) {
                return null;
            }
            base = "http://" + (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + plugin.config().packPort();
        }
        return base + "/customdisc-" + (pack == null ? "0" : pack.sha1Hex()) + ".zip";
    }

    /** 網域名稱或 IPv4。 */
    private static final java.util.regex.Pattern HOST = java.util.regex.Pattern.compile("[A-Za-z0-9.-]{1,253}");
    /** IPv6。 */
    private static final java.util.regex.Pattern HOST_V6 = java.util.regex.Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /**
     * 玩家連線時輸入的位址（握手封包裡的主機名稱）。
     * 只接受網域名稱、IPv4、IPv6 的字元，其他內容（例如 Forge 附加的標記）一律不用。
     */
    static String connectHost(Player player) {
        java.net.InetSocketAddress vh;
        try {
            vh = player.getVirtualHost();
        } catch (Throwable t) {
            return null;
        }
        if (vh == null) {
            return null;
        }
        String host = vh.getHostString();
        if (host == null) {
            return null;
        }
        int nul = host.indexOf('\0');
        if (nul >= 0) {
            host = host.substring(0, nul);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (HOST.matcher(host).matches() || (host.indexOf(':') >= 0 && HOST_V6.matcher(host).matches())) {
            return host;
        }
        return null;
    }

    /** 送資源包給一位玩家（基岩版玩家略過，他們由 Geyser 處理）。 */
    public void send(Player player) {
        PackBuilder.Built pack = javaPack;
        if (pack == null || plugin.bedrock().isBedrock(player)) {
            return;
        }
        String url = url(player);
        if (url == null) {
            plugin.getLogger().warning(plugin.messages().console(hub != null ? "pack-no-host-hub" : "pack-no-host",
                    "player", player.getName()));
            return;
        }
        player.addResourcePack(PACK_ID, url, pack.sha1(),
                Text.legacySection(plugin.messages().get("pack.prompt")), plugin.config().packRequired());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // 稍等一下再送，避免和伺服器本身的資源包擠在同一刻
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
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
                    player.kick(plugin.messages().component("pack.kick"));
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL -> plugin.getLogger().warning(plugin.messages().console(
                    "pack-download-failed", "player", player.getName(), "status", event.getStatus(), "url", url(player)));
            default -> {
            }
        }
    }
}
