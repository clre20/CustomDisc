package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import org.bukkit.Material;

/** 步驟 4：唱片設定循環播放；號角設定冷卻秒數。 */
final class LoopCooldownMenu extends EditorMenu {

    LoopCooldownMenu(EditSession session) {
        super(session, 3, session.draft().type() == SoundType.DISC ? "循環播放" : "冷卻時間", 4);
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        if (d.type() == SoundType.DISC) {
            set(13, d.loop()
                            ? glow(icon(Material.LIME_DYE, "&a循環播放：開",
                            "&7放進唱片機後會一直重複播放", "&7（唱片機輪播時不看這個設定）", "", "&e點擊切換"))
                            : icon(Material.GRAY_DYE, "&7循環播放：關",
                            "&7播完一次就停止", "", "&e點擊切換"),
                    p -> {
                        d.loop(!d.loop());
                        redraw();
                    });
        } else {
            int min = d.minCooldown();
            adjuster(10, d.cooldown(), min, 3600, d::cooldown,
                    icon(Material.CLOCK, "&f冷卻：&e" + d.cooldown() + " 秒", lines(
                            "&7最低 " + min + " 秒（音檔長度）",
                            "&7吹完後玩家身上所有號角",
                            "&7（含原版山羊角）都要等冷卻結束")));
        }

        back(18, p -> editor().soundFiles(p));
        stepIndicator(22);
        next(26, p -> editor().volumeRange(p));
        fill(0, 26);
    }
}
