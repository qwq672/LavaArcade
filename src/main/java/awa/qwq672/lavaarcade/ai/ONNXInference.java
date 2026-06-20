package awa.qwq672.lavaarcade.ai;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

public class ONNXInference {
    private static final Logger LOGGER = LoggerFactory.getLogger("ONNXInference");
    private static final int INPUT_SIZE = 28;
    private static final int OUTPUT_SIZE = 8;
    private static OrtEnvironment env;
    private static OrtSession session;
    private static boolean loaded = false;

    public static void loadFromResources(String resourcePath) {
        try (InputStream is = ONNXInference.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.warn("未找到内置模型: {}", resourcePath);
                return;
            }
            env = OrtEnvironment.getEnvironment();
            byte[] modelBytes = is.readAllBytes();
            session = env.createSession(modelBytes);
            loaded = true;
            LOGGER.info("ONNX 模型从资源加载成功: {}", resourcePath);
        } catch (Exception e) {
            LOGGER.error("加载 ONNX 模型失败", e);
        }
    }

    public static void loadFromFile(Path modelPath) throws Exception {
        if (session != null) session.close();
        if (env == null) env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString());
        loaded = true;
        LOGGER.info("ONNX 模型从文件加载成功: {}", modelPath);
    }

    public static float[] predict(float[] state) {
        if (!loaded) {
            LOGGER.warn("模型未加载");
            return null;
        }
        if (state.length != INPUT_SIZE) {
            LOGGER.error("输入维度错误: 需要 {}, 实际 {}", INPUT_SIZE, state.length);
            return null;
        }
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), new long[]{1, INPUT_SIZE})) {
            OrtSession.Result result = session.run(Collections.singletonMap("state", inputTensor));
            float[][] output = (float[][]) result.get("action").get().getValue();
            result.close();
            return output[0];
        } catch (OrtException e) {
            LOGGER.error("ONNX 推理失败", e);
            return null;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void close() throws Exception {
        if (session != null) session.close();
        if (env != null) env.close();
        loaded = false;
    }
}