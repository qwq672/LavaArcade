package awa.qwq672.lavaarcade.client;

import awa.qwq672.lavaarcade.ai.SkinManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class LavaarcadeConfigScreen extends Screen {
    private static final File CONFIG_FILE = new File("config/lavaarcade.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("LavaarcadeConfigScreen");
    private final Screen parent;
    private boolean enableAI = true;
    private int aiCount = 1;
    private int renderDistanceChunks = 8;
    private boolean enableCustomSkin = true;
    private boolean enableDefaultSkin = true;
    private boolean enableOnlineSkin = false;
    private int defaultSkinChance = 11;
    private boolean enableSpeech = true;
    private boolean enableRespawn = false;
    private TextFieldWidget aiCountField;
    private boolean enableONNX = true;

    private int scrollY = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;

    public static class ConfigData {
        public boolean enableAI = true;
        public int aiCount = 1;
        public int renderDistanceChunks = 8;
        public boolean enableCustomSkin = true;
        public boolean enableDefaultSkin = true;
        public boolean enableOnlineSkin = false;
        public int defaultSkinChance = 11;
        public boolean enableSpeech = true;
        public boolean enableRespawn = false;
        public boolean enableONNX;
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
                enableONNX = data.enableONNX;
                aiCount = data.aiCount;
                renderDistanceChunks = data.renderDistanceChunks;
                enableCustomSkin = data.enableCustomSkin;
                enableDefaultSkin = data.enableDefaultSkin;
                enableOnlineSkin = data.enableOnlineSkin;
                defaultSkinChance = data.defaultSkinChance;
                enableSpeech = data.enableSpeech;
                enableRespawn = data.enableRespawn;
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
        data.enableSpeech = enableSpeech;
        data.enableRespawn = enableRespawn;
        data.enableONNX = enableONNX;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = 30;
        int step = 25;

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableAI)
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_ai"),
                        (button, value) -> { enableAI = value; saveConfig(); }));
        y += step;

        this.aiCountField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("AI 数量"));
        this.aiCountField.setText(String.valueOf(aiCount));
        this.aiCountField.setChangedListener(text -> {
            try { int val = Integer.parseInt(text); if (val >= 1 && val <= 20) { aiCount = val; saveConfig(); } } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(this.aiCountField);
        y += step;

        this.addDrawableChild(new SliderWidget(centerX - 100, y, 200, 20,
                Text.translatable("text.lavaarcade.config.render_distance", renderDistanceChunks),
                renderDistanceChunks / 16.0) {
            protected void updateMessage() { int val = (int)(this.value * 16); setMessage(Text.translatable("text.lavaarcade.config.render_distance", val)); }
            protected void applyValue() { renderDistanceChunks = (int)(this.value * 16); saveConfig(); }
        });
        y += step;

        // 自定义皮肤
        this.addDrawableChild(ButtonWidget.builder(Text.literal(enableCustomSkin ? "C" : "C§c(关)"), button -> {
            enableCustomSkin = !enableCustomSkin; saveConfig(); button.setMessage(Text.literal(enableCustomSkin ? "C" : "C§c(关)"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_custom_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20).build());
        y += step;

        // 默认皮肤
        this.addDrawableChild(ButtonWidget.builder(Text.literal(enableDefaultSkin ? "D" : "D§c(关)"), button -> {
            enableDefaultSkin = !enableDefaultSkin; saveConfig(); button.setMessage(Text.literal(enableDefaultSkin ? "D" : "D§c(关)"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_default_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20).build());
        y += step;

        // 网络皮肤
        this.addDrawableChild(ButtonWidget.builder(Text.literal(enableOnlineSkin ? "N" : "N§c(关)"), button -> {
            enableOnlineSkin = !enableOnlineSkin; saveConfig(); button.setMessage(Text.literal(enableOnlineSkin ? "N" : "N§c(关)"));
        }).dimensions(centerX - 100, y, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.enable_online_skin.tooltip"), button -> {})
                .dimensions(centerX - 80, y, 180, 20).build());
        y += step;

        // 默认皮肤概率
        this.addDrawableChild(new SliderWidget(centerX - 100, y, 200, 20,
                Text.translatable("text.lavaarcade.config.default_skin_chance", defaultSkinChance),
                defaultSkinChance / 100.0) {
            protected void updateMessage() { int val = (int)(this.value * 100); setMessage(Text.translatable("text.lavaarcade.config.default_skin_chance", val)); }
            protected void applyValue() { defaultSkinChance = (int)(this.value * 100); saveConfig(); }
        });
        y += step;

        // 发言开关
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableSpeech)
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_speech"),
                        (button, value) -> { enableSpeech = value; saveConfig(); }));
        y += step;

        // 重生开关
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableRespawn)
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_respawn"),
                        (button, value) -> { enableRespawn = value; saveConfig(); }));
        y += step;

        // 在合适位置（例如重生开关之后）添加
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(enableONNX)
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("text.lavaarcade.config.enable_onnx"),
                        (button, value) -> {
                            enableONNX = value;
                            saveConfig();
                            // 显示提示信息
                            if (client != null) {
                                client.player.sendMessage(Text.literal("§e部分选项需要重新加载智能玩家才能生效"), false);
                            }
                        }));
        y += step;

        // 打开皮肤文件夹
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.lavaarcade.config.open_skin_folder"), button -> SkinManager.openSkinFolder())
                .dimensions(centerX - 100, y, 200, 20).build());
        y += step;

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> this.client.setScreen(this.parent))
                .dimensions(centerX - 50, y + 10, 100, 20).build());
        y += 30;

        contentHeight = y;
        int viewportHeight = this.height - 40;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        // 应用滚动偏移
        context.getMatrices().push();
        context.getMatrices().translate(0, -scrollY, 0);
        // 手动渲染所有子组件（因为 super.render 也会绘制，但会偏移？为了避免重复，我们调用 super 但需要调整鼠标坐标）
        // 更简单：遍历所有子组件，手动渲染并处理点击，但为了省事，我们仍然使用 super.render 并传入调整后的鼠标Y
        int translatedMouseY = mouseY + scrollY;
        super.render(context, mouseX, translatedMouseY, delta);
        context.getMatrices().pop();

        // 绘制滚动条
        if (maxScroll > 0) {
            int viewHeight = this.height - 40;
            int barHeight = Math.max(20, viewHeight * viewHeight / contentHeight);
            int barY = 20 + (scrollY * (viewHeight - barHeight) / maxScroll);
            context.fill(this.width - 10, barY, this.width - 5, barY + barHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll > 0) {
            scrollY -= (int) (amount * 20);
            scrollY = Math.max(0, Math.min(scrollY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double translatedY = mouseY + scrollY;
        return super.mouseClicked(mouseX, translatedY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double translatedY = mouseY + scrollY;
        return super.mouseDragged(mouseX, translatedY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double translatedY = mouseY + scrollY;
        return super.mouseReleased(mouseX, translatedY, button);
    }
}