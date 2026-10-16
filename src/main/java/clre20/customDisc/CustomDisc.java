package clre20.customDisc;

import clre20.customDisc.bedrock.Bedrock;
import clre20.customDisc.command.CdCommand;
import clre20.customDisc.config.PluginConfig;
import clre20.customDisc.data.SoundStore;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.gui.ChatInput;
import clre20.customDisc.gui.Editor;
import clre20.customDisc.gui.MenuListener;
import clre20.customDisc.item.ItemFactory;
import clre20.customDisc.item.ItemRefreshListener;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.pack.PackService;
import clre20.customDisc.pack.SoundLibrary;
import clre20.customDisc.playback.HornListener;
import clre20.customDisc.playback.JukeboxService;
import clre20.customDisc.playlist.PlaylistService;
import clre20.customDisc.update.UpdateChecker;
import clre20.customDisc.util.McVersion;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class CustomDisc extends JavaPlugin {

    private static CustomDisc instance;

    private PluginConfig config;
    private SoundStore store;
    private SoundLibrary library;
    private DatapackService datapack;
    private PackService packs;
    private ItemFactory items;
    private ItemRefreshListener refresher;
    private JukeboxService jukebox;
    private PlaylistService playlist;
    private ChatInput chat;
    private Editor editor;
    private Bedrock bedrock;
    private UpdateChecker updates;
    private boolean datapackChanged;

    public static CustomDisc get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        config = new PluginConfig(this);
        config.load();
        store = new SoundStore(this);
        store.load();
        library = new SoundLibrary(this);
        library.scan();
        syncLengths();

        datapack = new DatapackService(this);
        datapack.snapshotBoot();
        datapackChanged = datapack.write();

        bedrock = new Bedrock();
        bedrock.enable(this, config.bedrockEnabled());

        items = new ItemFactory(this);
        refresher = new ItemRefreshListener(this);
        packs = new PackService(this);
        jukebox = new JukeboxService(this);
        playlist = new PlaylistService(this);
        chat = new ChatInput(this);
        editor = new Editor(this);
        updates = new UpdateChecker(getPluginMeta().getVersion());

        register(packs, refresher, jukebox, new HornListener(this), playlist, new MenuListener(this), chat);

        PluginCommand command = getCommand("cdisc");
        if (command != null) {
            CdCommand executor = new CdCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        packs.startHttp();
        packs.rebuild(false);
        jukebox.start();
        playlist.start();

        getLogger().info("CustomDisc " + getPluginMeta().getVersion() + " 已啟用（Minecraft " + McVersion.current()
                + "）：唱片 " + store.all(SoundType.DISC).size() + " 張、號角 " + store.all(SoundType.HORN).size() + " 支");
        getLogger().info(playlist.enabled()
                ? "唱片機輪播：已啟用"
                : "唱片機輪播：此版本不使用插件的輪播（1.21.9～26.3 才啟用，26.4 以上由原版處理）");
        if (!DatapackService.instrumentsSupported()) {
            getLogger().info("此版本不支援自訂樂器，號角一律由插件播放");
        }
        if (datapackChanged) {
            getLogger().info("資料包已更新（" + datapack.folder().getPath() + "），重啟伺服器後新的唱片／號角會改用原生模式");
        }

        if (config.updateCheckOnStartup()) {
            updates.check().thenAccept(r -> {
                if (r.latest() == null) {
                    getLogger().info("檢查更新：" + r.error());
                } else if (r.newer()) {
                    getLogger().warning("有新版本 " + r.latest() + "（目前 " + r.current() + "）：" + UpdateChecker.PAGE);
                } else {
                    getLogger().info("目前已是最新版本");
                }
            });
        }
    }

    @Override
    public void onDisable() {
        if (editor != null) {
            editor.endAll();
        }
        if (chat != null) {
            chat.clear();
        }
        if (jukebox != null) {
            jukebox.shutdown();
        }
        if (playlist != null) {
            playlist.shutdown();
        }
        if (packs != null) {
            packs.stopHttp();
        }
        if (bedrock != null) {
            bedrock.disable();
        }
        instance = null;
    }

    private void register(Listener... listeners) {
        for (Listener l : listeners) {
            getServer().getPluginManager().registerEvents(l, this);
        }
    }

    /** 唱片／號角新增、修改或刪除之後：更新資料包、資源包，並刷新線上玩家的物品。 */
    public void applyChanges() {
        datapackChanged |= datapack.write();
        refresher.refreshOnline();
        packs.rebuild(true);
    }

    /** /cd reload。完成時回傳資源包是否有變。 */
    public CompletableFuture<Boolean> reload() {
        editor.endAll();
        config.load();
        store.load();
        library.scan();
        syncLengths();
        datapackChanged |= datapack.write();
        packs.startHttp();
        refresher.refreshOnline();
        return packs.rebuild(true).thenApply(changed -> {
            refresher.refreshOnline();
            return changed;
        });
    }

    /** 音檔在外面被換掉（同檔名、長度不同）時，自動更新記錄的長度與號角最低冷卻。 */
    private void syncLengths() {
        for (clre20.customDisc.data.CustomSound s : store.all()) {
            SoundLibrary.Entry e = library.find(s.soundFile());
            if (e == null || !e.valid() || Math.abs(e.info().seconds() - s.length()) < 0.05) {
                continue;
            }
            clre20.customDisc.data.CustomSound updated = s.copy();
            updated.length(e.info().seconds());
            if (updated.type() == SoundType.HORN && updated.cooldown() < updated.minCooldown()) {
                updated.cooldown(updated.minCooldown());
            }
            try {
                store.save(updated);
                getLogger().info(s.type().displayName() + " " + s.name() + " 的音檔長度已更新為 "
                        + Math.round(updated.length() * 10) / 10.0 + " 秒");
            } catch (java.io.IOException ex) {
                getLogger().warning("更新 " + s.name() + " 的長度失敗：" + ex.getMessage());
            }
        }
    }

    public boolean datapackChanged() {
        return datapackChanged;
    }

    public PluginConfig config() { return config; }
    public SoundStore store() { return store; }
    public SoundLibrary library() { return library; }
    public DatapackService datapack() { return datapack; }
    public PackService packs() { return packs; }
    public ItemFactory items() { return items; }
    public ItemRefreshListener refresher() { return refresher; }
    public JukeboxService jukebox() { return jukebox; }
    public PlaylistService playlist() { return playlist; }
    public ChatInput chat() { return chat; }
    public Editor editor() { return editor; }
    public Bedrock bedrock() { return bedrock; }
    public UpdateChecker updates() { return updates; }
}
