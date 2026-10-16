package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;

import java.util.UUID;

/** 一位玩家正在建立或編輯的一張唱片／一支號角。 */
public final class EditSession {

    private final UUID player;
    private final CustomSound draft;
    private final boolean creating;
    private boolean navigating;
    private String previewEvent;

    EditSession(UUID player, CustomSound draft, boolean creating) {
        this.player = player;
        this.draft = draft;
        this.creating = creating;
    }

    public UUID player() {
        return player;
    }

    public CustomSound draft() {
        return draft;
    }

    /** true = /cd create 的建立流程；false = /cd edit。 */
    public boolean creating() {
        return creating;
    }

    private boolean reachedOverview;

    /** 建立流程中、還沒走到總覽頁：每一步完成後自動進下一步。 */
    public boolean wizard() {
        return creating && !reachedOverview;
    }

    void reachedOverview() {
        reachedOverview = true;
    }

    boolean navigating() {
        return navigating;
    }

    void navigating(boolean v) {
        navigating = v;
    }

    String previewEvent() {
        return previewEvent;
    }

    void previewEvent(String v) {
        previewEvent = v;
    }
}
