package awa.qwq672.lavaarcade.ai;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");
    private static final Random RANDOM = new Random();

    private final ServerWorld world;
    private final EntityPlayerMPFake fakePlayer;
    private final AIPersonality personality;

    private AIBehavior behavior = AIBehavior.FOLLOW;
    private static boolean moveEnabled = true;
    private static double followDistance = 3.0;
    private static final double STOP_DISTANCE_RATIO = 0.5;
    private static final double MIN_DISTANCE = 1.5;

    private static boolean allowTools = false;
    private static boolean allowToolBlocks = false;

    private boolean isMoving = false;
    private int stuckCounter = 0;
    private Vec3d lastPos = null;
    private int exploreDirectionChangeCooldown = 0;
    private int attackCooldown = 0;

    // 自主决策相关
    private enum FriendlyTask {
        ATTACK_MONSTER, MINE_ORE, FOLLOW_PLAYER, EXPLORE
    }
    private FriendlyTask currentTask = FriendlyTask.EXPLORE;
    private int taskCooldown = 0;
    private int taskDuration = 0;
    private boolean isAttacking = false;

    public AIPlayer(ServerWorld world, EntityPlayerMPFake fakePlayer) {
        this.world = world;
        this.fakePlayer = fakePlayer;
        this.personality = AIPersonality.random();
        LOGGER.info("AI {} 性格: {}", fakePlayer.getName().getString(), personality.displayName);
    }

    public static void setMoveEnabled(boolean enabled) {
        moveEnabled = enabled;
        LOGGER.info("AI 移动开关: {}", enabled);
    }

    public static void setFollowDistance(double distance) {
        followDistance = Math.max(1.0, Math.min(20.0, distance));
        LOGGER.info("AI 跟随距离设置为: {}", followDistance);
    }

    public static void setAllowTools(boolean enable) { allowTools = enable; }
    public static void setAllowToolBlocks(boolean enable) { allowToolBlocks = enable; }
    public static boolean isAllowTools() { return allowTools; }
    public static boolean isAllowToolBlocks() { return allowToolBlocks; }

    public void setBehavior(AIBehavior newBehavior) {
        this.behavior = newBehavior;
        if (newBehavior == AIBehavior.IDLE && isMoving) {
            stopMoving();
        }
        LOGGER.info("AI {} 行为模式切换为: {}", fakePlayer.getName().getString(), newBehavior);
    }

    public AIBehavior getBehavior() {
        return behavior;
    }

    public EntityPlayerMPFake getEntity() {
        return fakePlayer;
    }

    public void onAttacked(LivingEntity attacker) {
        if (attacker == null) return;
        if (attackCooldown > 0) return;
        if (RANDOM.nextDouble() < 0.5) {
            attackEntity(attacker);
            attackCooldown = 20;
        }
    }

    private void attackEntity(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        isAttacking = true;
        equipBestWeapon();
        lookAt(target.getPos());
        fakePlayer.swingHand(Hand.MAIN_HAND);  // 添加手臂摆动
        fakePlayer.attack(target);
        isAttacking = false;
        LOGGER.info("AI {} 攻击了 {}", fakePlayer.getName().getString(), target.getName().getString());
    }

    private void equipBestWeapon() {
        if (!allowTools) return;
        PlayerInventory inv = fakePlayer.getInventory();
        int bestSlot = -1;
        double bestDamage = 0;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack stack = inv.main.get(i);
            if (stack.getItem() instanceof SwordItem) {
                double damage = ((SwordItem) stack.getItem()).getAttackDamage();
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot != -1 && bestSlot != inv.selectedSlot) {
            inv.selectedSlot = bestSlot;
        }
    }

    private void equipBestPickaxeForBlock(Block targetBlock) {
        if (!allowTools) return;
        ToolMaterial required = getRequiredToolLevel(targetBlock);
        if (required == null) return;
        PlayerInventory inv = fakePlayer.getInventory();
        int bestSlot = -1;
        int bestLevel = -1;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack stack = inv.main.get(i);
            if (stack.getItem() instanceof PickaxeItem) {
                PickaxeItem pick = (PickaxeItem) stack.getItem();
                int level = pick.getMaterial().getMiningLevel();
                if (level >= required.getMiningLevel() && level > bestLevel) {
                    bestLevel = level;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot != -1 && bestSlot != inv.selectedSlot) {
            inv.selectedSlot = bestSlot;
        }
    }

    private ToolMaterial getRequiredToolLevel(Block block) {
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return ToolMaterials.WOOD;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.COPPER_ORE) return ToolMaterials.STONE;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return ToolMaterials.IRON;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return ToolMaterials.IRON;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return ToolMaterials.IRON;
        if (block == Blocks.ANCIENT_DEBRIS) return ToolMaterials.DIAMOND;
        return null;
    }

    private void attackNearbyMonster() {
        if (attackCooldown > 0) return;
        LivingEntity target = findNearestHostile();
        if (target != null) {
            attackEntity(target);
            attackCooldown = 20;
        }
    }

    private LivingEntity findNearestHostile() {
        Box box = fakePlayer.getBoundingBox().expand(16);
        List<LivingEntity> monsters = world.getEntitiesByClass(LivingEntity.class, box, e -> e instanceof Monster && e.isAlive());
        if (monsters.isEmpty()) return null;
        LivingEntity nearest = null;
        double nearestDist = 64;
        for (LivingEntity e : monsters) {
            double dist = e.squaredDistanceTo(fakePlayer);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }

    private BlockPos findNearbyOre() {
        BlockPos center = fakePlayer.getBlockPos();
        int radius = 10;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Block block = world.getBlockState(pos).getBlock();
                    if (getRequiredToolLevel(block) != null) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void mineNearbyOre() {
        BlockPos orePos = findNearbyOre();
        if (orePos == null) return;
        equipBestPickaxeForBlock(world.getBlockState(orePos).getBlock());
        lookAt(Vec3d.ofCenter(orePos));
        ((ServerPlayerInterface) fakePlayer).getActionPack().start(ActionType.ATTACK, Action.continuous());
        // 模拟挖掘（实际需要监听方块破坏，这里简化）
        fakePlayer.getServer().execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            ((ServerPlayerInterface) fakePlayer).getActionPack().stopAll();
        });
        LOGGER.info("AI {} 开始挖掘 {}", fakePlayer.getName().getString(), orePos);
    }

    public void tick() {
        if (!moveEnabled) return;
        if (attackCooldown > 0) attackCooldown--;

        switch (behavior) {
            case IDLE:
                if (isMoving) stopMoving();
                return;
            case FOLLOW:
                followNearestPlayer();
                break;
            case EXPLORE:
                explore();
                break;
            case FRIENDLY:
                friendlyTick();
                break;
        }
    }

    private void friendlyTick() {
        if (taskCooldown <= 0) {
            decideTask();
            taskCooldown = 40; // 2秒重新决策
        } else {
            taskCooldown--;
        }
        taskDuration++;
        if (taskDuration > 200) { // 任务最长10秒
            taskCooldown = 0;
            taskDuration = 0;
        }

        switch (currentTask) {
            case ATTACK_MONSTER:
                attackNearbyMonster();
                break;
            case MINE_ORE:
                mineNearbyOre();
                break;
            case FOLLOW_PLAYER:
                followNearestPlayer();
                break;
            case EXPLORE:
                explore();
                break;
        }
    }

    private void decideTask() {
        // 优先攻击怪物
        if (findNearestHostile() != null) {
            currentTask = FriendlyTask.ATTACK_MONSTER;
            taskDuration = 0;
            return;
        }
        // 其次挖掘矿石（如果有工具且允许）
        if (allowTools && findNearbyOre() != null) {
            currentTask = FriendlyTask.MINE_ORE;
            taskDuration = 0;
            return;
        }
        // 随机决定
        if (RANDOM.nextDouble() < 0.3 && findNearestRealPlayer() != null) {
            currentTask = FriendlyTask.FOLLOW_PLAYER;
        } else {
            currentTask = FriendlyTask.EXPLORE;
        }
        taskDuration = 0;
    }

    private void followNearestPlayer() {
        PlayerEntity nearestPlayer = findNearestRealPlayer();
        if (nearestPlayer == null) {
            if (isMoving) stopMoving();
            return;
        }

        lookAt(nearestPlayer.getPos());

        double dist = fakePlayer.distanceTo(nearestPlayer);
        double stopDistance = followDistance * STOP_DISTANCE_RATIO;

        if (dist > followDistance) {
            avoidObstaclesAndJump();
            if (!isMoving) startMoving();
            checkStuck();
            updateSprint(nearestPlayer, dist);
        } else if (dist < MIN_DISTANCE) {
            if (isMoving) stopMoving();
            if (dist < 1.0) {
                EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
                actionPack.setForward(-1);
                fakePlayer.getServer().execute(() -> {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    if (behavior == AIBehavior.FRIENDLY) actionPack.setForward(0);
                });
            }
        } else if (dist < stopDistance) {
            if (isMoving) stopMoving();
        }
    }

    private void explore() {
        if (exploreDirectionChangeCooldown <= 0) {
            float newYaw = fakePlayer.getYaw() + (RANDOM.nextFloat() * 180 - 90);
            fakePlayer.setYaw(newYaw);
            fakePlayer.headYaw = newYaw;
            exploreDirectionChangeCooldown = 40;
        } else {
            exploreDirectionChangeCooldown--;
        }

        avoidObstaclesAndJump();
        if (!isMoving) startMoving();
        checkStuck();
        setSprinting(false);
    }

    private void startMoving() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.setForward(1);
        actionPack.setStrafing(0);
        isMoving = true;
        lastPos = fakePlayer.getPos();
        stuckCounter = 0;
    }

    private void stopMoving() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.stopMovement();
        isMoving = false;
    }

    private void setSprinting(boolean sprint) {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.setSprinting(sprint);
    }

    private void updateSprint(PlayerEntity target, double distance) {
        boolean shouldSprint = distance > 6 && !isDangerAhead(3);
        setSprinting(shouldSprint);
    }

    private boolean isDangerAhead(int steps) {
        Vec3d pos = fakePlayer.getPos();
        float yaw = fakePlayer.getYaw();
        Vec3d forward = new Vec3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        for (double d = 1; d <= steps; d += 0.5) {
            Vec3d checkPos = pos.add(forward.multiply(d)).add(0, 0.5, 0);
            BlockState state = world.getBlockState(new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z));
            if (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.MAGMA_BLOCK ||
                    state.getBlock() == Blocks.FIRE || state.getBlock() == Blocks.CACTUS) {
                return true;
            }
        }
        return false;
    }

    private void avoidObstaclesAndJump() {
        Vec3d pos = fakePlayer.getPos();
        float yaw = fakePlayer.getYaw();
        Vec3d forward = new Vec3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));

        // 检测前方其他假人
        double distToEntity = 1.2;
        Vec3d checkEntityPos = pos.add(forward.multiply(distToEntity));
        Box checkBox = new Box(checkEntityPos.x - 0.5, checkEntityPos.y, checkEntityPos.z - 0.5,
                checkEntityPos.x + 0.5, checkEntityPos.y + 1.8, checkEntityPos.z + 0.5);
        List<Entity> entities = world.getOtherEntities(fakePlayer, checkBox, e -> e instanceof EntityPlayerMPFake);
        if (!entities.isEmpty()) {
            BlockPos feetPos = new BlockPos((int) checkEntityPos.x, (int) checkEntityPos.y, (int) checkEntityPos.z);
            if (world.getBlockState(feetPos).isAir() && world.getBlockState(feetPos.up()).isAir()) {
                jump();
            } else {
                float newYaw = yaw + (RANDOM.nextBoolean() ? 45 : -45);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
            }
            return;
        }

        // 检测前方固体/危险方块
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            Vec3d checkPos = pos.add(forward.multiply(d)).add(0, 0.5, 0);
            BlockPos bp = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);
            BlockState state = world.getBlockState(bp);
            if (state.isSolid() || state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.MAGMA_BLOCK) {
                if (d <= 1.5 && world.getBlockState(bp.up()).isAir()) {
                    jump();
                    return;
                }
                float newYaw = yaw + (RANDOM.nextBoolean() ? 45 : -45);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                return;
            }
        }

        // 检测高墙
        for (double d = 0.5; d <= 1.5; d += 0.5) {
            Vec3d checkPos = pos.add(forward.multiply(d)).add(0, 1.5, 0);
            BlockPos bp = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);
            BlockState state = world.getBlockState(bp);
            if (state.isSolid()) {
                float newYaw = yaw + (RANDOM.nextBoolean() ? 45 : -45);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                return;
            }
        }
    }

    private void checkStuck() {
        if (lastPos == null) {
            lastPos = fakePlayer.getPos();
            return;
        }
        double moved = fakePlayer.getPos().distanceTo(lastPos);
        if (moved < 0.05) {
            stuckCounter++;
            if (stuckCounter > 10) {
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextBoolean() ? 30 : -30);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
            }
            if (stuckCounter > 20) {
                jump();
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextFloat() * 120 - 60);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                stuckCounter = 0;
                LOGGER.info("AI {} 严重卡住，跳跃并转向", fakePlayer.getName().getString());
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

    private void lookAt(Vec3d target) {
        Vec3d toTarget = target.subtract(fakePlayer.getPos());
        double dx = toTarget.x, dz = toTarget.z;
        double dy = toTarget.y;
        double horizontal = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        fakePlayer.setYaw(yaw);
        fakePlayer.setPitch(pitch);
        fakePlayer.headYaw = yaw;
        fakePlayer.bodyYaw = yaw;
    }

    private PlayerEntity findNearestRealPlayer() {
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerEntity player : world.getPlayers()) {
            if (player == fakePlayer) continue;
            if (player instanceof EntityPlayerMPFake) continue;
            double d = player.distanceTo(fakePlayer);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = player;
            }
        }
        return nearest;
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
}