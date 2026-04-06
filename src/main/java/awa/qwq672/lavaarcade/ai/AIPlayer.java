package awa.qwq672.lavaarcade.ai;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");
    private static final Random RANDOM = new Random();

    private final ServerWorld world;
    private final EntityPlayerMPFake fakePlayer;
    private final AIPersonality personality;

    private static boolean moveEnabled = true;
    private static double followDistance = 3.0;
    private static final double STOP_DISTANCE_RATIO = 0.5;

    private boolean isMoving = false;
    private int stuckCounter = 0;
    private Vec3d lastPos = null;

    public static void setMoveEnabled(boolean enabled) {
        moveEnabled = enabled;
        LOGGER.info("AI 移动开关: {}", enabled);
    }

    public static void setFollowDistance(double distance) {
        followDistance = Math.max(1.0, Math.min(20.0, distance));
        LOGGER.info("AI 跟随距离设置为: {}", followDistance);
    }

    public AIPlayer(ServerWorld world, EntityPlayerMPFake fakePlayer) {
        this.world = world;
        this.fakePlayer = fakePlayer;
        this.personality = AIPersonality.random();
        LOGGER.info("AI {} 性格: {}", fakePlayer.getName().getString(), personality.displayName);
    }

    public void tick() {
        if (!moveEnabled) return;

        // 寻找最近的真实玩家（忽略其他假人）
        PlayerEntity nearestPlayer = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerEntity player : world.getPlayers()) {
            if (player == fakePlayer) continue;
            // 可选：排除其他假人，只跟踪真实玩家
            if (player instanceof EntityPlayerMPFake) continue;
            double d = player.distanceTo(fakePlayer);
            if (d < nearestDist) {
                nearestDist = d;
                nearestPlayer = player;
            }
        }
        if (nearestPlayer == null) {
            if (isMoving) stopMoving();
            return;
        }

        // 转向玩家
        Vec3d direction = nearestPlayer.getPos().subtract(fakePlayer.getPos()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        fakePlayer.setYaw(yaw);
        fakePlayer.headYaw = yaw;

        double dist = fakePlayer.distanceTo(nearestPlayer);
        double stopDistance = followDistance * STOP_DISTANCE_RATIO;

        if (dist > followDistance) {
            if (!isMoving) startMoving();
            checkStuck();
        } else if (dist < stopDistance) {
            if (isMoving) stopMoving();
        }
    }

    private void startMoving() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.setForward(1);      // 向前移动
        actionPack.setStrafing(0);     // 不左右移动
        isMoving = true;
        LOGGER.info("AI {} 开始移动", fakePlayer.getName().getString());
        lastPos = fakePlayer.getPos();
        stuckCounter = 0;
    }

    private void stopMoving() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.stopMovement();
        isMoving = false;
        LOGGER.info("AI {} 停止移动", fakePlayer.getName().getString());
    }

    private void checkStuck() {
        if (lastPos == null) {
            lastPos = fakePlayer.getPos();
            return;
        }
        double moved = fakePlayer.getPos().distanceTo(lastPos);
        if (moved < 0.05) {   // 几乎没动，可能被卡住
            stuckCounter++;
            if (stuckCounter > 20) { // 卡住超过1秒（20 tick）
                jump();
                // 随机转向
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextFloat() * 120 - 60);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                stuckCounter = 0;
                LOGGER.info("AI {} 卡住，尝试跳跃并转向", fakePlayer.getName().getString());
            }
        } else {
            stuckCounter = 0;
        }
        lastPos = fakePlayer.getPos();
    }

    private void jump() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.start(ActionType.JUMP, Action.once());
    }

    public void sendChatMessage(String msg) {
        fakePlayer.sendMessage(Text.literal("§7[AI] " + fakePlayer.getName().getString() + "§r: " + msg));
    }

    public boolean shouldAcceptRequest(String requestType, String reason) {
        if (requestType.contains("自杀") || requestType.contains("跳岩浆")) return false;
        if (personality == AIPersonality.FUNNY) return RANDOM.nextBoolean();
        return true;
    }

    public void executeTask(String task) {
        String response = "§7[AI] " + fakePlayer.getName().getString() + "§r: ";
        if (personality == AIPersonality.FUNNY) response += "哈哈，" + task + "？我试试看！";
        else if (personality == AIPersonality.SERIOUS) response += "收到任务：" + task + "，正在处理。";
        else response += "好的，我会尝试完成：" + task;
        fakePlayer.sendMessage(Text.literal(response));
    }

    public EntityPlayerMPFake getEntity() {
        return fakePlayer;
    }
}