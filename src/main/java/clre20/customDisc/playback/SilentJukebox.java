package clre20.customDisc.playback;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 把唱片放進唱片機但不讓原版播放。
 *
 * <p>Bukkit 的 Jukebox#setRecord 會呼叫原版的 setTheItem，放入的同一刻就開始播原版音樂；
 * 之後才停掉的話，客戶端可能先收到停止、後載入完音樂，結果照樣播出原版音樂。
 * 這裡改用原版讀取存檔時用的 setSongItemWithoutPlaying，從一開始就不播放。</p>
 *
 * <p>Paper 1.20.5 起執行時使用 Mojang 名稱，名稱在 1.21.x～26.x 都相同。
 * 取不到時（例如 Spigot）回傳 false，由呼叫端改用一般方式。</p>
 */
final class SilentJukebox {

    private static volatile boolean initialized;
    private static Method worldHandle;
    private static Constructor<?> blockPos;
    private static Method getBlockEntity;
    private static Class<?> jukeboxEntity;
    private static Method setWithoutPlaying;
    private static Method notifyChanged;
    private static Method asNmsCopy;

    private SilentJukebox() {
    }

    private static synchronized boolean init(World world) {
        if (initialized) {
            return setWithoutPlaying != null;
        }
        initialized = true;
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            worldHandle = world.getClass().getMethod("getHandle");
            Class<?> pos = Class.forName("net.minecraft.core.BlockPos", false, cl);
            blockPos = pos.getConstructor(int.class, int.class, int.class);
            getBlockEntity = worldHandle.getReturnType().getMethod("getBlockEntity", pos);
            jukeboxEntity = Class.forName("net.minecraft.world.level.block.entity.JukeboxBlockEntity", false, cl);
            Class<?> nmsItem = Class.forName("net.minecraft.world.item.ItemStack", false, cl);
            Method set = jukeboxEntity.getMethod("setSongItemWithoutPlaying", nmsItem, long.class);
            Method notify = jukeboxEntity.getDeclaredMethod("notifyItemChangedInJukebox", boolean.class);
            notify.setAccessible(true);
            asNmsCopy = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack", false, cl)
                    .getMethod("asNMSCopy", ItemStack.class);
            notifyChanged = notify;
            setWithoutPlaying = set;
            return true;
        } catch (Throwable t) {
            setWithoutPlaying = null;
            return false;
        }
    }

    /**
     * 放入唱片（取代原本的唱片），不播放原版音樂。
     *
     * @return 成功時 true；這個伺服器不支援時 false
     */
    static boolean insert(Block block, ItemStack record) {
        if (!init(block.getWorld())) {
            return false;
        }
        try {
            Object level = worldHandle.invoke(block.getWorld());
            Object pos = blockPos.newInstance(block.getX(), block.getY(), block.getZ());
            Object entity = getBlockEntity.invoke(level, pos);
            if (!jukeboxEntity.isInstance(entity)) {
                return false;
            }
            // 經過的時間設成最大值：原版判斷為「已經播完」，所以不會開始播放
            setWithoutPlaying.invoke(entity, asNmsCopy.invoke(null, record), Long.MAX_VALUE);
            // 更新方塊的「有唱片」狀態（讓玩家能退出、漏斗與比較器正確判斷）
            notifyChanged.invoke(entity, true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
