package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** 建立／編輯流程中各頁共用的按鈕。 */
abstract class EditorMenu extends Menu {

    protected final int step;

    EditorMenu(EditSession session, int rows, String title, int step) {
        super(session, rows, title(session, title, step));
        this.step = step;
    }

    private static String title(EditSession s, String title, int step) {
        if (s.wizard()) {
            return "&8" + title + " &7(" + step + "/" + Editor.STEPS + ")";
        }
        return "&8" + title;
    }

    protected Editor editor() {
        return CustomDisc.get().editor();
    }

    /**
     * 返回按鈕：建立流程中是「上一步」，編輯時是「返回總覽」。
     */
    protected void back(int slot, Consumer<Player> previousStep) {
        if (session.wizard()) {
            if (previousStep == null) {
                set(slot, icon(Material.BARRIER, "&c取消建立", "&7不儲存並關閉"), p -> {
                    p.closeInventory();
                });
            } else {
                set(slot, icon(Material.ARROW, "&f上一步"), previousStep);
            }
        } else {
            set(slot, icon(Material.ARROW, "&f返回總覽"), p -> editor().overview(p));
        }
    }

    /** 下一步按鈕（只在建立流程中顯示）。 */
    protected void next(int slot, Consumer<Player> nextStep) {
        if (session.wizard()) {
            set(slot, icon(Material.LIME_STAINED_GLASS_PANE, "&a下一步"), nextStep);
        }
    }

    protected void stepIndicator(int slot) {
        if (session.wizard()) {
            set(slot, icon(Material.PAPER, "&f第 " + step + "/" + Editor.STEPS + " 步",
                    "&7建立" + session.draft().type().displayName() + "：&f" + session.draft().name()), null);
        }
    }

    /** 數值調整列：[-10][-1][數值][+1][+10]。 */
    protected void adjuster(int firstSlot, int value, int min, int max, Consumer<Integer> setter, org.bukkit.inventory.ItemStack display) {
        int[] deltas = {-10, -1, 0, 1, 10};
        int[] offsets = {0, 1, 3, 5, 6};
        for (int i = 0; i < deltas.length; i++) {
            int delta = deltas[i];
            int slot = firstSlot + offsets[i];
            if (delta == 0) {
                set(slot, display, null);
                continue;
            }
            boolean minus = delta < 0;
            int target = Math.max(min, Math.min(max, value + delta));
            set(slot, icon(minus ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                            (minus ? "&c" : "&a") + (delta > 0 ? "+" : "") + delta,
                            "&7點擊後：&f" + target),
                    p -> {
                        setter.accept(target);
                        redraw();
                    });
        }
    }
}
