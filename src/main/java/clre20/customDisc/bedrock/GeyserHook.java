package clre20.customDisc.bedrock;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Geyser 整合：基岩版玩家登入時送出插件產生的 .mcpack。
 * 只有伺服器有裝 Geyser 時才會載入這個類別。
 */
final class GeyserHook {

    private final EventRegistrar registrar;
    private final Logger logger;
    private volatile ResourcePack pack;

    GeyserHook(Object plugin, Logger logger) {
        this.registrar = EventRegistrar.of(plugin);
        this.logger = logger;
        GeyserApi.api().eventBus().subscribe(registrar, SessionLoadResourcePacksEvent.class, this::onLoadPacks);
    }

    private void onLoadPacks(SessionLoadResourcePacksEvent event) {
        ResourcePack p = pack;
        if (p == null) {
            return;
        }
        try {
            event.register(p, new ResourcePackOption<?>[0]);
        } catch (Exception e) {
            logger.warning(clre20.customDisc.CustomDisc.get().messages().console("bedrock-send-failed",
                    "reason", String.valueOf(e.getMessage())));
        }
    }

    void setPack(Path path) {
        if (path == null) {
            pack = null;
            return;
        }
        try {
            pack = ResourcePack.create(PackCodec.path(path));
        } catch (Exception e) {
            pack = null;
            logger.warning(clre20.customDisc.CustomDisc.get().messages().console("bedrock-load-failed",
                    "reason", String.valueOf(e.getMessage())));
        }
    }

    boolean isBedrock(UUID uuid) {
        try {
            return GeyserApi.api().connectionByUuid(uuid) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    void close() {
        try {
            GeyserApi.api().eventBus().unregisterAll(registrar);
        } catch (Throwable ignored) {
        }
    }
}
