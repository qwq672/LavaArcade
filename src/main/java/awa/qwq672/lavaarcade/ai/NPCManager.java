package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class NPCManager {
    private static final List<AIPlayer> aiPlayers = new ArrayList<>();
    private static final Random random = new Random();
    private static boolean hasSpawnedForWorld = false;
    private static final Set<String> generatedNames = new HashSet<>();

    public static void init() {
        // /spawnai 命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawnai")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            AIConfig.ConfigData config = AIConfig.getConfig();
                            if (!config.enableAI) {
                                context.getSource().sendMessage(Text.literal("§cAI 功能已禁用，无法生成。"));
                                return 0;
                            }
                            String name = AINameGenerator.generateName();
                            generatedNames.add(name);
                            player.getServer().getCommandManager().executeWithPrefix(
                                    player.getCommandSource(),
                                    "player " + name + " spawn"
                            );
                            context.getSource().sendMessage(Text.literal("§a已生成一个 AI 玩家！"));
                        }
                        return 1;
                    })
            );

            // /lava reloadai
            dispatcher.register(CommandManager.literal("lava")
                    .then(CommandManager.literal("reloadai")
                            .executes(context -> {
                                ServerCommandSource source = context.getSource();
                                ServerPlayerEntity player = source.getPlayer();
                                if (player == null) return 0;

                                for (AIPlayer ai : aiPlayers) {
                                    ai.getEntity().discard();
                                }
                                aiPlayers.clear();

                                AIConfig.ConfigData config = AIConfig.getConfig();
                                if (config.enableAI && config.aiCount > 0) {
                                    for (int i = 0; i < config.aiCount; i++) {
                                        String name = AINameGenerator.generateName();
                                        generatedNames.add(name);
                                        player.getServer().getCommandManager().executeWithPrefix(
                                                player.getCommandSource(),
                                                "player " + name + " spawn"
                                        );
                                    }
                                    source.sendMessage(Text.literal("§a已根据配置重新生成 " + config.aiCount + " 个 AI 玩家。"));
                                } else {
                                    source.sendMessage(Text.literal("§cAI 功能未启用或数量为0，已清除所有 AI。"));
                                }
                                return 1;
                            })
                    )
            );

            // /askai
            dispatcher.register(CommandManager.literal("askai")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("task", TextArgumentType.text())
                                    .executes(context -> {
                                        ServerCommandSource source = context.getSource();
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                        String task = TextArgumentType.getTextArgument(context, "task").getString();

                                        AIPlayer found = null;
                                        for (AIPlayer ai : aiPlayers) {
                                            if (ai.getEntity().getUuid().equals(target.getUuid())) {
                                                found = ai;
                                                break;
                                            }
                                        }
                                        if (found == null && target.getCommandTags().contains("lavaarcade_ai")) {
                                            found = new AIPlayer(target.getServerWorld(), (EntityPlayerMPFake) target);
                                            aiPlayers.add(found);
                                        }

                                        if (found != null) {
                                            boolean accept = found.shouldAcceptRequest(task, "");
                                            if (accept) {
                                                source.sendMessage(Text.literal("§aAI 接受了你的请求！"));
                                                found.executeTask(task);
                                            } else {
                                                source.sendMessage(Text.literal("§cAI 拒绝了你的请求。"));
                                            }
                                        } else {
                                            source.sendMessage(Text.literal("§c未找到指定的 AI 玩家。"));
                                        }
                                        return 1;
                                    })
                            )
                    )
            );
        });

        // 自动生成（仅第一次）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!hasSpawnedForWorld) {
                AIConfig.ConfigData config = AIConfig.getConfig();
                if (config.enableAI && config.aiCount > 0) {
                    for (int i = 0; i < config.aiCount; i++) {
                        String name = AINameGenerator.generateName();
                        generatedNames.add(name);
                        server.getCommandManager().executeWithPrefix(
                                handler.getPlayer().getCommandSource(),
                                "player " + name + " spawn"
                        );
                    }
                    hasSpawnedForWorld = true;
                }
            }
        });

        // Tick 更新
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AIConfig.ConfigData config = AIConfig.getConfig();
            if (!config.enableAI) {
                if (!aiPlayers.isEmpty()) {
                    for (AIPlayer ai : aiPlayers) {
                        ai.getEntity().discard();
                    }
                    aiPlayers.clear();
                    generatedNames.clear();
                }
                return;
            }

            // 包装新假人
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (aiPlayers.stream().anyMatch(ai -> ai.getEntity().getUuid().equals(player.getUuid()))) {
                    continue;
                }
                String name = player.getName().getString();
                if (generatedNames.remove(name) && player instanceof EntityPlayerMPFake fakePlayer) {
                    SkinManager.applyRandomSkin(fakePlayer);
                    AIPlayer ai = new AIPlayer(server.getOverworld(), fakePlayer);
                    aiPlayers.add(ai);
                    player.addCommandTag("lavaarcade_ai");
                }
            }
            aiPlayers.forEach(AIPlayer::tick);
        });
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}