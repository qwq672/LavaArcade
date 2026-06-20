package awa.qwq672.lavaarcade.ai;

import awa.qwq672.lavaarcade.Lavaarcade;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelManager");
    private static final Path MODELS_DIR = FabricLoader.getInstance().getGameDir()
            .resolve("LavaArcade").resolve("models");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ModelEntry> availableModels = new LinkedHashMap<>();
    private static String currentModelName = null;

    public static class ModelEntry {
        public String name;
        public Path modelPath;
        public Path metadataPath;
        public Path scriptPath;
        public ModelMetadata metadata;
    }

    public static class ModelMetadata {
        public int inputDim;
        public int outputDim;
        public List<String> featureOrder;
        public List<String> actionOrder;
        public Map<String, Object> extra;
    }

    public static void init() {
        try {
            Files.createDirectories(MODELS_DIR);
        } catch (IOException e) {
            LOGGER.error("无法创建模型目录", e);
        }
        reloadModels();
    }

    public static void reloadModels() {
        availableModels.clear();
        scanModels();
        if (availableModels.isEmpty()) {
            Path builtinPath = ONNXUtils.getModelPath();
            if (builtinPath != null) {
                ModelEntry entry = new ModelEntry();
                entry.name = "default";
                entry.modelPath = builtinPath;
                entry.metadata = createDefaultMetadata();
                availableModels.put("default", entry);
                currentModelName = "default";
                LOGGER.info("已加载内置默认模型 (路径: {})", builtinPath);
            } else {
                LOGGER.warn("警告：默认模型未找到，这可能是模组不完整或测试版问题导致的。");
                currentModelName = null;
            }
        } else {
            currentModelName = availableModels.keySet().iterator().next();
            LOGGER.info("默认模型: {}", currentModelName);
        }
        if (currentModelName != null && !availableModels.containsKey(currentModelName)) {
            currentModelName = availableModels.isEmpty() ? null : availableModels.keySet().iterator().next();
        }
    }

    public static void scanModels() {
        availableModels.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODELS_DIR)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    loadFromFolder(entry);
                } else if (entry.getFileName().toString().endsWith(".zip")) {
                    loadFromZip(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描模型目录失败", e);
        }
        LOGGER.info("模型扫描完成，找到 {} 个模型", availableModels.size());
    }

    private static void loadFromFolder(Path folder) {
        String name = folder.getFileName().toString();
        Path onnx = folder.resolve("model.onnx");
        Path metadata = folder.resolve("model_metadata.json");
        if (!Files.exists(onnx) || !Files.exists(metadata)) {
            LOGGER.warn("文件夹 {} 缺少 model.onnx 或 model_metadata.json，跳过", name);
            return;
        }
        ModelEntry entry = new ModelEntry();
        entry.name = name;
        entry.modelPath = onnx;
        entry.metadataPath = metadata;
        try {
            entry.metadata = loadMetadata(metadata);
            if (entry.metadata.extra != null && entry.metadata.extra.containsKey("lavaarcade_version")) {
                int modelVersion = ((Number) entry.metadata.extra.get("lavaarcade_version")).intValue();
                if (modelVersion > Lavaarcade.INTERNAL_VERSION) {
                    LOGGER.error("模型 {} 需要 LavaArcade {} 或更高版本，当前为 {}", name, modelVersion, Lavaarcade.INTERNAL_VERSION);
                    return;
                }
            }
            if (entry.metadata.inputDim != 28) {
                LOGGER.error("模型 {} 输入维度为 {}，当前仅支持 28", name, entry.metadata.inputDim);
                return;
            }
            if (entry.metadata.outputDim != 8) {
                LOGGER.error("模型 {} 输出维度为 {}，当前仅支持 8", name, entry.metadata.outputDim);
                return;
            }
            if (entry.metadata.featureOrder != null && entry.metadata.featureOrder.size() != entry.metadata.inputDim) {
                LOGGER.warn("模型 {} 特征顺序长度与 inputDim 不匹配，使用默认顺序", name);
                entry.metadata.featureOrder = getDefaultFeatureOrder(entry.metadata.inputDim);
            }
            if (entry.metadata.actionOrder != null && entry.metadata.actionOrder.size() != entry.metadata.outputDim) {
                LOGGER.warn("模型 {} 动作顺序长度与 outputDim 不匹配，使用默认顺序", name);
                entry.metadata.actionOrder = getDefaultActionOrder(entry.metadata.outputDim);
            }
            Path script = folder.resolve("behavior_script.py");
            entry.scriptPath = Files.exists(script) ? script : null;
            availableModels.put(name, entry);
            LOGGER.info("加载模型: {} (输入: {}, 输出: {})", name, entry.metadata.inputDim, entry.metadata.outputDim);
        } catch (Exception e) {
            LOGGER.error("加载模型 {} 失败", name, e);
        }
    }

    private static ModelMetadata loadMetadata(Path metadataPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(metadataPath)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            ModelMetadata meta = new ModelMetadata();
            meta.inputDim = json.get("inputDim").getAsInt();
            meta.outputDim = json.get("outputDim").getAsInt();
            if (json.has("featureOrder")) {
                meta.featureOrder = parseStringList(json.get("featureOrder").getAsJsonArray());
            } else {
                meta.featureOrder = getDefaultFeatureOrder(meta.inputDim);
            }
            if (json.has("actionOrder")) {
                meta.actionOrder = parseStringList(json.get("actionOrder").getAsJsonArray());
            } else {
                meta.actionOrder = getDefaultActionOrder(meta.outputDim);
            }
            meta.extra = json.has("extra") ? GSON.fromJson(json.get("extra"), HashMap.class) : new HashMap<>();
            return meta;
        }
    }

    private static List<String> parseStringList(com.google.gson.JsonArray array) {
        List<String> list = new ArrayList<>();
        for (var elem : array) {
            list.add(elem.getAsString());
        }
        return list;
    }

    private static List<String> getDefaultFeatureOrder(int dim) {
        List<String> defaultOrder = Arrays.asList(
                "health", "food", "x", "z", "y",
                "vel_x", "vel_z", "on_ground", "in_water", "yaw", "pitch",
                "enemy_dist", "enemy_angle", "attack_cooldown", "held_item",
                "mode_pvp", "mode_minigame", "mode_survival",
                "subgoal_0", "subgoal_1", "subgoal_2", "subgoal_3", "subgoal_4",
                "subgoal_5", "subgoal_6", "subgoal_7", "subgoal_8", "subgoal_9"
        );
        if (defaultOrder.size() >= dim) {
            return defaultOrder.subList(0, dim);
        }
        return defaultOrder;
    }

    private static List<String> getDefaultActionOrder(int dim) {
        List<String> defaultOrder = Arrays.asList("jump", "sneak", "attack", "use", "forward", "back", "left", "right");
        if (defaultOrder.size() >= dim) {
            return defaultOrder.subList(0, dim);
        }
        return defaultOrder;
    }

    private static ModelMetadata createDefaultMetadata() {
        ModelMetadata meta = new ModelMetadata();
        meta.inputDim = 28;
        meta.outputDim = 8;
        meta.featureOrder = getDefaultFeatureOrder(28);
        meta.actionOrder = getDefaultActionOrder(8);
        meta.extra = new HashMap<>();
        meta.extra.put("description", "内置默认模型（28维输入）");
        meta.extra.put("lavaarcade_version", Lavaarcade.INTERNAL_VERSION);
        return meta;
    }

    private static void loadFromZip(Path zipFile) {
        String name = zipFile.getFileName().toString().replace(".zip", "");
        Path targetDir = MODELS_DIR.resolve(name);
        try {
            Files.createDirectories(targetDir);
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    Path target = targetDir.resolve(ze.getName());
                    if (ze.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            loadFromFolder(targetDir);
        } catch (IOException e) {
            LOGGER.error("解压 ZIP 模型 {} 失败", zipFile, e);
        }
    }

    public static ModelEntry getModel(String name) {
        return availableModels.get(name);
    }

    public static Collection<String> getModelNames() {
        return availableModels.keySet();
    }

    public static List<ModelEntry> getAllModels() {
        return new ArrayList<>(availableModels.values());
    }

    public static boolean setCurrentModel(String name, ServerCommandSource source) {
        if (!availableModels.containsKey(name)) {
            if (source != null) {
                source.sendMessage(Text.literal("§c未找到模型: " + name));
            }
            return false;
        }
        ModelEntry entry = availableModels.get(name);
        try {
            ONNXInference.loadFromFile(entry.modelPath);
            currentModelName = name;
            if (source != null) {
                source.sendMessage(Text.literal("§a已切换模型至: " + name));
            }
            LOGGER.info("切换模型至: {}", name);
            return true;
        } catch (Exception e) {
            if (source != null) {
                source.sendMessage(Text.literal("§c加载模型失败: " + e.getMessage()));
            }
            LOGGER.error("加载模型 {} 失败", name, e);
            return false;
        }
    }

    public static String getCurrentModelName() {
        return currentModelName;
    }

    public static ModelEntry getCurrentModel() {
        return availableModels.get(currentModelName);
    }
}