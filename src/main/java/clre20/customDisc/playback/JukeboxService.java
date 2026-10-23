package clre20.customDisc.playback;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.util.BlockKey;
import clre20.customDisc.util.Text;
import clre20.customDisc.util.Vanilla;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
 *   <li>插件播放模式的唱片：放進唱片機時不讓原版播放，改由插件播放自訂音檔。</li>
 * </ul>
 */
public final class JukeboxService implements Listener {

    /** 唱片機放唱片時原版用的音量（影響可聽範圍）。 */
    private static final float JUKEBOX_VOLUME = 4.0f;

    private static final class Session {
        final BlockKey key;
        final Track track;
        final Location at;
        BukkitTask endTask;

        Session(BlockKey key, Track track, Location at) {
            this.key = key;
            this.track = track;
            this.at = at;
        }
    }

    private final CustomDisc plugin;
    private final Map<BlockKey, Session> sessions = new HashMap<>();
    /** 原生模式、開了循環的唱片機。 */
    private final Set<BlockKey> nativeLoops = new HashSet<>();
    private BukkitTask watchTask;
    /** watch 每 0.5 秒一次，音符每隔一次（1 秒）產生。 */
    private boolean particleTick;

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
        if (hand == null || hand.getType() == Material.GOAT_HORN || !plugin.items().isCustom(hand)) {
            return;
        }
        if (player.isSneaking()) {
            return; // 原版：蹲下時拿著物品不會放進唱片機
        }
        Block block = event.getClickedBlock();
        if (!(block.getState(false) instanceof Jukebox jukebox) || jukebox.hasRecord()) {
            return;
        }
        CustomSound sound = plugin.items().soundOf(hand);
        if (sound == null) {
            event.setCancelled(true);
            plugin.messages().send(player, "playback.disc-invalid");
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

        // 插件播放模式（或物品需要更新）：自己放進去，不讓原版播放
        event.setCancelled(true);
        insertAndPlay(block, record);
        takeOne(player, event.getHand(), hand);
    }

    private static void takeOne(Player player, EquipmentSlot slot, ItemStack hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack left = hand.clone();
        left.setAmount(hand.getAmount() - 1);
        setHand(player, slot, left.getAmount() <= 0 ? null : left);
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
            if (!(block.getState(false) instanceof Jukebox now) || now.hasRecord()) {
                return;
            }
            ItemStack one = item.clone();
            one.setAmount(1);
            if (!source.removeItem(one).isEmpty()) {
                return;
            }
            ItemStack fresh = plugin.items().refresh(one);
            insertAndPlay(block, fresh != null ? fresh : one);
        });
    }

    /** 原生模式唱片放進去後：記錄要不要循環。 */
    private void afterInserted(Block block) {
        if (!(block.getState(false) instanceof Jukebox jb) || !jb.hasRecord()) {
            return;
        }
        CustomSound sound = plugin.items().soundOf(jb.getRecord());
        if (sound != null && sound.type() == SoundType.DISC && sound.loop()) {
            nativeLoops.add(BlockKey.of(block));
        }
    }

    /** 一首要由插件播放的歌。 */
    private record Track(String recordId, String soundKey, long ticks, Component title, double range, boolean loop) {
    }

    /** 這張自訂唱片由插件播放時的資料；不是自訂唱片時回傳 null。 */
    private Track trackOf(ItemStack record, CustomSound sound) {
        if (sound != null && sound.type() == SoundType.DISC) {
            return new Track(sound.id(), DatapackService.NAMESPACE + ":" + sound.soundEvent(),
                    Math.max(20L, (long) Math.ceil(sound.length() * 20.0)),
                    Component.translatable("record.nowPlaying", Text.color(sound.displayName())),
                    sound.range(), sound.loop());
        }
        return null;
    }

    /** 唱片機裡這張唱片的識別：自訂唱片用 ID，原版唱片用物品種類。 */
    private String recordId(ItemStack record) {
        if (record == null || record.getType() == Material.AIR) {
            return null;
        }
        String id = plugin.items().idOf(record);
        return id != null ? id : record.getType().name();
    }

    /**
     * 把唱片放進唱片機並開始播放。
     * 插件播放模式的唱片不讓原版播放，由插件播放自訂音檔；其他交給原版。
     */
    public void insertAndPlay(Block block, ItemStack record) {
        BlockKey key = BlockKey.of(block);
        stop(key);
        nativeLoops.remove(key);
        if (!(block.getState(false) instanceof Jukebox)) {
            return;
        }
        CustomSound sound = plugin.items().soundOf(record);
        boolean nativeSong = sound != null && plugin.datapack().isNative(sound);
        boolean pluginMode = sound != null && !nativeSong;
        Track track = trackOf(record, sound);

        if (track != null && pluginMode) {
            stopVanilla(block);
            if (!SilentJukebox.insert(block, record)) {
                // 這個伺服器不支援靜音放入：一般方式放入後停掉原版，並再補送幾次停止
                vanillaInsert(block, record);
                stopVanilla(block);
                stopVanillaLater(block, record);
            }
            play(block, track);
            return;
        }
        // 交給原版播放
        vanillaInsert(block, record);
        if (block.getState(false) instanceof Jukebox live && !live.isPlaying()) {
            live.startPlaying();
        }
        if (nativeSong && sound.loop()) {
            nativeLoops.add(key);
        }
    }

    private static void vanillaInsert(Block block, ItemStack record) {
        if (block.getState() instanceof Jukebox jb) {
            jb.setRecord(record);
            jb.update(true, false);
        }
    }

    private static void stopVanilla(Block block) {
        if (block.getState(false) instanceof Jukebox live && live.isPlaying()) {
            live.stopPlaying();
        }
    }

    /** 備用：客戶端可能比停止指令晚開始播，過幾個 tick 再對附近玩家停掉外觀唱片的原版音樂。 */
    private void stopVanillaLater(Block block, ItemStack record) {
        String key = Vanilla.songKey(record.getType());
        if (key == null) {
            return;
        }
        String event = "minecraft:music_disc." + key;
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (long delay : new long[]{2L, 6L, 12L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : at.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(at) <= 80 * 80) {
                        p.stopSound(event, SoundCategory.RECORDS);
                    }
                }
            }, delay);
        }
    }

    // ------------------------------------------------------------ 插件播放

    private void play(Block block, Track track) {
        BlockKey key = BlockKey.of(block);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Session session = new Session(key, track, at);
        sessions.put(key, session);

        double max = track.range() + 8;
        for (Player p : block.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= max * max) {
                p.playSound(at, track.soundKey(), SoundCategory.RECORDS, JUKEBOX_VOLUME, 1.0f);
                if (p.getLocation().distanceSquared(at) <= 64 * 64) {
                    p.sendActionBar(track.title());
                }
            }
        }
        session.endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onEnd(session), track.ticks());
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
        if (session.track.loop() && block.getState(false) instanceof Jukebox jb && jb.hasRecord()
                && session.track.recordId().equals(recordId(jb.getRecord()))) {
            CustomSound latest = plugin.items().soundOf(jb.getRecord());
            if (latest != null && !plugin.datapack().isNative(latest)) {
                Track next = trackOf(jb.getRecord(), latest);
                if (next != null) {
                    play(block, next);
                }
            }
        }
    }

    /** 停止插件播放的聲音（只對聽得到的玩家，不影響其他唱片機）。 */
    public void stop(BlockKey key) {
        Session s = sessions.remove(key);
        if (s == null) {
            return;
        }
        if (s.endTask != null) {
            s.endTask.cancel();
        }
        org.bukkit.World world = s.at.getWorld();
        if (world == null) {
            return;
        }
        double max = s.track.range() + 16;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(s.at) <= max * max) {
                p.stopSound(s.track.soundKey(), SoundCategory.RECORDS);
            }
        }
    }

    /** 播放中的唱片機不能被漏斗把唱片抽走（和原版播放中一樣）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperTake(InventoryMoveItemEvent event) {
        if (sessions.isEmpty() || event.getSource().getType() != InventoryType.JUKEBOX) {
            return;
        }
        if (event.getSource().getHolder() instanceof Jukebox jb && sessions.containsKey(BlockKey.of(jb.getBlock()))) {
            event.setCancelled(true);
        }
    }

    /** 音符粒子：和原版一樣每秒一次，在唱片機上方 1.2 格。 */
    private void particles(Block block) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 1.2, block.getZ() + 0.5);
        double color = java.util.concurrent.ThreadLocalRandom.current().nextInt(4) / 24.0;
        block.getWorld().spawnParticle(Particle.NOTE, at, 0, color, 0, 0, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.JUKEBOX) {
            BlockKey key = BlockKey.of(event.getBlock());
            stop(key);
            nativeLoops.remove(key);
        }
    }

    /** 每 0.5 秒檢查：唱片被拿走、唱片機被破壞就停止；原生模式循環播放。沒有任何播放時直接跳過。 */
    private void watch() {
        if (sessions.isEmpty() && nativeLoops.isEmpty()) {
            return;
        }
        particleTick = !particleTick;
        Iterator<Map.Entry<BlockKey, Session>> it = sessions.entrySet().iterator();
        java.util.List<BlockKey> toStop = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            Block block = s.key.block();
            if (block == null || block.getType() != Material.JUKEBOX) {
                toStop.add(s.key);
                continue;
            }
            BlockState state = block.getState(false);
            if (!(state instanceof Jukebox jb) || !jb.hasRecord()
                    || !s.track.recordId().equals(recordId(jb.getRecord()))) {
                toStop.add(s.key);
                continue;
            }
            if (particleTick) {
                particles(block);
            }
        }
        toStop.forEach(this::stop);

        Iterator<BlockKey> loops = nativeLoops.iterator();
        while (loops.hasNext()) {
            BlockKey key = loops.next();
            Block block = key.block();
            if (block == null || !(block.getState(false) instanceof Jukebox jb) || !jb.hasRecord()) {
                loops.remove();
                continue;
            }
            CustomSound sound = plugin.items().soundOf(jb.getRecord());
            if (sound == null || !sound.loop() || !plugin.datapack().isNative(sound)) {
                loops.remove();
                continue;
            }
            if (!jb.isPlaying()) {
                jb.startPlaying();
            }
        }
    }
}
