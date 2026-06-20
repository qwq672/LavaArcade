package awa.qwq672.lavaarcade;

import awa.qwq672.lavaarcade.ai.AIConfig;
import awa.qwq672.lavaarcade.ai.ModelManager;
import awa.qwq672.lavaarcade.ai.SpeechManager;
import awa.qwq672.lavaarcade.game.GameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import awa.qwq672.lavaarcade.ai.NPCManager;
import awa.qwq672.lavaarcade.ai.SkinManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Lavaarcade implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lavaarcade");
    public static final int INTERNAL_VERSION = 1;
    public static final String EXTERNAL_VERSION = "v260620-2";

    public static Path SKINS_DIR;

    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        SKINS_DIR = gameDir.resolve("LavaArcade").resolve("skins");
        try {
            Files.createDirectories(SKINS_DIR);
            LOGGER.info("LavaArcade 皮肤文件夹: {}", SKINS_DIR.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("无法创建皮肤文件夹", e);
        }

        SkinManager.init();
        SpeechManager.init();
        ModelManager.init();
        NPCManager.init();
        GameManager.init();

        AIConfig.ConfigData config = AIConfig.getConfig();
        if (config.enableCortex) {
            String defaultModel = ModelManager.getCurrentModelName();
            if (defaultModel != null) {
                ModelManager.setCurrentModel(defaultModel, null);
            }
        }

        LOGGER.info("LavaArcade 主模组初始化完成 (版本: {}, 内部版本: {})", EXTERNAL_VERSION, INTERNAL_VERSION);
    }
}