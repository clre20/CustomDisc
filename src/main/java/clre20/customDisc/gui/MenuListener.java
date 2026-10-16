package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 箱子 UI 的點擊處理：一律只接受左鍵，其他點擊方式全部取消。 */
public final class MenuListener implements Listener {

    private final CustomDisc plugin;

    public MenuListener(CustomDisc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        // 下一個 tick 再執行，避免在點擊事件中切換介面
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
                menu.click(raw, player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (menu.session.navigating()) {
            return;
        }
        // 等一個 tick 看看是不是換到下一個介面或在等聊天室輸入；都不是代表玩家自己關掉
        Bukkit.getScheduler().runTask(plugin, () -> plugin.editor().checkClosed(player, menu.session));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.editor().end(event.getPlayer(), false);
    }
}
