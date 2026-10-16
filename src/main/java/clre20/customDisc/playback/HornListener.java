package clre20.customDisc.playback;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Msg;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.pack.DatapackService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 號角。冷卻照原版：綁在玩家身上，吹任何一支號角後所有山羊角一起冷卻。
 * <ul>
 *   <li>原版號角、原生模式的自訂號角：交給原版（冷卻 = 樂器的 use_duration）。</li>
 *   <li>插件播放模式的自訂號角：插件播放聲音並設定冷卻。</li>
 * </ul>
 */
public final class HornListener implements Listener {

    public static final String BYPASS = "customdisc.horn.bypasscooldown";

    private final CustomDisc plugin;

    public HornListener(CustomDisc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOAT_HORN || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        // 點到箱子、門之類的方塊時，原版會先操作方塊，不會吹號角
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable() && !player.isSneaking()
                && event.useInteractedBlock() != Event.Result.DENY) {
            return;
        }

        boolean bypass = player.hasPermission(BYPASS);
        if (!plugin.items().isCustom(item)) {
            if (bypass) {
                clearCooldownNextTick(player);
            }
            return;
        }

        CustomSound sound = plugin.items().soundOf(item);
        if (sound == null) {
            event.setUseItemInHand(Event.Result.DENY);
            Msg.send(player, "&c這支號角已失效，無法使用");
            return;
        }
        if (plugin.datapack().isNative(sound) && plugin.items().refresh(item) == null) {
            if (bypass) {
                clearCooldownNextTick(player);
            }
            return;
        }

        // 插件播放模式
        event.setUseItemInHand(Event.Result.DENY);
        if (!bypass && player.hasCooldown(Material.GOAT_HORN)) {
            return;
        }
        Location at = player.getLocation();
        String key = DatapackService.NAMESPACE + ":" + sound.soundEvent();
        float volume = sound.range() / 16.0f;
        double max = sound.range() + 8;
        for (Player p : player.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= max * max) {
                p.playSound(at, key, SoundCategory.RECORDS, volume, 1.0f);
            }
        }
        if (!bypass) {
            player.setCooldown(Material.GOAT_HORN, sound.cooldown() * 20);
        }
    }

    private void clearCooldownNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> player.setCooldown(Material.GOAT_HORN, 0));
    }
}
