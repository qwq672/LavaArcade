package awa.qwq672.lavaarcade.ai;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class SkinManager {
    // 统一皮肤文件夹：LavaArcade/skins
    private static final File SKIN_FOLDER = new File("LavaArcade/skins");
    private static final Random RANDOM = new Random();
    private static final List<File> skinFiles = new ArrayList<>();
    private static final Map<String, String> skinDataCache = new HashMap<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SkinManager");

    public static void init() {
        scanSkinFolder();
    }

    public static void scanSkinFolder() {
        skinFiles.clear();
        if (!SKIN_FOLDER.exists() || !SKIN_FOLDER.isDirectory()) {
            SKIN_FOLDER.mkdirs();
            LOGGER.info("创建皮肤文件夹: {}", SKIN_FOLDER.getAbsolutePath());
            return;
        }
        File[] files = SKIN_FOLDER.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                if (isValidSkin(file)) {
                    skinFiles.add(file);
                    LOGGER.debug("找到皮肤文件: {}", file.getName());
                }
            }
        }
        LOGGER.info("共加载 {} 个有效皮肤文件", skinFiles.size());
    }

    private static boolean isValidSkin(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) return false;
            int w = img.getWidth(), h = img.getHeight();
            return (w == 64 && h == 64) || (w == 64 && h == 32);
        } catch (IOException e) {
            return false;
        }
    }

    public static File getRandomSkinFile() {
        return skinFiles.isEmpty() ? null : skinFiles.get(RANDOM.nextInt(skinFiles.size()));
    }

    public static String getSkinBase64(File skinFile) {
        if (skinFile == null) return null;
        String fileName = skinFile.getName();
        if (skinDataCache.containsKey(fileName)) return skinDataCache.get(fileName);
        try (FileInputStream fis = new FileInputStream(skinFile)) {
            byte[] data = fis.readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(data);
            String json = String.format("{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64,%s\"}}}", base64);
            String result = Base64.getEncoder().encodeToString(json.getBytes());
            skinDataCache.put(fileName, result);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void applyRandomSkin(ServerPlayerEntity player) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        GameProfile profile = player.getGameProfile();
        profile.getProperties().removeAll("textures");

        if (config.enableCustomSkin && !skinFiles.isEmpty()) {
            File skinFile = getRandomSkinFile();
            if (skinFile != null) {
                String skinBase64 = getSkinBase64(skinFile);
                if (skinBase64 != null) {
                    profile.getProperties().put("textures", new Property("textures", skinBase64, ""));
                    LOGGER.debug("为 AI {} 应用自定义皮肤: {}", player.getName().getString(), skinFile.getName());
                    refreshSkinForPlayer(player);
                    return;
                }
            }
        }
        if (config.enableDefaultSkin && RANDOM.nextInt(100) < config.defaultSkinChance) {
            LOGGER.debug("为 AI {} 应用默认皮肤", player.getName().getString());
            profile.getProperties().removeAll("textures");
            refreshSkinForPlayer(player);
        }
        // 网络皮肤预留
    }

    // 强制客户端刷新皮肤（通过重新发送玩家信息）
    private static void refreshSkinForPlayer(ServerPlayerEntity player) {
        if (player.getServer() != null) {
            // 发送 PlayerListS2CPacket 更新玩家信息，触发皮肤重载
            player.getServer().getPlayerManager().sendToAll(
                    new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_GAME_MODE, player)
            );
        }
    }

    // 安全打开皮肤文件夹（不会崩溃）
    public static void openSkinFolder() throws Exception {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                desktop.open(SKIN_FOLDER);
                LOGGER.info("已打开皮肤文件夹: {}", SKIN_FOLDER.getAbsolutePath());
                return;
            }
        }
        // 备选方案：使用系统命令
        String os = System.getProperty("os.name").toLowerCase();
        String[] cmd;
        if (os.contains("win")) {
            cmd = new String[]{"explorer", SKIN_FOLDER.getAbsolutePath()};
        } else if (os.contains("mac")) {
            cmd = new String[]{"open", SKIN_FOLDER.getAbsolutePath()};
        } else if (os.contains("nix") || os.contains("nux")) {
            cmd = new String[]{"xdg-open", SKIN_FOLDER.getAbsolutePath()};
        } else {
            throw new UnsupportedOperationException("不支持的操作系统: " + os);
        }
        Runtime.getRuntime().exec(cmd);
        LOGGER.info("通过系统命令打开皮肤文件夹: {}", SKIN_FOLDER.getAbsolutePath());
    }
}