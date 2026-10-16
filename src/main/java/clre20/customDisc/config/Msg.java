package clre20.customDisc.config;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.util.Text;
import org.bukkit.command.CommandSender;

/** 傳送帶前綴的訊息。 */
public final class Msg {

    private Msg() {
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(Text.color(CustomDisc.get().config().prefix() + text));
    }

    /** 不加前綴（用在多行清單的內容行）。 */
    public static void raw(CommandSender to, String text) {
        to.sendMessage(Text.color(text));
    }
}
