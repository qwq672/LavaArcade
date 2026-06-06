package awa.qwq672.lavaarcade.ai;

import ai.onnxruntime.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ONNXDecisionModule implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ONNXDecisionModule");
    private final OrtEnvironment env;
    private final OrtSession session;
    private final float actionThreshold;
    private final int inferenceInterval;
    private int tickCounter = 0;
    private float[] lastOutput = new float[4];

    public ONNXDecisionModule(Path modelPath, float threshold, int interval) throws OrtException {
        this.actionThreshold = threshold;
        this.inferenceInterval = interval;
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath.toString());
        LOGGER.info("ONNX 模型加载成功: {}", modelPath);
    }

    public static ONNXDecisionModule createDefault() {
        Path modelPath = ONNXUtils.getModelPath();
        if (modelPath == null) return null;
        try {
            // 阈值和间隔可以从配置文件读取
            return new ONNXDecisionModule(modelPath, 0.5f, 4);
        } catch (OrtException e) {
            LOGGER.error("创建 ONNX 模块失败", e);
            return null;
        }
    }

    public void tick(AIPlayer ai) {
        tickCounter++;
        if (tickCounter < inferenceInterval) {
            applyActions(ai, lastOutput);
            return;
        }
        tickCounter = 0;
        float[] features = extractFeatures(ai);
        float[] output = inference(features);
        System.arraycopy(output, 0, lastOutput, 0, 4);
        applyActions(ai, output);
    }

    private float[] extractFeatures(AIPlayer ai) {
        var player = ai.getEntity();
        var pos = player.getPos();
        var vel = player.getVelocity();
        float yaw = player.getYaw() % 360;
        if (yaw < 0) yaw += 360;
        float pitch = player.getPitch();
        return new float[]{
                player.getHealth() / 20.0f,
                player.getHungerManager().getFoodLevel() / 20.0f,
                (float) (Math.abs(pos.x) % 1000) / 1000.0f,
                (float) (Math.abs(pos.z) % 1000) / 1000.0f,
                (float) (Math.max(0, Math.min(256, pos.y)) / 256.0),
                (float) Math.min(1.0, Math.abs(vel.x) / 10.0),
                (float) Math.min(1.0, Math.abs(vel.z) / 10.0),
                player.isOnGround() ? 1.0f : 0.0f,
                player.isTouchingWater() ? 1.0f : 0.0f,
                yaw / 360.0f,
                (pitch + 90) / 180.0f
        };
    }

    private float[] inference(float[] features) {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), new long[]{1, features.length})) {
            OrtSession.Result result = session.run(Map.of("state", tensor));
            float[][] output = (float[][]) result.get(0).getValue();
            result.close();
            return output[0];
        } catch (OrtException e) {
            LOGGER.error("ONNX 推理失败", e);
            return new float[]{0, 0, 0, 0};
        }
    }

    private void applyActions(AIPlayer ai, float[] output) {
        var player = ai.getEntity();
        // jump
        if (output[0] > actionThreshold && player.isOnGround()) {
            ai.jump();
        }
        // sneak
        player.setSneaking(output[1] > actionThreshold);
        // attack
        if (output[2] > actionThreshold && ai.getAttackCooldown() <= 0) {
            LivingEntity target = findNearestMonster(ai);
            if (target != null) {
                ai.attackEntity(target);
                ai.setAttackCooldown(20);
            }
        }
        // use (预留)
        if (output[3] > actionThreshold) {
            ai.getEntity().swingHand(net.minecraft.util.Hand.MAIN_HAND);
            // 实际使用物品的逻辑可由后续扩展
        }
    }

    private LivingEntity findNearestMonster(AIPlayer ai) {
        var world = ai.getEntity().getWorld();
        Box box = ai.getEntity().getBoundingBox().expand(16);
        List<LivingEntity> monsters = world.getEntitiesByClass(LivingEntity.class, box, e -> e instanceof Monster && e.isAlive());
        if (monsters.isEmpty()) return null;
        LivingEntity nearest = null;
        double nearestDist = 64;
        for (LivingEntity e : monsters) {
            double dist = e.squaredDistanceTo(ai.getEntity());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }

    @Override
    public void close() throws Exception {
        session.close();
        env.close();
    }
}