package clre20.customDisc.playback;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Msg;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.util.BlockKey;
import clre20.customDisc.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 唱片機播放。
 * <ul>
 *   <li>原生模式的唱片：交給原版播放，插件只負責循環播放。</li>
 *   <li>插件播放模式的唱片：放進唱片機時停掉原版聲音，改由插件播放自訂音檔。</li>
 * </ul>
 */
public final class JukeboxService implements Listener {

    /** 唱片機放唱片時原版用的音量（影響可聽範圍）。 */
    private static final float JUKEBOX_VOLUME = 4.0f;

    private final class Session {
        final BlockKey key;
        final CustomSound sound;
        final boolean playlist;
        BukkitTask endTask;

        Session(BlockKey key, CustomSound sound, boolean playlist) {
            this.key = key;
            this.sound = sound;
            this.playlist = playlist;
        }
    }

    private final CustomDisc plugin;
    private final Map<BlockKey, Session> sessions = new HashMap<>();
    /** 原生模式、開了循環的唱片機。 */
    private final Set<BlockKey> nativeLoops = new HashSet<>();
    private BukkitTask watchTask;

    public JukeboxService(CustomDisc plugin) {
        this.plugin = plugin;
    }

    public void start() {
        watchTask = Bukkit.getScheduler().runTaskTimer(plugin, this::watch, 10L, 10L);
    }

    public void shutdown() {
        if (watchTask != null) {
            watchTask.cancel();
        }
        for (Session s : sessions.values().toArray(new Session[0])) {
            stop(s.key);
        }
        sessions.clear();
        nativeLoops.clear();
    }

    public boolean hasSession(Block block) {
        return sessions.containsKey(BlockKey.of(block));
    }

    // ------------------------------------------------------------ 放唱片

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.JUKEBOX
                || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (hand == null || !plugin.items().isCustom(hand) || hand.getType() == Material.GOAT_HORN) {
            return;
        }
        if (player.isSneaking()) {
            return; // 原版：蹲下時拿著物品不會放進唱片機
        }
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Jukebox jukebox) || jukebox.hasRecord()) {
            return;
        }

        CustomSound sound = plugin.items().soundOf(hand);
        if (sound == null) {
            event.setCancelled(true);
            Msg.send(player, "&c這張唱片已失效，無法播放");
            return;
        }
        ItemStack fresh = plugin.items().refresh(hand);
        ItemStack record = (fresh != null ? fresh : hand).clone();
        record.setAmount(1);

        if (plugin.datapack().isNative(sound) && fresh == null) {
            // 原生模式：交給原版放進去
            Bukkit.getScheduler().runTask(plugin, () -> afterInserted(block));
            return;
        }

        // 插件播放模式（或物品需要更新）：自己放進去，同一刻停掉原版聲音
        event.setCancelled(true);
        insertAndPlay(block, record, false);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack left = hand.clone();
            left.setAmount(hand.getAmount() - 1);
            setHand(player, event.getHand(), left.getAmount() <= 0 ? null : left);
        }
    }

    private static void setHand(Player player, EquipmentSlot slot, ItemStack item) {
        if (slot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    /** 漏斗把唱片送進唱片機。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        if (dest.getType() != InventoryType.JUKEBOX) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.items().isCustom(item)) {
            return;
        }
        InventoryHolder holder = dest.getHolder();
        if (!(holder instanceof Jukebox jb)) {
            return;
        }
        Block block = jb.getBlock();
        CustomSound sound = plugin.items().soundOf(item);
        if (sound == null) {
            event.setCancelled(true); // 已失效的唱片不能放進去
            return;
        }
        if (plugin.datapack().isNative(sound) && plugin.items().refresh(item) == null) {
            Bukkit.getScheduler().runTask(plugin, () -> afterInserted(block));
            return;
        }
        // 插件播放模式（或需要更新的物品）：自己搬，避免原版先播出外觀唱片的音樂
        event.setCancelled(true);
        Inventory source = event.getSource();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(block.getState() instanceof Jukebox now) || now.hasRecord()) {
                return;
            }
            ItemStack one = item.clone();
            one.setAmount(1);
            if (!source.removeItem(one).isEmpty()) {
                return;
            }
            ItemStack fresh = plugin.items().refresh(one);
            insertAndPlay(block, fresh != null ? fresh : one, false);
        });
    }

    /** 原生模式唱片放進去後：記錄要不要循環。 */
    private void afterInserted(Block block) {
        if (!(block.getState() instanceof Jukebox jb) || !jb.hasRecord()) {
            return;
        }
        CustomSound sound = plugin.items().soundOf(jb.getRecord());
        if (sound != null && sound.type() == SoundType.DISC && sound.loop()) {
            nativeLoops.add(BlockKey.of(block));
        }
    }

    /**
     * 把唱片放進唱片機並開始播放（輪播也用這個）。
     *
     * @param playlist 是否是輪播中，輪播時不看單張唱片的循環設定
     */
    public void insertAndPlay(Block block, ItemStack record, boolean playlist) {
        BlockKey key = BlockKey.of(block);
        stop(key);
        nativeLoops.remove(key);
        if (!(block.getState() instanceof Jukebox jb)) {
            return;
        }
        jb.setRecord(record);
        jb.update(true, false);

        CustomSound sound = plugin.items().soundOf(record);
        boolean pluginMode = sound != null && !plugin.datapack().isNative(sound);
        if (block.getState() instanceof Jukebox live) {
            if (pluginMode) {
                if (live.isPlaying()) {
                    live.stopPlaying();
                }
            } else if (!live.isPlaying()) {
                live.startPlaying();
            }
        }
        if (pluginMode) {
            play(block, sound, playlist);
        } else if (!playlist && sound != null && sound.loop()) {
            nativeLoops.add(key);
        }
    }

    // ------------------------------------------------------------ 插件播放

    private void play(Block block, CustomSound sound, boolean playlist) {
        BlockKey key = BlockKey.of(block);
        Session session = new Session(key, sound.copy(), playlist);
        sessions.put(key, session);

        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        String event = DatapackService.NAMESPACE + ":" + sound.soundEvent();
        Component nowPlaying = Component.translatable("record.nowPlaying", Text.color(sound.displayName()));
        double max = sound.range() + 8;
        for (Player p : block.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= max * max) {
                p.playSound(at, event, SoundCategory.RECORDS, JUKEBOX_VOLUME, 1.0f);
                if (p.getLocation().distanceSquared(at) <= 64 * 64) {
                    p.sendActionBar(nowPlaying);
                }
            }
        }
        long ticks = Math.max(20L, (long) Math.ceil(sound.length() * 20.0));
        session.endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onEnd(session), ticks);
    }

    private void onEnd(Session session) {
        if (sessions.get(session.key) != session) {
            return;
        }
        sessions.remove(session.key);
        Block block = session.key.block();
        if (block == null || block.getType() != Material.JUKEBOX) {
            return;
        }
        if (plugin.playlist().isPlaylist(block)) {
            plugin.playlist().onSongFinished(block);
            return;
        }
        if (session.sound.loop() && block.getState() instanceof Jukebox jb && jb.hasRecord()
                && session.sound.id().equals(plugin.items().idOf(jb.getRecord()))) {
            CustomSound latest = plugin.items().soundOf(jb.getRecord());
            if (latest != null && !plugin.datapack().isNative(latest)) {
                play(block, latest, false);
            }
        }
    }

    /** 停止插件播放的聲音。 */
    public void stop(BlockKey key) {
        Session s = sessions.remove(key);
        if (s == null) {
            return;
        }
        if (s.endTask != null) {
            s.endTask.cancel();
        }
        String event = DatapackService.NAMESPACE + ":" + s.sound.soundEvent();
        org.bukkit.World world = Bukkit.getWorld(key.world());
        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.stopSound(event, SoundCategory.RECORDS);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.JUKEBOX) {
            BlockKey key = BlockKey.of(event.getBlock());
            stop(key);
            nativeLoops.remove(key);
        }
    }

    /** 每 0.5 秒檢查：唱片被拿走、唱片機被破壞就停止；原生模式循環播放。 */
    private void watch() {
        Iterator<Map.Entry<BlockKey, Session>> it = sessions.entrySet().iterator();
        java.util.List<BlockKey> toStop = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            Block block = s.key.block();
            if (block == null || block.getType() != Material.JUKEBOX) {
                toStop.add(s.key);
                continue;
            }
            BlockState state = block.getState();
            if (!(state instanceof Jukebox jb) || !jb.hasRecord()
                    || !s.sound.id().equals(plugin.items().idOf(jb.getRecord()))) {
                toStop.add(s.key);
            }
        }
        toStop.forEach(this::stop);

        Iterator<BlockKey> loops = nativeLoops.iterator();
        while (loops.hasNext()) {
            BlockKey key = loops.next();
            Block block = key.block();
            if (block == null || !(block.getState() instanceof Jukebox jb) || !jb.hasRecord()) {
                loops.remove();
                continue;
            }
            CustomSound sound = plugin.items().soundOf(jb.getRecord());
            if (sound == null || !sound.loop() || !plugin.datapack().isNative(sound)) {
                loops.remove();
                continue;
            }
            if (plugin.playlist().isPlaylist(block)) {
                continue; // 輪播時由輪播處理
            }
            if (!jb.isPlaying()) {
                jb.startPlaying();
            }
        }
    }
}
