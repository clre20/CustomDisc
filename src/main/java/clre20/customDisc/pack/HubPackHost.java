package clre20.customDisc.pack;

import clre20.httpHub.api.HttpHubService;
import clre20.httpHub.api.Route;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** 透過 HttpHub 提供資源包：/customdisc/pack.zip。只有在 HttpHub 啟用時才會載入這個類別。 */
final class HubPackHost implements PackHost {

    private static final String PATH = "pack.zip";

    private final Plugin plugin;
    private final HttpHubService hub;
    private volatile Route route;

    private HubPackHost(Plugin plugin, HttpHubService hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** HttpHub 沒有提供服務時回傳 null。 */
    static PackHost create(Plugin plugin) {
        HttpHubService hub = HttpHubService.get();
        return hub == null ? null : new HubPackHost(plugin, hub);
    }

    @Override
    public synchronized void publish(PackBuilder.Built pack) {
        if (pack == null) {
            return;
        }
        Route r = route;
        if (r != null && r.active()) {
            r.update(pack.bytes());
        } else {
            route = hub.serve(plugin, PATH, pack.bytes(), "application/zip");
        }
    }

    @Override
    public String url(Player player, PackBuilder.Built pack) {
        if (pack == null) {
            return null;
        }
        Route r = route;
        if (r == null || !r.active()) {
            // HttpHub 重新啟用過，路徑已被清掉：重新註冊
            publish(pack);
            r = route;
        }
        String url = hub.url(player, r);
        // 帶上雜湊值，內容更新時網址也會不同
        return url == null ? null : url + "?v=" + pack.sha1Hex();
    }

    @Override
    public boolean running() {
        return hub.running();
    }

    @Override
    public int port() {
        return hub.port();
    }

    @Override
    public synchronized void close() {
        Route r = route;
        route = null;
        if (r != null) {
            r.remove();
        }
    }
}
