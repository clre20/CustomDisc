package clre20.customDisc.pack;

import org.bukkit.entity.Player;

/** 由其他插件提供資源包下載（目前是 HttpHub）。用介面隔開，沒裝 HttpHub 時不會載入它的類別。 */
interface PackHost {

    /** 換成新的資源包內容。 */
    void publish(PackBuilder.Built pack);

    /** 這位玩家的下載網址；取不到位址時回傳 null。 */
    String url(Player player, PackBuilder.Built pack);

    /** 對方的 HTTP 伺服器是否在運作。 */
    boolean running();

    int port();

    /** 移除註冊的路徑。 */
    void close();
}
