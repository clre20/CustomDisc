package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import org.bukkit.Material;

/** 步驟 5：音量（0–100%）與可聽範圍（格）。 */
final class VolumeRangeMenu extends EditorMenu {

    VolumeRangeMenu(EditSession session) {
        super(session, 4, "音量與範圍", 5);
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        set(9, icon(Material.NOTE_BLOCK, "&f音量", "&70–100%"), null);
        adjuster(10, d.volume(), 0, 100, d::volume,
                icon(Material.NOTE_BLOCK, "&f音量：&e" + d.volume() + "%", lines("&7聲音的大小")));

        set(18, icon(Material.ENDER_EYE, "&f可聽範圍", "&71–512 格"), null);
        java.util.List<String> rangeLore = lines("&7離聲音多遠以內聽得到");
        if (d.type() == SoundType.DISC) {
            rangeLore.add("&7原生模式下，唱片機只會通知");
            rangeLore.add("&764 格內的玩家開始播放");
        }
        adjuster(19, d.range(), 1, 512, d::range,
                icon(Material.ENDER_EYE, "&f範圍：&e" + d.range() + " 格", rangeLore));

        back(27, p -> editor().loopCooldown(p));
        stepIndicator(31);
        next(35, p -> editor().overview(p));
        fill(0, 35);
    }
}
