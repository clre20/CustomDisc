package clre20.customDisc.bedrock;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/** Floodgate：判斷玩家是不是基岩版。只有伺服器有裝 Floodgate 時才會載入。 */
final class FloodgateHook {

    boolean isBedrock(UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Throwable t) {
            return false;
        }
    }
}
