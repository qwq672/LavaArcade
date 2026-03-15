package awa.qwq672.lavaarcade.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class LavaarcadeConfigScreen extends Screen {

    private static final File CONFIG_FILE = new File("config/lavaarcade.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Screen parent;
    private boolean enableAI = true;
    private int aiCount = 1;
    private TextFieldWidget aiCountField;

    public static class ConfigData {
        public boolean enableAI = true;
        public int aiCount = 1;
    }

    protected LavaarcadeConfigScreen(Screen parent) {
        super(Text.translatable("text.lavaarcade.config.title"));
        this.parent = parent;
        loadConfig();
    }

    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                enableAI = data.enableAI;
                aiCount = data.aiCount;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveConfig() {
        ConfigData data = new ConfigData();
        data.enableAI = enableAI;
        data.aiCount = aiCount;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        super.init();

        // 启用 AI 开关
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableAI)
                .build(this.width / 2 - 100, this.height / 2 - 60, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_ai"),
                        (button, value) -> {
                            enableAI = value;
                            saveConfig();
                        }));

        // AI 数量输入框
        this.aiCountField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 30, 200, 20, Text.literal("AI 数量"));
        this.aiCountField.setText(String.valueOf(aiCount));
        this.aiCountField.setChangedListener(text -> {
            try {
                int val = Integer.parseInt(text);
                if (val >= 1 && val <= 20) {
                    aiCount = val;
                    saveConfig();
                }
            } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(this.aiCountField);

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.back"),
                button -> this.client.setScreen(this.parent)
        ).dimensions(this.width / 2 - 50, this.height / 2 + 10, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}