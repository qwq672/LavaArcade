package awa.qwq672.lavaarcade.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LavaarcadeClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lavaarcade-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("LavaArcade 客户端初始化...");

        // 在选项界面添加 "LavaArcade 设置" 按钮
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen) {
                ButtonWidget settingsButton = ButtonWidget.builder(
                        Text.translatable("text.lavaarcade.button.settings"),
                        button -> client.setScreen(new LavaarcadeConfigScreen(screen))
                ).dimensions(10, 10, 120, 20).build();
                Screens.getButtons(screen).add(settingsButton);
            }
        });

        LOGGER.info("LavaArcade 客户端初始化完成。");
    }
}