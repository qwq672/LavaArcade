package awa.qwq672.lavaarcade.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseListScreen extends Screen {
    protected final Screen parent;
    protected int scrollY = 0;
    protected int maxScroll = 0;
    protected List<Text> entries = new ArrayList<>();

    protected BaseListScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract void refreshEntries();

    @Override
    protected void init() {
        super.init();
        refreshEntries();
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("刷新"), button -> {
                    refreshEntries();
                })
                .dimensions(10, this.height - 30, 80, 20).build());
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("返回"), button -> client.setScreen(parent))
                .dimensions(this.width - 90, this.height - 30, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        int y = 30;
        int visibleHeight = this.height - 70;
        int startIdx = scrollY / 20;
        int endIdx = Math.min(entries.size(), startIdx + visibleHeight / 20 + 1);
        for (int i = startIdx; i < endIdx && y < this.height - 40; i++) {
            context.drawText(textRenderer, entries.get(i), 10, y, 0xFFFFFF, false);
            y += 20;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (entries.size() * 20 > this.height - 70) {
            maxScroll = Math.max(0, entries.size() * 20 - (this.height - 70));
            scrollY = (int) Math.max(0, Math.min(maxScroll, scrollY - amount * 20));
        }
        return true;
    }
}