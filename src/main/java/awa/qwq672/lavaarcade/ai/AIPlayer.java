package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");
    private static final Random RANDOM = new Random();

    private final ServerWorld world;
    private final EntityPlayerMPFake fakePlayer;
    private final AIPersonality personality;
    private final AIState currentState = AIState.FOLLOW_PLAYER;
    private LivingEntity target;

    // 移动控制
    private boolean isMoving = false;
    private static boolean moveEnabled = true;
    private static double followDistance = 3.0;   // 跟随距离，默认3格
    private static final double STOP_DISTANCE_RATIO = 0.5; // 停止距离 = followDistance * 0.5

    private int stuckCounter = 0;      // 卡住计数器
    private Vec3d lastPos = null;      // 上一位置，用于检测是否移动

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

        PlayerEntity nearestPlayer = world.getClosestPlayer(fakePlayer, 20.0);
        if (nearestPlayer == null) {
            if (isMoving) stopMoving();
            return;
        }

        // 转向玩家
        Vec3d direction = nearestPlayer.getPos().subtract(fakePlayer.getPos()).normalize();
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(direction.y, horizontal));
        fakePlayer.setYaw(yaw);
        fakePlayer.setPitch(pitch);
        fakePlayer.headYaw = yaw;

        double dist = fakePlayer.distanceTo(nearestPlayer);
        double stopDistance = followDistance * STOP_DISTANCE_RATIO;

        if (dist > followDistance) {
            if (!isMoving) {
                startMoving();
            }
            // 检测是否卡住
            checkStuck();
        } else if (dist < stopDistance) {
            if (isMoving) {
                stopMoving();
            }
        }

        // 障碍物躲避
        checkObstacles();
    }

    private void startMoving() {
        String name = fakePlayer.getName().getString();
        String command = "/player " + name + " move forward";
        // 使用服务端控制台源执行
        int result = Objects.requireNonNull(fakePlayer.getServer()).getCommandManager().executeWithPrefix(
                fakePlayer.getServer().getCommandSource(),
                command
        );
        isMoving = true;
        LOGGER.info("AI {} 开始移动, 命令结果: {}", name, result);
        lastPos = fakePlayer.getPos();
        stuckCounter = 0;
    }

    private void stopMoving() {
        String name = fakePlayer.getName().getString();
        String command = "/player " + name + " move stop";
        Objects.requireNonNull(fakePlayer.getServer()).getCommandManager().executeWithPrefix(
                fakePlayer.getServer().getCommandSource(),
                command
        );
        isMoving = false;
        LOGGER.info("AI {} 停止移动", name);
    }

    private void checkStuck() {
        if (lastPos == null) {
            lastPos = fakePlayer.getPos();
            return;
        }
        double moved = fakePlayer.getPos().distanceTo(lastPos);
        if (moved < 0.1) {
            stuckCounter++;
            if (stuckCounter > 20) { // 卡住超过1秒（20 tick）
                // 随机转向
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextFloat() * 120 - 60);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                // 尝试跳跃
                String jumpCmd = "/player " + fakePlayer.getName().getString() + " jump";
                Objects.requireNonNull(fakePlayer.getServer()).getCommandManager().executeWithPrefix(
                        fakePlayer.getServer().getCommandSource(),
                        jumpCmd
                );
                stuckCounter = 0;
                LOGGER.info("AI {} 卡住，尝试转向跳跃", fakePlayer.getName().getString());
            }
        } else {
            stuckCounter = 0;
        }
        lastPos = fakePlayer.getPos();
    }

    private void checkObstacles() {
        // 简单实现：检测前方2格内是否有固体方块或危险方块
        Vec3d pos = fakePlayer.getPos();
        Vec3d forward = new Vec3d(Math.sin(Math.toRadians(fakePlayer.getYaw())), 0, Math.cos(Math.toRadians(fakePlayer.getYaw())));
        for (double d = 1; d <= 2; d += 0.5) {
            Vec3d checkPos = pos.add(forward.multiply(d)).add(0, 0.5, 0);
            var blockState = world.getBlockState(new net.minecraft.util.math.BlockPos((int)checkPos.x, (int)checkPos.y, (int)checkPos.z));
            if (blockState.isSolid() || blockState.getBlock() == net.minecraft.block.Blocks.LAVA ||
                    blockState.getBlock() == net.minecraft.block.Blocks.FIRE ||
                    blockState.getBlock() == net.minecraft.block.Blocks.MAGMA_BLOCK ||
                    blockState.getBlock() == net.minecraft.block.Blocks.CACTUS) {
                // 随机左转或右转30度
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextBoolean() ? 30 : -30);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                LOGGER.info("AI {} 避开障碍物", fakePlayer.getName().getString());
                break;
            }
        }
    }

    public void sendChatMessage(String msg) {
        fakePlayer.sendMessage(Text.literal("§7[AI] " + fakePlayer.getName().getString() + "§r: " + msg));
    }

    public boolean shouldAcceptRequest(String requestType, String reason) {
        if (requestType.contains("自杀") || requestType.contains("跳岩浆")) return false;
        // 根据性格决定
        if (personality == AIPersonality.FUNNY) {
            return RANDOM.nextBoolean();
        }
        return true;
    }

    public void executeTask(String task) {
        // 简单回复
        String response = "§7[AI] " + fakePlayer.getName().getString() + "§r: ";
        if (personality == AIPersonality.FUNNY) {
            response += "哈哈，" + task + "？我试试看！";
        } else if (personality == AIPersonality.SERIOUS) {
            response += "收到任务：" + task + "，正在处理。";
        } else {
            response += "好的，我会尝试完成：" + task;
        }
        fakePlayer.sendMessage(Text.literal(response));
    }

    public EntityPlayerMPFake getEntity() {
        return fakePlayer;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }
}