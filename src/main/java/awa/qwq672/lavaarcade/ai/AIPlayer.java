package awa.qwq672.lavaarcade.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.entity.Entity;
import java.util.Random;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");

    private final ServerWorld world;
    private final ServerPlayerEntity entity;
    private final AIPersonality personality;
    private final Random random = new Random();
    private AIState currentState = AIState.FOLLOW_PLAYER;
    private LivingEntity target;

    public AIPlayer(ServerWorld world, ServerPlayerEntity entity) {
        this.world = world;
        this.entity = entity;
        this.personality = AIPersonality.random();
        LOGGER.info("AI {} 的性格是：{}", entity.getName().getString(), personality.displayName);
    }

    public void tick() {
        PlayerEntity nearestPlayer = world.getClosestPlayer(entity, 20.0);
        if (nearestPlayer != null) {
            // 计算方向向量：从 AI 眼睛指向玩家眼睛
            Vec3d aiEyes = entity.getEyePos();
            Vec3d playerEyes = nearestPlayer.getEyePos();
            Vec3d direction = playerEyes.subtract(aiEyes).normalize();

            // 转换为偏航角 (yaw) 和俯仰角 (pitch)
            double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(direction.y, horizontalDistance));

            // 应用到 AI 实体
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            // 更新头部旋转（可选，使头部也转向）
            entity.headYaw = yaw;
        }
    }

    public void sendChatMessage(String msg) {
        entity.sendMessage(Text.literal("§7[AI] " + entity.getName().getString() + "§r: " + msg));
    }

    public boolean shouldAcceptRequest(String requestType, String reason) {
        // 简化版：总是拒绝危险请求
        if (requestType.contains("自杀") || requestType.contains("跳岩浆")) {
            return false;
        }
        return true;
    }

    public void executeTask(String task) {
        sendChatMessage("我收到了任务：" + task + "，但还没学会怎么做。");
    }

    public ServerPlayerEntity getEntity() {
        return entity;
    }
}