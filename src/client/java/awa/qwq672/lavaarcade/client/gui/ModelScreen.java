package awa.qwq672.lavaarcade.client.gui;

import awa.qwq672.lavaarcade.ai.ModelManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModelScreen extends BaseListScreen {
    public ModelScreen(Screen parent) {
        super(Text.literal("模型管理"), parent);
    }

    @Override
    protected void refreshEntries() {
        entries.clear();
        ModelManager.reloadModels();
        entries.add(Text.literal("§6当前模型: §e" + ModelManager.getCurrentModelName()));
        entries.add(Text.literal(" "));
        for (String name : ModelManager.getModelNames()) {
            ModelManager.ModelEntry entry = ModelManager.getModel(name);
            String status = name.equals(ModelManager.getCurrentModelName()) ? "§a[已加载] " : "§7[可用] ";
            String info = entry != null ? " §7(输入: " + entry.metadata.inputDim + " 输出: " + entry.metadata.outputDim + ")" : "";
            entries.add(Text.literal(status + name + info));
        }
        if (ModelManager.getModelNames().isEmpty()) {
            entries.add(Text.literal("§c未找到任何模型"));
        }
    }
}