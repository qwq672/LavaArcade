package awa.qwq672.lavaarcade.ai;

import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class AIStateExtractor {

    public static float[] extractState(ServerPlayerEntity player, GameModeType mode) {
        float[] state = new float[28];
        int idx = 0;

        state[idx++] = (float) (player.getHealth() / player.getMaxHealth());
        state[idx++] = player.getHungerManager().getFoodLevel() / 20.0f;
        state[idx++] = (float) player.getX();
        state[idx++] = (float) player.getZ();
        state[idx++] = (float) (player.getY() / 256.0);
        Vec3d vel = player.getVelocity();
        state[idx++] = (float) (vel.x / 10.0);
        state[idx++] = (float) (vel.z / 10.0);
        state[idx++] = player.isOnGround() ? 1.0f : 0.0f;
        state[idx++] = player.isTouchingWater() ? 1.0f : 0.0f;
        state[idx++] = (player.getYaw() % 360) / 360.0f;
        state[idx++] = player.getPitch() / 90.0f;

        float[] enemy = getNearestEnemyInfo(player);
        state[idx++] = enemy[0];
        state[idx++] = enemy[1];
        state[idx++] = player.getAttackCooldownProgress(0.5f);
        state[idx++] = getItemCode(player.getMainHandStack()) / 10.0f;

        if (mode == GameModeType.PVP) {
            state[idx++] = 1.0f; state[idx++] = 0.0f; state[idx++] = 0.0f;
        } else if (mode == GameModeType.MINIGAME) {
            state[idx++] = 0.0f; state[idx++] = 1.0f; state[idx++] = 0.0f;
        } else {
            state[idx++] = 0.0f; state[idx++] = 0.0f; state[idx++] = 1.0f;
        }

        for (int i = 0; i < 10; i++) {
            state[idx++] = (i == 0) ? 1.0f : 0.0f;
        }

        return state;
    }

    private static float[] getNearestEnemyInfo(ServerPlayerEntity player) {
        ServerPlayerEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player) continue;
            double dist = player.distanceTo(other);
            if (dist < minDist) {
                minDist = dist;
                nearest = other;
            }
        }
        if (nearest != null) {
            float dist = (float) Math.min(1.0, minDist / 32.0);
            double dx = nearest.getX() - player.getX();
            double dz = nearest.getZ() - player.getZ();
            double angleToEnemy = Math.toDegrees(Math.atan2(dz, dx));
            double playerYaw = player.getYaw();
            double rel = angleToEnemy - playerYaw;
            rel = ((rel % 360) + 360) % 360;
            if (rel > 180) rel -= 360;
            float angle = (float) (rel / 180.0);
            return new float[]{dist, angle};
        }
        return new float[]{1.0f, 0.0f};
    }

    private static int getItemCode(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (item instanceof SwordItem) return 1;
        if (item instanceof AxeItem) return 2;
        if (item instanceof PickaxeItem) return 3;
        if (item instanceof BlockItem) return 4;
        if (item instanceof BowItem) return 5;
        if (item.isFood()) return 6;
        return 7;
    }
}