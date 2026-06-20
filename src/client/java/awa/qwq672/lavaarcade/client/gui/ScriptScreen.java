package awa.qwq672.lavaarcade.client.gui;

import awa.qwq672.lavaarcade.game.GameManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ScriptScreen extends BaseListScreen {
    public ScriptScreen(Screen parent) {
        super(Text.literal("脚本管理"), parent);
    }

    @Override
    protected void refreshEntries() {
        entries.clear();
        if (GameManager.hasActiveGame()) {
            entries.add(Text.literal("§c⚠ 有游戏正在进行中！"));
            entries.add(Text.literal("§c请先停止游戏再刷新脚本。"));
        }
        GameManager.reloadScripts();
        entries.add(Text.literal("§6可用脚本:"));
        var scripts = GameManager.getAvailableScripts();
        if (scripts.isEmpty()) {
            entries.add(Text.literal("§c未找到任何脚本"));
        } else {
            for (String name : scripts) {
                entries.add(Text.literal("§7- §f" + name));
            }
        }
    }
}