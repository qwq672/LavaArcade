package awa.qwq672.lavaarcade.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class AIConfig {
    private static final File CONFIG_FILE = new File("config/lavaarcade.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ConfigData {
        public boolean enableAI = true;
        public int aiCount = 1;
        public int renderDistanceChunks = 8; // 新增
        public boolean enableCustomSkin = true;
        public boolean enableDefaultSkin = true;
        public boolean enableOnlineSkin = false;
        public int defaultSkinChance = 11;
    }

    public static ConfigData getConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new ConfigData();
    }
}