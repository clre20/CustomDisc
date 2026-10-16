package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Msg;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.pack.SoundLibrary;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建立／編輯流程。
 * 建立：1 外觀 → 2 名稱與描述 → 3 音檔 → 4 循環／冷卻 → 5 音量與範圍 → 6 總覽確認。
 * 編輯：直接打開總覽頁，點哪一項就改哪一項，改完回到總覽。
 */
public final class Editor {

    public static final int STEPS = 6;

    private final CustomDisc plugin;
    private final Map<UUID, EditSession> sessions = new HashMap<>();

    public Editor(CustomDisc plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ 開始／結束

    public void startCreate(Player player, SoundType type, String name) {
        end(player, false);
        CustomSound d = new CustomSound(type, name);
        if (type == SoundType.DISC) {
            d.appearance("MUSIC_DISC_CAT");
            d.volume(plugin.config().defaultDiscVolume());
            d.range(plugin.config().defaultDiscRange());
            d.loop(false);
            d.comparator(null);
        } else {
            d.appearance("PONDER");
            d.volume(plugin.config().defaultHornVolume());
            d.range(plugin.config().defaultHornRange());
            d.cooldown(plugin.config().defaultHornCooldown());
        }
        d.displayName(name);
        d.soundFile("");
        d.revision(0);
        EditSession s = new EditSession(player.getUniqueId(), d, true);
        sessions.put(player.getUniqueId(), s);
        appearance(player);
    }

    public void startEdit(Player player, CustomSound sound) {
        end(player, false);
        EditSession s = new EditSession(player.getUniqueId(), sound.copy(), false);
        sessions.put(player.getUniqueId(), s);
        overview(player);
    }

    public EditSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** 結束編輯（不儲存）。 */
    public void end(Player player, boolean message) {
        EditSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        stopPreview(player, s);
        plugin.chat().cancelSilently(player.getUniqueId());
        if (message) {
            Msg.send(player, "&e已取消" + (s.creating() ? "建立" : "編輯") + "，沒有儲存任何變更");
        }
    }

    public void endAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.closeInventory();
                end(p, true);
            }
        }
        sessions.clear();
    }

    /** 介面被關掉一個 tick 後檢查：不是換頁也不是在等輸入，就當作取消。 */
    void checkClosed(Player player, EditSession s) {
        if (sessions.get(player.getUniqueId()) != s || plugin.chat().waiting(player)) {
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.session == s) {
            return;
        }
        end(player, true);
    }

    private EditSession require(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ------------------------------------------------------------ 各頁面

    public void appearance(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new AppearanceMenu(s, 0).open(player);
        }
    }

    public void overview(Player player) {
        EditSession s = require(player);
        if (s != null) {
            s.reachedOverview();
            new OverviewMenu(s).open(player);
        }
    }

    public void soundFiles(Player player) {
        EditSession s = require(player);
        if (s != null) {
            plugin.library().scan();
            new SoundFileMenu(s, 0).open(player);
        }
    }

    public void loopCooldown(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new LoopCooldownMenu(s).open(player);
        }
    }

    public void volumeRange(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new VolumeRangeMenu(s).open(player);
        }
    }

    public void comparator(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new ComparatorMenu(s).open(player);
        }
    }

    /** 建立流程：選完外觀之後。 */
    void afterAppearance(Player player, EditSession s) {
        if (s.wizard()) {
            promptName(player);
        } else {
            overview(player);
        }
    }

    public void promptName(Player player) {
        EditSession s = require(player);
        if (s == null) {
            return;
        }
        String cancel = plugin.config().cancelWord();
        plugin.chat().ask(player, text -> {
            if (text.isBlank()) {
                Msg.send(player, "&c名稱不能是空的，請重新輸入");
                promptName(player);
                return;
            }
            s.draft().displayName(text);
            Msg.send(player, "&a名稱已設定為：&r" + text);
            if (s.wizard()) {
                promptLore(player);
            } else {
                overview(player);
            }
        }, () -> {
            if (s.wizard()) {
                appearance(player);
            } else {
                overview(player);
            }
        });
        player.closeInventory();
        step(player, s, 2);
        Msg.send(player, "&f請在聊天室輸入&e顯示名稱&f（支援 & 顏色碼）");
        Msg.send(player, "&7目前：&r" + s.draft().displayName() + " &7｜輸入「" + cancel + "」返回");
    }

    public void promptLore(Player player) {
        EditSession s = require(player);
        if (s == null) {
            return;
        }
        String cancel = plugin.config().cancelWord();
        plugin.chat().ask(player, text -> {
            List<String> lines = new ArrayList<>();
            if (!text.equals("無")) {
                for (String line : text.split("\\|")) {
                    lines.add(line.trim());
                }
            }
            s.draft().lore(lines);
            Msg.send(player, lines.isEmpty() ? "&a已清空描述" : "&a描述已設定（" + lines.size() + " 行）");
            if (s.wizard()) {
                soundFiles(player);
            } else {
                overview(player);
            }
        }, () -> {
            if (s.wizard()) {
                promptName(player);
            } else {
                overview(player);
            }
        });
        player.closeInventory();
        step(player, s, 2);
        Msg.send(player, "&f請在聊天室輸入&e描述&f，用 &e| &f分隔多行（支援 & 顏色碼）");
        Msg.send(player, "&7輸入「無」清空描述 ｜輸入「" + cancel + "」返回");
    }

    private void step(Player player, EditSession s, int step) {
        if (s.wizard()) {
            Msg.send(player, "&8— 建立" + s.draft().type().displayName() + " " + s.draft().name()
                    + "：第 " + step + "/" + STEPS + " 步 —");
        }
    }

    /** 選完音檔。 */
    void afterSoundFile(Player player, EditSession s, SoundLibrary.Entry entry) {
        CustomSound d = s.draft();
        d.soundFile(entry.path());
        d.length(entry.info().seconds());
        Msg.send(player, "&a已選擇音檔：&f" + entry.path() + " &7（" + formatTime(entry.info().seconds()) + "）");
        if (entry.info().stereo()) {
            Msg.send(player, "&e注意：這是立體聲音檔，在 Minecraft 裡不會隨距離變小聲，建議轉成單聲道");
        }
        if (d.type() == SoundType.HORN && d.cooldown() < d.minCooldown()) {
            d.cooldown(d.minCooldown());
            Msg.send(player, "&e冷卻時間不能低於音檔長度，已自動調整為 " + d.cooldown() + " 秒");
        }
        if (s.wizard()) {
            loopCooldown(player);
        } else {
            overview(player);
        }
    }

    // ------------------------------------------------------------ 試聽

    void togglePreview(Player player, EditSession s) {
        if (s.previewEvent() != null) {
            stopPreview(player, s);
            return;
        }
        SoundLibrary.Entry e = plugin.library().find(s.draft().soundFile());
        if (e == null || !e.valid()) {
            Msg.send(player, "&c還沒選擇音檔，或音檔已不存在");
            return;
        }
        if (!plugin.packs().contains(e.id())) {
            Msg.send(player, "&e這是新加入的音檔，要先 &f/cd reload &e重新產生資源包後才能試聽");
            return;
        }
        String event = DatapackService.NAMESPACE + ":file." + e.id();
        player.playSound(player.getLocation(), event, SoundCategory.RECORDS, s.draft().volume() / 100f, 1.0f);
        s.previewEvent(event);
        Msg.send(player, "&a試聽中（只有你聽得到），再點一次停止");
    }

    void stopPreview(Player player, EditSession s) {
        if (s.previewEvent() != null) {
            player.stopSound(s.previewEvent(), SoundCategory.RECORDS);
            s.previewEvent(null);
        }
    }

    // ------------------------------------------------------------ 儲存

    void save(Player player, EditSession s) {
        CustomSound d = s.draft();
        SoundLibrary.Entry e = plugin.library().find(d.soundFile());
        if (e == null || !e.valid()) {
            Msg.send(player, "&c請先選擇音檔");
            return;
        }
        d.length(e.info().seconds());
        if (d.type() == SoundType.HORN && d.cooldown() < d.minCooldown()) {
            d.cooldown(d.minCooldown());
        }
        if (s.creating() && plugin.store().exists(d.type(), d.name())) {
            Msg.send(player, "&c名稱 " + d.name() + " 已經被使用了");
            return;
        }
        try {
            plugin.store().save(d);
        } catch (IOException ex) {
            Msg.send(player, "&c儲存失敗：" + ex.getMessage());
            return;
        }
        sessions.remove(player.getUniqueId());
        stopPreview(player, s);
        player.closeInventory();

        CustomSound saved = plugin.store().get(d.type(), d.name());
        plugin.applyChanges();
        String what = d.type().displayName() + " &f" + d.name();
        Msg.send(player, "&a已" + (s.creating() ? "建立" : "儲存") + what);
        if (plugin.datapack().nativeAfterRestart(saved)) {
            Msg.send(player, "&7目前由插件播放，重啟伺服器後會改用原生模式");
        }
        if (s.creating()) {
            Msg.send(player, "&7用 &f/cd give " + d.type().id() + " " + d.name() + " &7拿取");
        }
    }

    static String formatTime(double seconds) {
        int total = (int) Math.round(seconds);
        return total / 60 + ":" + String.format("%02d", total % 60);
    }
}
