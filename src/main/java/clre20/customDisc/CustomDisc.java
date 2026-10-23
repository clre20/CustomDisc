package clre20.customDisc;

import clre20.customDisc.bedrock.Bedrock;
import clre20.customDisc.command.CdCommand;
import clre20.customDisc.config.Messages;
import clre20.customDisc.config.PluginConfig;
import clre20.customDisc.data.CustomSound;
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
import clre20.customDisc.update.UpdateChecker;
import clre20.customDisc.update.UpdateNotifier;
import clre20.customDisc.util.McVersion;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class CustomDisc extends JavaPlugin {

    private static CustomDisc instance;

    private PluginConfig config;
    private Messages messages;
    private SoundStore store;
    private SoundLibrary library;
    private DatapackService datapack;
    private PackService packs;
    private ItemFactory items;
    private ItemRefreshListener refresher;
    private JukeboxService jukebox;
    private ChatInput chat;
    private Editor editor;
    private Bedrock bedrock;
    private UpdateChecker updates;
    private UpdateNotifier updateNotifier;
    private volatile boolean datapackChanged;

    public static CustomDisc get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        config = new PluginConfig(this);
        messages = new Messages(this);
        loadFiles();

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
        chat = new ChatInput(this);
        editor = new Editor(this);
        updates = new UpdateChecker(getPluginMeta().getVersion());
        updateNotifier = new UpdateNotifier(this);

        register(packs, refresher, jukebox, new HornListener(this), new MenuListener(this), chat, updateNotifier);

        PluginCommand command = getCommand("cdisc");
        if (command != null) {
            CdCommand executor = new CdCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        packs.startHttp();
        packs.rebuild(false);
        jukebox.start();

        getLogger().info(messages.console("enabled", "version", getPluginMeta().getVersion(), "mc", McVersion.current(),
                "discs", store.all(SoundType.DISC).size(), "horns", store.all(SoundType.HORN).size()));
        if (!DatapackService.instrumentsSupported()) {
            getLogger().info(messages.console("no-instruments"));
        }
        if (datapackChanged) {
            getLogger().info(messages.console("datapack-changed", "path", datapack.folder().getPath()));
        }
        updateNotifier.checkOnStartup();
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
        if (packs != null) {
            packs.stopHttp();
        }
        if (bedrock != null) {
            bedrock.disable();
        }
        if (updates != null) {
            updates.shutdown();
        }
        if (store != null) {
            store.flush();
        }
        instance = null;
    }

    private void register(Listener... listeners) {
        for (Listener l : listeners) {
            getServer().getPluginManager().registerEvents(l, this);
        }
    }

    /** 讀取 config.yml 與 messages.yml，缺少的項目自動補上。 */
    private void loadFiles() {
        config.load();
        messages.load();
        if (config.lastFilled() > 0) {
            getLogger().info(messages.console("config-filled", "count", config.lastFilled()));
        }
        if (config.migratedDefaults()) {
            getLogger().info(messages.console("config-migrated"));
        }
        if (messages.lastFilled() > 0) {
            getLogger().info(messages.console("messages-filled", "count", messages.lastFilled()));
        }
    }

    /** 唱片／號角新增、修改或刪除之後：更新資料包、資源包，並刷新線上玩家的物品。 */
    public void applyChanges() {
        datapack.writeAsync().thenAccept(changed -> {
            if (changed) {
                datapackChanged = true;
            }
        });
        refresher.refreshOnline();
        packs.rebuild(true);
    }

    /** /cd reload。完成時回傳資源包是否有變。 */
    public CompletableFuture<Boolean> reload() {
        editor.endAll();
        loadFiles();
        store.load();
        packs.startHttp();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        // 音檔在背景掃描，回到主執行緒再更新資料
        library.scanAsync().thenRun(() -> getServer().getScheduler().runTask(this, () -> {
            syncLengths();
            refresher.refreshOnline();
            datapack.writeAsync().thenAccept(changed -> {
                if (changed) {
                    datapackChanged = true;
                }
                getServer().getScheduler().runTask(this, () -> packs.rebuild(true).thenAccept(packChanged -> {
                    refresher.refreshOnline();
                    result.complete(packChanged);
                }));
            });
        }));
        return result;
    }

    /** 音檔在外面被換掉（同檔名、長度不同）時，自動更新記錄的長度與號角最低冷卻。 */
    private void syncLengths() {
        for (CustomSound s : store.all()) {
            SoundLibrary.Entry e = library.find(s.soundFile());
            if (e == null || !e.valid() || Math.abs(e.info().seconds() - s.length()) < 0.05) {
                continue;
            }
            CustomSound updated = s.copy();
            updated.length(e.info().seconds());
            if (updated.type() == SoundType.HORN && updated.cooldown() < updated.minCooldown()) {
                updated.cooldown(updated.minCooldown());
            }
            store.save(updated);
            getLogger().info(messages.console("length-updated", "type", s.type().displayName(), "name", s.name(),
                    "seconds", Math.round(updated.length() * 10) / 10.0));
        }
    }

    public boolean datapackChanged() {
        return datapackChanged;
    }

    public PluginConfig config() { return config; }
    public Messages messages() { return messages; }
    public SoundStore store() { return store; }
    public SoundLibrary library() { return library; }
    public DatapackService datapack() { return datapack; }
    public PackService packs() { return packs; }
    public ItemFactory items() { return items; }
    public ItemRefreshListener refresher() { return refresher; }
    public JukeboxService jukebox() { return jukebox; }
    public ChatInput chat() { return chat; }
    public Editor editor() { return editor; }
    public Bedrock bedrock() { return bedrock; }
    public UpdateChecker updates() { return updates; }
    public UpdateNotifier updateNotifier() { return updateNotifier; }
}
