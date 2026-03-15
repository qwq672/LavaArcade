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
    private static final Random RANDOM = new Random();
    private static List<File> skinFiles = new ArrayList<>();
    private static Map<String, String> skinDataCache = new HashMap<>();

    public static void init() {
        scanSkinFolder();
    }

    public static void scanSkinFolder() {
        skinFiles.clear();
        if (!SKIN_FOLDER.exists() || !SKIN_FOLDER.isDirectory()) {
            SKIN_FOLDER.mkdirs();
            return;
        }

        File[] files = SKIN_FOLDER.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".PNG")
        );

        if (files != null) {
            for (File file : files) {
                if (isValidSkin(file)) {
                    skinFiles.add(file);
                }
            }
        }
    }

    private static boolean isValidSkin(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return false;
            return image.getWidth() == 64 && image.getHeight() == 64;
        } catch (IOException e) {
            return false;
        }
    }

    public static File getRandomSkinFile() {
        if (skinFiles.isEmpty()) return null;
        return skinFiles.get(RANDOM.nextInt(skinFiles.size()));
    }

    public static String getSkinBase64(File skinFile) {
        if (skinFile == null) return null;

        String fileName = skinFile.getName();
        if (skinDataCache.containsKey(fileName)) {
            return skinDataCache.get(fileName);
        }

        try (FileInputStream fis = new FileInputStream(skinFile)) {
            byte[] fileData = fis.readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(fileData);
            String textureJson = String.format(
                    "{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64,%s\"}}}",
                    base64
            );
            String result = Base64.getEncoder().encodeToString(textureJson.getBytes());
            skinDataCache.put(fileName, result);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void applyRandomSkin(ServerPlayerEntity player) {
        File skinFile = getRandomSkinFile();
        if (skinFile == null) return;

        String skinBase64 = getSkinBase64(skinFile);
        if (skinBase64 == null) return;

        GameProfile profile = player.getGameProfile();
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", skinBase64, ""));
        // 不需要额外调用刷新，FakePlayer 生成时会读取 GameProfile
    }
}