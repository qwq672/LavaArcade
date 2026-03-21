package awa.qwq672.lavaarcade.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LavaarcadeClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lavaarcade-client");
    private static boolean isModdedServer = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("LavaArcade 客户端初始化...");

        // TODO: 实体渲染距离控制需要 Mixin 或 Fabric API 更高版本，暂时注释
        /*
        EntityRenderEvents.BEFORE_RENDER.register((entity, context, matrices, vertexConsumers, light) -> {
            if (entity instanceof PlayerEntity player && player.getCommandTags().contains("lavaarcade_ai")) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    double distance = client.player.distanceTo(player);
                    AIConfig.ConfigData config = AIConfig.getConfig();
                    int renderDistanceBlocks = config.renderDistanceChunks * 16;
                    if (renderDistanceBlocks > 0 && distance > renderDistanceBlocks) {
                        return ActionResult.FAIL;
                    }
                }
            }
            return ActionResult.PASS;
        });
        */

        // 监听加入服务器事件
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            isModdedServer = client.isIntegratedServerRunning();
        });

        // 在选项界面添加按钮
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen) {
                ButtonWidget settingsButton = ButtonWidget.builder(
                                Text.translatable("text.lavaarcade.button.settings"),
                                button -> {
                                    if (isModdedServer || client.isIntegratedServerRunning()) {
                                        client.setScreen(new LavaarcadeConfigScreen(screen));
                                    } else {
                                        client.player.sendMessage(Text.literal("§c此服务器未安装LavaArcade，无法对其进行设置。"), false);
                                    }
                                })
                        .dimensions(10, 10, 120, 20).build();
                Screens.getButtons(screen).add(settingsButton);
            }
        });

        LOGGER.info("LavaArcade 客户端初始化完成。");
    }
}