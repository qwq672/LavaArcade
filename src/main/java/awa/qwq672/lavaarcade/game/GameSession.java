package awa.qwq672.lavaarcade.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameSession {
    public final UUID id = UUID.randomUUID();
    private final MinecraftServer server;
    private final GameScript script;
    private final List<ServerPlayerEntity> players;
    private final Set<ServerPlayerEntity> eliminated = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<ServerPlayerEntity, Integer> kills = new ConcurrentHashMap<>();
    private boolean active = true;
    private int timer = 0;
    private boolean damageListenerRegistered = false;

    public GameSession(MinecraftServer server, GameScript script, List<ServerPlayerEntity> players) {
        this.server = server;
        this.script = script;
        this.players = new ArrayList<>(players);
        for (ServerPlayerEntity p : players) {
            kills.put(p, 0);
        }
        grantGameGear();
        initScoreboard();
        teleportPlayersToSpawn();
        registerDamageListener();
        executeCommands(script.startCommands);
        broadcast(Text.literal("§a游戏《" + script.name + "》开始！"));
    }

    private void grantGameGear() {
        if (script.gameGear == null) return;
        for (ServerPlayerEntity p : players) {
            if (script.gameGear.clearInventory) {
                p.getInventory().clear();
                p.getInventory().armor.clear();
                p.getInventory().offHand.set(0, ItemStack.EMPTY);
            }
            for (GameScript.ItemStackConfig config : script.gameGear.items) {
                Item item = Registries.ITEM.get(new Identifier(config.item));
                if (item == Items.AIR) continue;
                ItemStack stack = new ItemStack(item, config.count);
                if (config.slot >= 0 && config.slot < 36) {
                    p.getInventory().setStack(config.slot, stack);
                } else {
                    p.getInventory().insertStack(stack);
                }
            }
        }
    }

    private void clearGameGear() {
        if (script.gameGear == null) return;
        for (ServerPlayerEntity p : players) {
            for (GameScript.ItemStackConfig config : script.gameGear.items) {
                Item item = Registries.ITEM.get(new Identifier(config.item));
                if (item == Items.AIR) continue;
                p.getInventory().remove(stack -> stack.getItem() == item, 64, p.getInventory());
            }
        }
    }

    private void initScoreboard() {
        if (script.scoreboard != null) {
            GameScoreboard.createObjective(server, script.scoreboard.title);
            for (ServerPlayerEntity p : players) {
                GameScoreboard.setScore(server, p.getName().getString(), script.scoreboard.initialValue);
                GameScoreboard.showToPlayer(p);
            }
        }
    }

    private void teleportPlayersToSpawn() {
        if (script.spawnPoints == null || script.spawnPoints.isEmpty()) return;
        Random rand = new Random();
        for (ServerPlayerEntity p : players) {
            BlockPos spawn = script.spawnPoints.get(rand.nextInt(script.spawnPoints.size()));
            p.teleport(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }
    }

    private void registerDamageListener() {
        if (damageListenerRegistered) return;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!active) return true;
            if (!(entity instanceof ServerPlayerEntity)) return true;
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (!players.contains(player) || eliminated.contains(player)) return true;

            if (script.invincibleInside && isInRegion(player)) {
                if (source.getAttacker() instanceof LivingEntity && script.knockbackOnHit) {
                    applyKnockback(player, (LivingEntity) source.getAttacker());
                }
                return false;
            }
            return true;
        });
        damageListenerRegistered = true;
    }

    private void applyKnockback(ServerPlayerEntity player, LivingEntity attacker) {
        Vec3d knockDir = player.getPos().subtract(attacker.getPos()).normalize();
        player.addVelocity(knockDir.x * 0.4, 0.2, knockDir.z * 0.4);
        player.velocityModified = true;
    }

    public void tick() {
        if (!active) return;
        if (script.timeLimit > 0) {
            timer++;
            if (timer >= script.timeLimit * 20) {
                endGame(null);
                return;
            }
        }
        for (ServerPlayerEntity p : players) {
            if (eliminated.contains(p)) continue;
            if (isEliminated(p)) {
                eliminate(p);
            }
        }
        checkWin();
    }

    private boolean isEliminated(ServerPlayerEntity player) {
        if (script.eliminationRule == null) return false;
        String condition = script.eliminationRule.condition;
        switch (condition) {
            case "out_of_bounds":
                if (script.region == null) return false;
                if (script.region.shape.equals("cube")) {
                    Box box = new Box(script.region.from, script.region.to);
                    return !box.contains(player.getPos());
                } else if (script.region.shape.equals("sphere")) {
                    return player.getPos().distanceTo(Vec3d.ofCenter(script.region.center)) > script.region.radius;
                }
                return false;
            case "fall_into_void":
                return player.getY() < -64;
            case "health_zero":
                return player.getHealth() <= 0.0f;
            default:
                return false;
        }
    }

    private void eliminate(ServerPlayerEntity player) {
        eliminated.add(player);
        String msg = script.eliminationRule != null ? script.eliminationRule.message : "{player} 被淘汰";
        msg = msg.replace("{player}", player.getName().getString());
        broadcast(Text.literal("§c" + msg));
        if (script.onEliminate != null) {
            String cmd = script.onEliminate.replace("{player}", player.getName().getString());
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
        }
        player.teleport(0, 100, 0);
        if (script.scoreboard != null) {
            GameScoreboard.setScore(server, player.getName().getString(), -1);
        }
        if (script.respawnAfterElimination) {
        }
    }

    public void recordKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (!active) return;
        if (!players.contains(killer) || eliminated.contains(killer)) return;
        kills.put(killer, kills.getOrDefault(killer, 0) + 1);
        if (script.scoreboard != null) {
            GameScoreboard.setScore(server, killer.getName().getString(), kills.get(killer));
        }
        broadcast(Text.literal("§e" + killer.getName().getString() + " 击杀了 " + victim.getName().getString()));
        if (script.onKill != null) {
            String cmd = script.onKill.replace("{killer}", killer.getName().getString()).replace("{victim}", victim.getName().getString());
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
        }
        checkWin();
    }

    private void checkWin() {
        List<ServerPlayerEntity> alive = new ArrayList<>(players);
        alive.removeAll(eliminated);
        if (script.winCondition == null) return;
        String type = script.winCondition.type;
        switch (type) {
            case "last_survivor":
                if (alive.size() <= 1) {
                    endGame(alive.size() == 1 ? alive.get(0) : null);
                }
                break;
            case "first_to_kills":
                for (Map.Entry<ServerPlayerEntity, Integer> entry : kills.entrySet()) {
                    if (entry.getValue() >= script.winCondition.value) {
                        endGame(entry.getKey());
                        return;
                    }
                }
                break;
        }
    }

    public void endGame(ServerPlayerEntity winner) {
        active = false;
        if (winner != null) {
            broadcast(Text.literal("§6" + winner.getName().getString() + " 赢得了游戏！"));
        } else {
            broadcast(Text.literal("§e游戏结束，无人获胜"));
        }
        executeCommands(script.endCommands);
        clearGameGear();
        cleanupScoreboard();
        GameManager.removeSession(this.id);
    }

    private void cleanupScoreboard() {
        if (script.scoreboard != null) {
            GameScoreboard.hideFromAll(server);
            GameScoreboard.removeObjective(server);
        }
    }

    private void executeCommands(List<String> commands) {
        if (commands == null) return;
        for (String cmd : commands) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
        }
    }

    private void broadcast(Text message) {
        for (ServerPlayerEntity p : players) {
            p.sendMessage(message, false);
        }
    }

    public boolean isActive() { return active; }
    public List<ServerPlayerEntity> getPlayers() { return players; }
    public Set<ServerPlayerEntity> getEliminated() { return eliminated; }
    public GameScript getScript() { return script; }

    public boolean isInRegion(ServerPlayerEntity player) {
        if (script.region == null) return true;
        if (script.region.shape.equals("cube")) {
            Box box = new Box(script.region.from, script.region.to);
            return box.contains(player.getPos());
        } else if (script.region.shape.equals("sphere")) {
            return player.getPos().distanceTo(Vec3d.ofCenter(script.region.center)) <= script.region.radius;
        }
        return true;
    }
}