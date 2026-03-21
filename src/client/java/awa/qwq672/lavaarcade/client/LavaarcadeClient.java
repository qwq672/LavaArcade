package awa.qwq672.lavaarcade.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LavaarcadeClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lavaarcade-client");
    private static boolean isInGame = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("LavaArcade 客户端初始化...");

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> isInGame = true);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> isInGame = false);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen) {
                ButtonWidget settingsButton = ButtonWidget.builder(
                                Text.translatable("text.lavaarcade.button.settings"),
                                button -> {
                                    if (isInGame) {
                                        client.setScreen(new LavaarcadeConfigScreen(screen));
                                    } else {
                                        // 使用 Toast 提示
                                        MinecraftClient.getInstance().getToastManager().add(new SystemToast(
                                                SystemToast.Type.TUTORIAL_HINT,
                                                Text.literal("提示"),
                                                Text.literal("请先进入游戏世界再修改 LavaArcade 设置。")
                                        ));
                                    }
                                })
                        .dimensions(10, 10, 120, 20).build();
                Screens.getButtons(screen).add(settingsButton);
            }
        });

        LOGGER.info("LavaArcade 客户端初始化完成。");
    }
}