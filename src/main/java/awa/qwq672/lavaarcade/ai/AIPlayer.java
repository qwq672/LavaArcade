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
import net.minecraft.item.ArmorItem;
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

import java.util.*;
import java.util.stream.Collectors;

public class AIPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIPlayer");
    private static final Random RANDOM = new Random();
    private static final double ACTION_DISTANCE = 3.0; // 3格内才能动作

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
    private int equipCooldown = 0;
    private UUID followTargetId = null;
    private int followRetryCooldown = 0;

    // 挖掘专用字段
    private int miningCooldown = 0;
    private BlockPos targetMiningPos = null;
    private int miningProgress = 0;

    // ONNX 模块
    private ONNXDecisionModule onnxModule;
    private static boolean enableONNX = false;

    // 自主决策相关
    private enum FriendlyTask {
        ATTACK_MONSTER, MINE_ORE, FOLLOW_PLAYER, EXPLORE
    }
    private FriendlyTask currentTask = FriendlyTask.EXPLORE;
    private int taskCooldown = 0;
    private int taskDuration = 0;
    private boolean isAttacking = false;

    // ==================== 构造与静态方法 ====================
    public AIPlayer(ServerWorld world, EntityPlayerMPFake fakePlayer) {
        this.world = world;
        this.fakePlayer = fakePlayer;
        this.personality = AIPersonality.random();
        LOGGER.info("AI {} 性格: {}", fakePlayer.getName().getString(), personality.displayName);

        if (enableONNX) {
            try {
                this.onnxModule = ONNXDecisionModule.createDefault();
                if (this.onnxModule != null) {
                    LOGGER.info("AI {} 已加载 ONNX 模块", fakePlayer.getName().getString());
                }
            } catch (Exception e) {
                LOGGER.error("AI {} 加载 ONNX 模块失败", fakePlayer.getName().getString(), e);
            }
        }
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

    public static void setEnableONNX(boolean enable) { enableONNX = enable; }
    public static boolean isEnableONNX() { return enableONNX; }

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

    public AIPersonality getPersonality() { return personality; }

    public int getAttackCooldown() { return attackCooldown; }
    public void setAttackCooldown(int cooldown) { attackCooldown = cooldown; }

    public void jump() {
        EntityPlayerActionPack actionPack = ((ServerPlayerInterface) fakePlayer).getActionPack();
        actionPack.start(ActionType.JUMP, Action.once());
    }

    public void attackEntity(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        double distSq = fakePlayer.squaredDistanceTo(target);
        if (distSq > ACTION_DISTANCE * ACTION_DISTANCE) {
            lookAt(target.getPos());
            if (!isMoving) startMoving();
            setSprinting(true);
            return;
        }
        if (isMoving) stopMoving();
        setSprinting(false);
        isAttacking = true;
        equipBestWeapon();
        lookAt(target.getPos());
        fakePlayer.swingHand(Hand.MAIN_HAND);
        fakePlayer.attack(target);
        isAttacking = false;
        LOGGER.info("AI {} 攻击了 {}", fakePlayer.getName().getString(), target.getName().getString());
        if (RANDOM.nextDouble() < 0.3) {
            SpeechManager.say(this, "combat.attack", Collections.emptyMap());
        }
    }

    public void onAttacked(LivingEntity attacker) {
        if (attacker == null) return;
        if (attackCooldown > 0) return;
        if (RANDOM.nextDouble() < 0.5) {
            attackEntity(attacker);
            attackCooldown = 20;
        }
        if (RANDOM.nextDouble() < 0.5) {
            SpeechManager.say(this, "combat.hurt", Collections.emptyMap());
        }
    }

    // ---------------------- 装备系统 ----------------------
    private float getArmorScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem)) return 0;
        ArmorItem armor = (ArmorItem) stack.getItem();
        return armor.getProtection() + armor.getToughness();
    }

    private void equipBestArmor() {
        PlayerInventory inv = fakePlayer.getInventory();
        for (int slotIndex = 0; slotIndex < 4; slotIndex++) {
            ItemStack current = inv.armor.get(slotIndex);
            float bestScore = getArmorScore(current);
            int bestSlot = -1;
            for (int i = 0; i < inv.main.size(); i++) {
                ItemStack stack = inv.main.get(i);
                if (stack.getItem() instanceof ArmorItem) {
                    ArmorItem armor = (ArmorItem) stack.getItem();
                    int targetSlot = -1;
                    switch (armor.getSlotType()) {
                        case FEET: targetSlot = 0; break;
                        case LEGS: targetSlot = 1; break;
                        case CHEST: targetSlot = 2; break;
                        case HEAD: targetSlot = 3; break;
                        default: break;
                    }
                    if (targetSlot == slotIndex) {
                        float score = getArmorScore(stack);
                        if (score > bestScore) {
                            bestScore = score;
                            bestSlot = i;
                        }
                    }
                }
            }
            if (bestSlot != -1) {
                ItemStack bestStack = inv.main.get(bestSlot);
                inv.main.set(bestSlot, current);
                inv.armor.set(slotIndex, bestStack);
            }
        }
    }

    // ---------------------- 战斗辅助 ----------------------
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

    // ---------------------- 挖掘 ----------------------
    private boolean hasLineOfSight(BlockPos target) {
        Vec3d start = fakePlayer.getEyePos();
        Vec3d end = Vec3d.ofCenter(target);
        int dx = (int) Math.signum(end.x - start.x);
        int dy = (int) Math.signum(end.y - start.y);
        int dz = (int) Math.signum(end.z - start.z);
        double steps = Math.max(Math.abs(end.x - start.x), Math.max(Math.abs(end.y - start.y), Math.abs(end.z - start.z)));
        for (int i = 1; i < steps; i++) {
            double x = start.x + dx * i / steps;
            double y = start.y + dy * i / steps;
            double z = start.z + dz * i / steps;
            BlockPos block = new BlockPos((int) x, (int) y, (int) z);
            if (block.equals(target)) continue;
            BlockState state = world.getBlockState(block);
            if (state.isSolid() && !state.isAir()) {
                return false;
            }
        }
        return true;
    }

    private BlockPos findNearbyOre() {
        BlockPos center = fakePlayer.getBlockPos();
        int radius = 6;
        BlockPos closest = null;
        double closestDist = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Block block = world.getBlockState(pos).getBlock();
                    if (getRequiredToolLevel(block) != null) {
                        if (hasLineOfSight(pos)) {
                            double distSq = dx*dx + dy*dy + dz*dz;
                            if (distSq < closestDist) {
                                closestDist = distSq;
                                closest = pos;
                            }
                        }
                    }
                }
            }
        }
        return closest;
    }

    private void mineNearbyOre() {
        if (miningCooldown > 0) {
            miningCooldown--;
            return;
        }
        if (targetMiningPos == null) {
            targetMiningPos = findNearbyOre();
            if (targetMiningPos == null) return;
            miningProgress = 0;
            equipBestPickaxeForBlock(world.getBlockState(targetMiningPos).getBlock());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("ore", world.getBlockState(targetMiningPos).getBlock().getName().getString());
            SpeechManager.say(this, "mining.start", placeholders);
        }
        double distSq = fakePlayer.getPos().squaredDistanceTo(Vec3d.ofCenter(targetMiningPos));
        if (distSq > ACTION_DISTANCE * ACTION_DISTANCE) {
            lookAt(Vec3d.ofCenter(targetMiningPos));
            if (!isMoving) startMoving();
            setSprinting(true);
            return;
        }
        if (isMoving) stopMoving();
        setSprinting(false);
        lookAt(Vec3d.ofCenter(targetMiningPos));
        miningProgress++;
        if (miningProgress >= 10) {
            fakePlayer.interactionManager.tryBreakBlock(targetMiningPos);
            fakePlayer.getServer().execute(() -> {
                if (world.getBlockState(targetMiningPos).isAir()) {
                    targetMiningPos = null;
                    miningCooldown = 60;
                    SpeechManager.say(this, "mining.finish", Collections.emptyMap());
                } else {
                    miningProgress = 0;
                }
            });
        }
    }

    // ---------------------- 主 Tick ----------------------
    public void tick() {
        if (!moveEnabled) return;
        if (attackCooldown > 0) attackCooldown--;
        if (equipCooldown <= 0) {
            equipBestArmor();
            equipCooldown = 100;
        } else {
            equipCooldown--;
        }

        // 行为模式移动控制
        switch (behavior) {
            case IDLE:
                if (isMoving) stopMoving();
                break;
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

        // ONNX 附加动作决策（不影响移动）
        if (enableONNX && onnxModule != null) {
            onnxModule.tick(this);
        }
    }

    private void friendlyTick() {
        if (taskCooldown <= 0) {
            decideTask();
            taskCooldown = 40;
        } else {
            taskCooldown--;
        }
        taskDuration++;
        if (taskDuration > 200) {
            taskCooldown = 0;
            taskDuration = 0;
        }

        switch (currentTask) {
            case ATTACK_MONSTER:
                attackNearbyMonster();
                break;
            case MINE_ORE:
                mineNearbyOre();
                if (targetMiningPos == null) taskCooldown = 0;
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
        if (findNearestHostile() != null) {
            currentTask = FriendlyTask.ATTACK_MONSTER;
            taskDuration = 0;
            return;
        }
        if (allowTools && findNearbyOre() != null) {
            currentTask = FriendlyTask.MINE_ORE;
            taskDuration = 0;
            return;
        }
        if (RANDOM.nextDouble() < 0.3 && findNearestRealPlayer() != null) {
            currentTask = FriendlyTask.FOLLOW_PLAYER;
        } else {
            currentTask = FriendlyTask.EXPLORE;
        }
        taskDuration = 0;
    }

    private void followNearestPlayer() {
        if (followRetryCooldown > 0) followRetryCooldown--;
        PlayerEntity target = null;
        if (followTargetId != null) {
            target = world.getPlayerByUuid(followTargetId);
            if (target == null || target.isSpectator() || target.isRemoved()) followTargetId = null;
        }
        if (followTargetId == null && followRetryCooldown == 0) {
            List<PlayerEntity> validPlayers = world.getPlayers().stream()
                    .filter(p -> p != fakePlayer && !(p instanceof EntityPlayerMPFake) && !p.isSpectator())
                    .collect(Collectors.toList());
            if (!validPlayers.isEmpty()) {
                if (RANDOM.nextDouble() < 0.2) {
                    followTargetId = null;
                } else {
                    followTargetId = validPlayers.get(RANDOM.nextInt(validPlayers.size())).getUuid();
                }
                followRetryCooldown = 200;
            } else {
                followTargetId = null;
            }
        }
        if (followTargetId != null) target = world.getPlayerByUuid(followTargetId);
        if (target == null) {
            if (isMoving) stopMoving();
            return;
        }

        lookAt(target.getPos());
        double dist = fakePlayer.distanceTo(target);
        double stopDistance = followDistance * STOP_DISTANCE_RATIO;

        if (dist > followDistance) {
            avoidObstaclesAndJump();
            if (!isMoving) startMoving();
            checkStuck();
            updateSprint(target, dist);
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
        Vec3d[] directions = {forward, rotate(forward, 45), rotate(forward, -45)};
        for (Vec3d dir : directions) {
            for (double d = 0.5; d <= 1.5; d += 0.5) {
                Vec3d checkPos = pos.add(dir.multiply(d)).add(0, 0.5, 0);
                BlockPos bp = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);
                BlockState state = world.getBlockState(bp);
                if (state.isSolid() || state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.MAGMA_BLOCK) {
                    if (d <= 1.0 && world.getBlockState(bp.up()).isAir()) {
                        jump();
                        return;
                    }
                    float newYaw = yaw + (RANDOM.nextBoolean() ? 45 : -45);
                    fakePlayer.setYaw(newYaw);
                    fakePlayer.headYaw = newYaw;
                    return;
                }
            }
        }
    }

    private Vec3d rotate(Vec3d vec, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vec.x * cos - vec.z * sin;
        double z = vec.x * sin + vec.z * cos;
        return new Vec3d(x, vec.y, z);
    }

    private void checkStuck() {
        if (lastPos == null) { lastPos = fakePlayer.getPos(); return; }
        double moved = fakePlayer.getPos().distanceTo(lastPos);
        if (moved < 0.05) {
            stuckCounter++;
            if (stuckCounter > 20) {
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextBoolean() ? 30 : -30);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
            }
            if (stuckCounter > 40) {
                jump();
                float newYaw = fakePlayer.getYaw() + (RANDOM.nextFloat() * 120 - 60);
                fakePlayer.setYaw(newYaw);
                fakePlayer.headYaw = newYaw;
                stuckCounter = 20;
                LOGGER.info("AI {} 严重卡住，跳跃并转向", fakePlayer.getName().getString());
            }
        } else {
            stuckCounter = Math.max(0, stuckCounter - 2);
        }
        lastPos = fakePlayer.getPos();
    }

    private void lookAt(Vec3d target) {
        Vec3d toTarget = target.subtract(fakePlayer.getPos());
        double dx = toTarget.x, dz = toTarget.z, dy = toTarget.y;
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
            if (player.isSpectator()) continue;
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

    // 公共刷新方法（供 NPCManager 调用）
    public void reloadONNXModule() {
        if (enableONNX) {
            try {
                if (onnxModule != null) onnxModule.close();
                this.onnxModule = ONNXDecisionModule.createDefault();
                LOGGER.info("AI {} ONNX 模块已重载", fakePlayer.getName().getString());
            } catch (Exception e) {
                LOGGER.error("AI {} 重载 ONNX 模块失败", fakePlayer.getName().getString(), e);
            }
        } else {
            if (onnxModule != null) {
                try { onnxModule.close(); } catch (Exception ignored) {}
                onnxModule = null;
            }
        }
    }
}