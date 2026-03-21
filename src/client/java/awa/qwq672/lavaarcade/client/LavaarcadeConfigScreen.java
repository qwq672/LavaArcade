package awa.qwq672.lavaarcade.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

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
    private int renderDistanceChunks = 8; // 默认8区块
    private boolean enableCustomSkin = true;
    private boolean enableDefaultSkin = true;
    private boolean enableOnlineSkin = false;
    private int defaultSkinChance = 11;
    private TextFieldWidget aiCountField;

    public static class ConfigData {
        public boolean enableAI = true;
        public int aiCount = 1;
        public int renderDistanceChunks = 8; // 新增
        public boolean enableCustomSkin = true;
        public boolean enableDefaultSkin = true;
        public boolean enableOnlineSkin = false;
        public int defaultSkinChance = 11;
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
                renderDistanceChunks = data.renderDistanceChunks;
                enableCustomSkin = data.enableCustomSkin;
                enableDefaultSkin = data.enableDefaultSkin;
                enableOnlineSkin = data.enableOnlineSkin;
                defaultSkinChance = data.defaultSkinChance;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveConfig() {
        ConfigData data = new ConfigData();
        data.enableAI = enableAI;
        data.aiCount = aiCount;
        data.renderDistanceChunks = renderDistanceChunks;
        data.enableCustomSkin = enableCustomSkin;
        data.enableDefaultSkin = enableDefaultSkin;
        data.enableOnlineSkin = enableOnlineSkin;
        data.defaultSkinChance = defaultSkinChance;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = 30;

        // 启用 AI 开关
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableAI)
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_ai"),
                        (button, value) -> {
                            enableAI = value;
                            saveConfig();
                        }));
        y += 25;

        // AI 数量输入框
        this.aiCountField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("AI 数量"));
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
        y += 25;

        // AI 显示范围（区块）滑块
        this.addDrawableChild(new SliderWidget(centerX - 100, y, 200, 20,
                Text.translatable("text.lavaarcade.config.render_distance", renderDistanceChunks),
                (renderDistanceChunks) / 16.0) { // 0-16 区块映射到 0-1
            @Override
            protected void updateMessage() {
                int val = (int) (this.value * 16);
                setMessage(Text.translatable("text.lavaarcade.config.render_distance", val));
            }

            @Override
            protected void applyValue() {
                renderDistanceChunks = (int) (this.value * 16);
                saveConfig();
            }
        });
        y += 25;

        // 自定义皮肤开关（正方形按钮，文字 "L"）
        this.addDrawableChild(ButtonWidget.builder(Text.literal("L"), button -> {
            enableCustomSkin = !enableCustomSkin;
            saveConfig();
            button.setMessage(Text.literal(enableCustomSkin ? "L" : "L§c（关）"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        // 说明文字
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_custom_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20)
                .build());
        y += 25;

        // 默认皮肤开关（正方形按钮）
        this.addDrawableChild(ButtonWidget.builder(Text.literal("D"), button -> {
            enableDefaultSkin = !enableDefaultSkin;
            saveConfig();
            button.setMessage(Text.literal(enableDefaultSkin ? "D" : "D§c（关）"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_default_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20)
                .build());
        y += 25;

        // 网络皮肤开关
        this.addDrawableChild(ButtonWidget.builder(Text.literal("N"), button -> {
            enableOnlineSkin = !enableOnlineSkin;
            saveConfig();
            button.setMessage(Text.literal(enableOnlineSkin ? "N" : "N§c（关）"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_online_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20)
                .build());
        y += 25;

        // 默认皮肤概率滑块
        this.addDrawableChild(new SliderWidget(centerX - 100, y, 200, 20,
                Text.translatable("text.lavaarcade.config.default_skin_chance", defaultSkinChance),
                defaultSkinChance / 100.0) {
            @Override
            protected void updateMessage() {
                int val = (int) (this.value * 100);
                setMessage(Text.translatable("text.lavaarcade.config.default_skin_chance", val));
            }

            @Override
            protected void applyValue() {
                defaultSkinChance = (int) (this.value * 100);
                saveConfig();
            }
        });
        y += 25;

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.back"),
                button -> this.client.setScreen(this.parent)
        ).dimensions(centerX - 50, y + 10, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}