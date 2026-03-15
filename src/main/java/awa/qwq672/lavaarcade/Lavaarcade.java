package awa.qwq672.lavaarcade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import awa.qwq672.lavaarcade.ai.NPCManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Lavaarcade implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lavaarcade");
    public static Path SKINS_DIR;

    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path lavaDir = gameDir.resolve("LavaArcade");
        SKINS_DIR = lavaDir.resolve("skins");

        try {
            Files.createDirectories(SKINS_DIR);
            LOGGER.info("LavaArcade 皮肤文件夹已创建/确认: {}", SKINS_DIR.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("无法创建皮肤文件夹", e);
        }

        NPCManager.init();
        LOGGER.info("LavaArcade 主模组初始化完成");
    }
}