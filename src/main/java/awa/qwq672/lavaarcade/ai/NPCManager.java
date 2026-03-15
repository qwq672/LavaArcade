package awa.qwq672.lavaarcade.ai;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class NPCManager {
    private static final List<AIPlayer> aiPlayers = new ArrayList<>();
    private static final Random random = new Random();
    private static boolean hasSpawnedForWorld = false;

    public static void init() {
        // 调试命令 /spawnai
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawnai")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            spawnAIPlayers(player.getServerWorld(), player.getBlockPos(), 1);
                            context.getSource().sendMessage(Text.literal("§a已生成一个 AI 玩家！"));
                        }
                        return 1;
                    })
            );

            // 命令 /askai <玩家> <任务>
            dispatcher.register(CommandManager.literal("askai")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("task", TextArgumentType.text())
                                    .executes(context -> {
                                        ServerCommandSource source = context.getSource();
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                        String task = TextArgumentType.getTextArgument(context, "task").getString();

                                        for (AIPlayer ai : aiPlayers) {
                                            if (ai.getEntity().getUuid().equals(target.getUuid())) {
                                                boolean accept = ai.shouldAcceptRequest(task, "");
                                                if (accept) {
                                                    source.sendMessage(Text.literal("§aAI 接受了你的请求！"));
                                                    ai.executeTask(task);
                                                } else {
                                                    source.sendMessage(Text.literal("§cAI 拒绝了你的请求。"));
                                                }
                                                break;
                                            }
                                        }
                                        return 1;
                                    })
                            )
                    )
            );
        });

        // 玩家加入时生成 AI（仅第一次）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!hasSpawnedForWorld) {
                AIConfig.ConfigData config = AIConfig.getConfig();
                if (config.enableAI && config.aiCount > 0) {
                    ServerPlayerEntity player = handler.getPlayer();
                    spawnAIPlayers(player.getServerWorld(), player.getBlockPos(), config.aiCount);
                    hasSpawnedForWorld = true;
                }
            }
        });

        // Tick 更新所有 AI
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            aiPlayers.forEach(AIPlayer::tick);
        });
    }

    private static void spawnAIPlayers(ServerWorld world, BlockPos near, int count) {
        for (int i = 0; i < count; i++) {
            String name = AINameGenerator.generateName();
            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, name);

            FakePlayer fakePlayer = FakePlayer.get(world, profile);
            if (fakePlayer == null) continue;

            int offsetX = random.nextInt(6) - 3;
            int offsetZ = random.nextInt(6) - 3;
            fakePlayer.refreshPositionAndAngles(
                    near.getX() + offsetX, near.getY(), near.getZ() + offsetZ,
                    random.nextFloat() * 360, 0
            );

            fakePlayer.setCustomName(Text.literal(name));
            fakePlayer.setCustomNameVisible(true);
            fakePlayer.setInvulnerable(true);

            world.spawnEntity(fakePlayer);

            // 注意：AIPlayer 的构造器需要 ServerPlayerEntity 类型，这里传入 fakePlayer
            AIPlayer ai = new AIPlayer(world, fakePlayer);
            aiPlayers.add(ai);
        }
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}