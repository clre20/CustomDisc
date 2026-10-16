package clre20.customDisc.item;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 讓已發出去的唱片／號角自動更新：進服、打開容器、撿起物品時檢查。 */
public final class ItemRefreshListener implements Listener {

    private final CustomDisc plugin;

    public ItemRefreshListener(CustomDisc plugin) {
        this.plugin = plugin;
    }

    /** 更新一個容器裡所有過期的自訂物品。 */
    public void refresh(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack fresh = plugin.items().refresh(contents[i]);
            if (fresh != null) {
                contents[i] = fresh;
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    public void refresh(Player player) {
        refresh(player.getInventory());
        refresh(player.getEnderChest());
    }

    public void refreshOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof Menu)) {
                refresh(top);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory top = event.getInventory();
        if (top.getHolder() instanceof Menu) {
            return;
        }
        refresh(top);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack fresh = plugin.items().refresh(event.getItem().getItemStack());
        if (fresh != null) {
            event.getItem().setItemStack(fresh);
        }
    }
}
