package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** 建立／編輯流程中各頁共用的按鈕。 */
abstract class EditorMenu extends Menu {

    protected final int step;

    /**
     * @param titleKey messages.yml 裡的頁面標題鍵
     */
    EditorMenu(EditSession session, int rows, String titleKey, int step, Object... vars) {
        super(session, rows, title(session, CustomDisc.get().messages().get(titleKey, vars), step));
        this.step = step;
    }

    private static String title(EditSession s, String title, int step) {
        if (s.wizard()) {
            return CustomDisc.get().messages().get("gui.title-step", "title", title, "step", step, "total", Editor.STEPS);
        }
        return CustomDisc.get().messages().get("gui.title", "title", title);
    }

    protected Editor editor() {
        return CustomDisc.get().editor();
    }

    /** 選擇／目前選擇的提示行。 */
    protected String selectLine(boolean selected) {
        return text(selected ? "gui.selected" : "gui.click-select");
    }

    /**
     * 返回按鈕：建立流程中是「上一步」（第一步是「取消建立」），編輯時是「返回總覽」。
     */
    protected void back(int slot, Consumer<Player> previousStep) {
        if (session.wizard()) {
            if (previousStep == null) {
                set(slot, icon(Material.BARRIER, text("gui.cancel-create"), lines("gui.cancel-create-lore")),
                        Player::closeInventory);
            } else {
                set(slot, icon(Material.ARROW, text("gui.previous-step"), List.of()), previousStep);
            }
        } else {
            set(slot, icon(Material.ARROW, text("gui.back-overview"), List.of()), p -> editor().overview(p));
        }
    }

    /** 下一步按鈕（只在建立流程中顯示）。 */
    protected void next(int slot, Consumer<Player> nextStep) {
        if (session.wizard()) {
            set(slot, icon(Material.LIME_STAINED_GLASS_PANE, text("gui.next-step"), List.of()), nextStep);
        }
    }

    protected void stepIndicator(int slot) {
        if (session.wizard()) {
            set(slot, icon(Material.PAPER, text("gui.step-name", "step", step, "total", Editor.STEPS),
                    lines("gui.step-lore", "type", session.draft().type().displayName(),
                            "name", session.draft().name())), null);
        }
    }

    /** 數值調整列：[-10][-1][數值][+1][+10]。 */
    protected void adjuster(int firstSlot, int value, int min, int max, Consumer<Integer> setter, ItemStack display) {
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
                            text(minus ? "gui.adjust-minus" : "gui.adjust-plus", "delta", minus ? delta : "" + delta),
                            lines("gui.adjust-lore", "target", target)),
                    p -> {
                        setter.accept(target);
                        redraw();
                    });
        }
    }
}
