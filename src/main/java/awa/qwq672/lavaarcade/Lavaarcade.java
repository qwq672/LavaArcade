package awa.qwq672.lavaarcade;

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

        // 初始化皮肤管理器
        SkinManager.init();
        // 初始化AI管理器
        NPCManager.init();

        LOGGER.info("LavaArcade 主模组初始化完成");
    }
}