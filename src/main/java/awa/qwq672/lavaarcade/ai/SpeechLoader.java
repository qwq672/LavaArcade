package awa.qwq672.lavaarcade.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class SpeechLoader {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("lavaarcade");
    private static final File EXTERNAL_FILE = CONFIG_DIR.resolve("speech.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject root = null;

    public static void init() {
        try (Reader reader = getReader()) {
            root = GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            e.printStackTrace();
            root = new JsonObject();
        }
    }

    private static Reader getReader() throws IOException {
        if (EXTERNAL_FILE.exists()) {
            // 使用外部文件
            return new FileReader(EXTERNAL_FILE);
        } else {
            // 使用 jar 内的默认资源
            InputStream is = SpeechLoader.class.getResourceAsStream("/assets/lavaarcade/speech.json");
            if (is == null) {
                throw new FileNotFoundException("Cannot find default speech.json in jar");
            }
            return new InputStreamReader(is);
        }
    }

    public static String getRandomMessage(String key, Map<String, String> placeholders) {
        if (root == null) return "???";
        JsonElement element = root;
        for (String part : key.split("\\.")) {
            if (element != null && element.isJsonObject()) {
                element = element.getAsJsonObject().get(part);
            } else {
                element = null;
                break;
            }
        }
        if (element == null || !element.isJsonArray()) return "???";
        List<String> list = new ArrayList<>();
        element.getAsJsonArray().forEach(e -> list.add(e.getAsString()));
        if (list.isEmpty()) return "???";
        String template = list.get(new Random().nextInt(list.size()));
        return replacePlaceholders(template, placeholders);
    }

    private static String replacePlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}