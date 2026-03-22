package awa.qwq672.lavaarcade.ai;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class SkinManager {
    private static final File SKIN_FOLDER = new File("lavaarcade/ai_skin");
    private static final File CAPE_FOLDER = new File("lavaarcade/ai_capes");
    private static final Random RANDOM = new Random();
    private static List<File> skinFiles = new ArrayList<>();
    private static List<File> capeFiles = new ArrayList<>();
    private static Map<String, String> skinDataCache = new HashMap<>();
    private static Map<String, String> capeDataCache = new HashMap<>();

    public static void init() {
        scanSkinFolder();
        scanCapeFolder();
    }

    public static void scanSkinFolder() {
        skinFiles.clear();
        if (!SKIN_FOLDER.exists() || !SKIN_FOLDER.isDirectory()) {
            SKIN_FOLDER.mkdirs();
            return;
        }
        File[] files = SKIN_FOLDER.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                if (isValidSkin(file)) skinFiles.add(file);
            }
        }
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

    public static void scanCapeFolder() {
        capeFiles.clear();
        if (!CAPE_FOLDER.exists() || !CAPE_FOLDER.isDirectory()) {
            CAPE_FOLDER.mkdirs();
            return;
        }
        File[] files = CAPE_FOLDER.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                try {
                    if (ImageIO.read(file) != null) capeFiles.add(file);
                } catch (IOException ignored) {}
            }
        }
    }

    public static File getRandomSkinFile() {
        return skinFiles.isEmpty() ? null : skinFiles.get(RANDOM.nextInt(skinFiles.size()));
    }

    public static File getRandomCapeFile() {
        return capeFiles.isEmpty() ? null : capeFiles.get(RANDOM.nextInt(capeFiles.size()));
    }

    public static String getSkinBase64(File skinFile) {
        return getImageBase64(skinFile, skinDataCache);
    }

    public static String getCapeBase64(File capeFile) {
        return getImageBase64(capeFile, capeDataCache);
    }

    private static String getImageBase64(File file, Map<String, String> cache) {
        if (file == null) return null;
        String fileName = file.getName();
        if (cache.containsKey(fileName)) return cache.get(fileName);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = fis.readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(data);
            String json;
            if (file.getParentFile().getName().equals("ai_capes")) {
                json = String.format("{\"textures\":{\"CAPE\":{\"url\":\"data:image/png;base64,%s\"}}}", base64);
            } else {
                json = String.format("{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64,%s\"}}}", base64);
            }
            String result = Base64.getEncoder().encodeToString(json.getBytes());
            cache.put(fileName, result);
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

        // 皮肤
        if (config.enableCustomSkin && !skinFiles.isEmpty()) {
            File skinFile = getRandomSkinFile();
            if (skinFile != null) {
                String skinBase64 = getSkinBase64(skinFile);
                if (skinBase64 != null) {
                    profile.getProperties().put("textures", new Property("textures", skinBase64, ""));
                }
            }
        } else if (config.enableDefaultSkin && RANDOM.nextInt(100) < config.defaultSkinChance) {
            // 默认皮肤：清除纹理即可
            profile.getProperties().removeAll("textures");
        }

        // 披风（简单合并，会丢失皮肤，仅演示）
        if (config.enableCape && !capeFiles.isEmpty()) {
            File capeFile = getRandomCapeFile();
            if (capeFile != null) {
                String capeBase64 = getCapeBase64(capeFile);
                if (capeBase64 != null) {
                    profile.getProperties().removeAll("textures");
                    profile.getProperties().put("textures", new Property("textures", capeBase64, ""));
                }
            }
        }
    }

    public static void openSkinFolder() {
        openFolder(SKIN_FOLDER);
    }

    public static void openCapeFolder() {
        openFolder(CAPE_FOLDER);
    }

    private static void openFolder(File folder) {
        if (!folder.exists()) folder.mkdirs();
        if (!java.awt.Desktop.isDesktopSupported()) {
            System.err.println("无法打开文件夹：当前环境不支持 Desktop API。");
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(folder);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (java.awt.HeadlessException e) {
            System.err.println("无法打开文件夹：Headless 环境。");
        }
    }
}