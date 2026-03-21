package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");

    private final ServerWorld world;
    private final EntityPlayerMPFake fakePlayer;
    private final AIPersonality personality;
    private final Random random = new Random();
    private AIState currentState = AIState.FOLLOW_PLAYER;
    private LivingEntity target;

    // 移动参数
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double STOP_DISTANCE = 1.5;
    private static final double MOVE_SPEED = 0.3;

    public AIPlayer(ServerWorld world, EntityPlayerMPFake fakePlayer) {
        this.world = world;
        this.fakePlayer = fakePlayer;
        this.personality = AIPersonality.random();
        LOGGER.info("AI {} 的性格是：{}", fakePlayer.getName().getString(), personality.displayName);
    }

    public void tick() {
        PlayerEntity nearestPlayer = world.getClosestPlayer(fakePlayer, 20.0);
        if (nearestPlayer != null) {
            // 1. 转向玩家
            Vec3d direction = nearestPlayer.getPos().subtract(fakePlayer.getPos()).normalize();
            double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(direction.y, horizontal));
            fakePlayer.setYaw(yaw);
            fakePlayer.setPitch(pitch);
            fakePlayer.headYaw = yaw;

            // 2. 移动逻辑
            double dist = fakePlayer.distanceTo(nearestPlayer);
            if (dist > FOLLOW_DISTANCE) {
                Vec3d move = nearestPlayer.getPos().subtract(fakePlayer.getPos()).normalize();
                fakePlayer.setVelocity(move.x * MOVE_SPEED, fakePlayer.getVelocity().y, move.z * MOVE_SPEED);
            } else if (dist < STOP_DISTANCE) {
                fakePlayer.setVelocity(0, fakePlayer.getVelocity().y, 0);
            }
        } else {
            // 没有玩家时，缓慢停止
            fakePlayer.setVelocity(fakePlayer.getVelocity().multiply(0.8));
        }
    }

    public void sendChatMessage(String msg) {
        fakePlayer.sendMessage(Text.literal("§7[AI] " + fakePlayer.getName().getString() + "§r: " + msg));
    }

    public boolean shouldAcceptRequest(String requestType, String reason) {
        if (requestType.contains("自杀") || requestType.contains("跳岩浆")) {
            return false;
        }
        return true;
    }

    public void executeTask(String task) {
        Text message = Text.literal("§7[AI] " + fakePlayer.getName().getString() + "§r: ")
                .append(Text.literal("我收到了任务："))
                .append(Text.literal(task))
                .append(Text.literal("，但还没学会怎么做。"));
        fakePlayer.sendMessage(message);
    }

    public EntityPlayerMPFake getEntity() {
        return fakePlayer;
    }
}