package awa.qwq672.lavaarcade.ai;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ONNXUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("ONNXUtils");
    private static final String MODEL_PATH_IN_JAR = "/assets/lavaarcade/models/default.onnx";
    private static Path cachedModelPath = null;

    /**
     * 获取 ONNX 模型文件的路径（如果 jar 内存在则解压到临时目录）
     */
    public static synchronized Path getModelPath() {
        if (cachedModelPath != null && Files.exists(cachedModelPath)) {
            return cachedModelPath;
        }
        try (InputStream is = ONNXUtils.class.getResourceAsStream(MODEL_PATH_IN_JAR)) {
            if (is == null) {
                LOGGER.warn("未在 jar 内找到默认模型: {}", MODEL_PATH_IN_JAR);
                return null;
            }
            // 解压到临时目录
            Path tempDir = FabricLoader.getInstance().getGameDir().resolve("lavaarcache");
            Files.createDirectories(tempDir);
            cachedModelPath = tempDir.resolve("default.onnx");
            Files.copy(is, cachedModelPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("ONNX 模型已解压到: {}", cachedModelPath);
            return cachedModelPath;
        } catch (Exception e) {
            LOGGER.error("无法加载 ONNX 模型", e);
            return null;
        }
    }
}