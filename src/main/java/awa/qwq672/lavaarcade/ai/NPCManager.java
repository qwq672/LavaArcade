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

            // /askai 命令 - 修复：正确查找 AI
            dispatcher.register(CommandManager.literal("askai")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("task", TextArgumentType.text())
                                    .executes(context -> {
                                        ServerCommandSource source = context.getSource();
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                        String task = TextArgumentType.getTextArgument(context, "task").getString();

                                        // 在 aiPlayers 中查找
                                        AIPlayer found = null;
                                        for (AIPlayer ai : aiPlayers) {
                                            if (ai.getEntity().getUuid().equals(target.getUuid())) {
                                                found = ai;
                                                break;
                                            }
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

        // 玩家加入时自动生成 AI（仅第一次）
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

        // 每 tick 扫描并包装假人，同时更新 AI
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 获取当前渲染距离配置
            AIConfig.ConfigData config = AIConfig.getConfig();
            int renderDistance = config.renderDistanceChunks * 16; // 区块转格数（粗略）

            // 扫描所有在线玩家，将新生成的假人包装为 AIPlayer
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // 检查是否已被包装
                if (aiPlayers.stream().anyMatch(ai -> ai.getEntity().getUuid().equals(player.getUuid()))) {
                    continue;
                }
                String name = player.getName().getString();
                if (generatedNames.remove(name) && player instanceof EntityPlayerMPFake fakePlayer) {
                    // 应用皮肤（SkinManager 已实现）
                    SkinManager.applyRandomSkin(fakePlayer);
                    AIPlayer ai = new AIPlayer(server.getOverworld(), fakePlayer);
                    aiPlayers.add(ai);
                    player.addCommandTag("lavaarcade_ai");
                }
            }

            // 更新所有 AI（但根据距离决定是否执行 tick？我们只控制可见性，不控制逻辑）
            // 逻辑仍执行，但可见性由客户端渲染距离控制（见下方客户端修改）
            aiPlayers.forEach(AIPlayer::tick);
        });
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}