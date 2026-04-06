package awa.qwq672.lavaarcade.ai;

import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.IOException;

public class SkinManager {
    private static final File SKIN_FOLDER = new File("LavaArcade/skins");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SkinManager");

    public static void init() {
        if (!SKIN_FOLDER.exists()) SKIN_FOLDER.mkdirs();
    }

    // 不再应用自定义皮肤，避免签名验证失败
    public static void applyRandomSkin(ServerPlayerEntity player) {
        // 清空纹理，使用默认皮肤
        player.getGameProfile().getProperties().removeAll("textures");
        LOGGER.debug("AI {} 使用默认皮肤", player.getName().getString());
    }

    public static void openSkinFolder() {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(SKIN_FOLDER);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) Runtime.getRuntime().exec(new String[]{"explorer", SKIN_FOLDER.getAbsolutePath()});
                else if (os.contains("mac")) Runtime.getRuntime().exec(new String[]{"open", SKIN_FOLDER.getAbsolutePath()});
                else if (os.contains("nix") || os.contains("nux")) Runtime.getRuntime().exec(new String[]{"xdg-open", SKIN_FOLDER.getAbsolutePath()});
            }
        } catch (IOException e) {
            LOGGER.error("无法打开皮肤文件夹", e);
        }
    }
}