package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 攔截聊天室輸入（輸入的文字不會被其他玩家看到）。 */
public final class ChatInput implements Listener {

    private record Pending(Consumer<String> onInput, Runnable onCancel, BukkitTask timeout) {
    }

    private final CustomDisc plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(CustomDisc plugin) {
        this.plugin = plugin;
    }

    /**
     * 等待玩家輸入。輸入取消字、逾時或離線時呼叫 onCancel（離線時不呼叫）。
     */
    public void ask(Player player, Consumer<String> onInput, Runnable onCancel) {
        cancelSilently(player.getUniqueId());
        int seconds = plugin.config().chatTimeoutSeconds();
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p = pending.remove(player.getUniqueId());
            if (p != null && player.isOnline()) {
                clre20.customDisc.config.Msg.send(player, "&e輸入逾時，已返回");
                p.onCancel().run();
            }
        }, seconds * 20L);
        pending.put(player.getUniqueId(), new Pending(onInput, onCancel, timeout));
    }

    public boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancelSilently(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p != null) {
            p.timeout().cancel();
        }
    }

    public void clear() {
        for (Pending p : pending.values()) {
            p.timeout().cancel();
        }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        p.timeout().cancel();
        String text = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim();
        String cancelWord = plugin.config().cancelWord();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase(cancelWord)) {
                p.onCancel().run();
            } else {
                p.onInput().accept(text);
            }
        });
    }

    /** 舊版聊天事件也一起擋掉，避免其他聊天插件把輸入內容廣播出去。 */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (pending.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getRecipients().clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelSilently(event.getPlayer().getUniqueId());
    }
}
