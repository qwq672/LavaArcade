package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

import java.util.*;

public class NPCManager {
    private static final List<AIPlayer> aiPlayers = new ArrayList<>();
    private static final Random random = new Random();
    private static final Set<String> generatedNames = new HashSet<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCManager");
    private static boolean serverStarted = false;

    private static void teleportAIToSafePosition(ServerPlayerEntity player, String aiName) {
        if (player == null) return;
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 5 + random.nextDouble() * 10;
        double x = player.getX() + radius * Math.cos(angle);
        double z = player.getZ() + radius * Math.sin(angle);
        double y = findSafeY(player.getServerWorld(), (int) x, (int) z, player.getY());
        if (Double.isNaN(y)) {
            y = player.getY();
            x = player.getX();
            z = player.getZ();
        }
        String tpCommand = String.format("/tp %s %.2f %.2f %.2f", aiName, x, y, z);
        player.getServer().getCommandManager().executeWithPrefix(
                player.getServer().getCommandSource(),
                tpCommand
        );
        LOGGER.info("AI {} 传送到 ({:.2f}, {:.2f}, {:.2f})", aiName, x, y, z);
    }

    private static double findSafeY(net.minecraft.server.world.ServerWorld world, int x, int z, double fallbackY) {
        for (int y = 320; y >= -64; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            BlockState above = world.getBlockState(pos.up());
            if (state.isSolid() && above.isAir()) {
                if (state.getBlock() != Blocks.LAVA && state.getBlock() != Blocks.WATER &&
                        state.getBlock() != Blocks.MAGMA_BLOCK && state.getBlock() != Blocks.CACTUS) {
                    return y + 1;
                }
            }
        }
        return Double.NaN;
    }

    private static EntityPlayerMPFake spawnAI(ServerPlayerEntity referencePlayer, String name) {
        if (referencePlayer == null) return null;
        referencePlayer.getServer().getCommandManager().executeWithPrefix(
                referencePlayer.getServer().getCommandSource(),
                "/player " + name + " spawn"
        );
        // 等待假人出现（最多等待1秒）
        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            ServerPlayerEntity fake = referencePlayer.getServer().getPlayerManager().getPlayer(name);
            if (fake instanceof EntityPlayerMPFake) {
                return (EntityPlayerMPFake) fake;
            }
        }
        return null;
    }

    private static void wrapAI(ServerPlayerEntity referencePlayer, EntityPlayerMPFake fake) {
        if (fake == null) return;
        if (aiPlayers.stream().anyMatch(ai -> ai.getEntity().getUuid().equals(fake.getUuid()))) return;
        AIPlayer ai = new AIPlayer(referencePlayer.getServerWorld(), fake);
        aiPlayers.add(ai);
        fake.addCommandTag("lavaarcade_ai");
        SkinManager.applyRandomSkin(fake);
        LOGGER.info("包装并应用皮肤 AI: {}", fake.getName().getString());
    }

    private static void generateAIsForPlayer(ServerPlayerEntity player) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableAI || config.aiCount <= 0) return;

        // 统计当前世界已存在的 AI 数量（通过标签）
        long currentCount = player.getServerWorld().getPlayers().stream()
                .filter(p -> p.getCommandTags().contains("lavaarcade_ai"))
                .count();
        int needed = config.aiCount - (int) currentCount;
        if (needed <= 0) return;

        for (int i = 0; i < needed; i++) {
            String name = AINameGenerator.generateName();
            while (generatedNames.contains(name)) {
                name = AINameGenerator.generateName();
            }
            generatedNames.add(name);
            EntityPlayerMPFake fake = spawnAI(player, name);
            if (fake != null) {
                teleportAIToSafePosition(player, name);
                wrapAI(player, fake);
            } else {
                LOGGER.warn("生成AI失败: {}", name);
            }
        }
        LOGGER.info("为玩家 {} 补足 {} 个AI", player.getName().getString(), needed);
    }

    private static void clearAllAIs(ServerCommandSource source) {
        for (AIPlayer ai : aiPlayers) {
            String name = ai.getEntity().getName().getString();
            source.getServer().getCommandManager().executeWithPrefix(
                    source.getServer().getCommandSource(),
                    "/player " + name + " kill"
            );
        }
        aiPlayers.clear();
        generatedNames.clear();
        LOGGER.info("所有AI已清除");
    }

    public static void init() {
        // 服务器启动时生成AI
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverStarted = true;
            server.execute(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    generateAIsForPlayer(player);
                }
            });
        });

        // 玩家加入时补足AI（过滤掉假人）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // 关键修复：如果是 Carpet 假人，跳过，避免递归
            if (player instanceof EntityPlayerMPFake) {
                LOGGER.debug("跳过假人 {} 的 JOIN 事件", player.getName().getString());
                return;
            }
            if (serverStarted) {
                server.execute(() -> generateAIsForPlayer(player));
            }
        });

        // 命令注册（省略，与之前相同，可复用之前提供的完整命令部分）
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /spawnai 手动生成
            dispatcher.register(CommandManager.literal("spawnai")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            String name = AINameGenerator.generateName();
                            generatedNames.add(name);
                            EntityPlayerMPFake fake = spawnAI(player, name);
                            if (fake != null) {
                                teleportAIToSafePosition(player, name);
                                wrapAI(player, fake);
                                context.getSource().sendMessage(Text.literal("§a已生成AI: " + name));
                            } else {
                                context.getSource().sendMessage(Text.literal("§c生成失败"));
                            }
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
                                clearAllAIs(source);
                                generateAIsForPlayer(player);
                                source.sendMessage(Text.literal("§a已重新生成AI"));
                                return 1;
                            })
                    )
                    .then(CommandManager.literal("moveai")
                            .then(CommandManager.literal("on")
                                    .executes(context -> {
                                        AIPlayer.setMoveEnabled(true);
                                        context.getSource().sendMessage(Text.literal("§aAI移动已开启"));
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("off")
                                    .executes(context -> {
                                        AIPlayer.setMoveEnabled(false);
                                        context.getSource().sendMessage(Text.literal("§aAI移动已关闭"));
                                        return 1;
                                    })
                            )
                    )
                    .then(CommandManager.literal("setfollow")
                            .then(CommandManager.argument("distance", StringArgumentType.word())
                                    .executes(context -> {
                                        String arg = StringArgumentType.getString(context, "distance");
                                        try {
                                            int dist = Integer.parseInt(arg);
                                            if (dist < 1 || dist > 20) {
                                                context.getSource().sendMessage(Text.literal("§c距离需在1-20之间"));
                                                return 0;
                                            }
                                            AIPlayer.setFollowDistance(dist);
                                            context.getSource().sendMessage(Text.literal("§aAI跟随距离已设置为 " + dist + " 格"));
                                        } catch (NumberFormatException e) {
                                            context.getSource().sendMessage(Text.literal("§c请输入数字"));
                                        }
                                        return 1;
                                    })
                            )
                    )
            );

            // /askai 命令
            dispatcher.register(CommandManager.literal("askai")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("task", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                        String task = StringArgumentType.getString(context, "task");
                                        // 查找对应的AIPlayer
                                        AIPlayer ai = aiPlayers.stream()
                                                .filter(a -> a.getEntity().getUuid().equals(target.getUuid()))
                                                .findFirst().orElse(null);
                                        if (ai == null) {
                                            context.getSource().sendMessage(Text.literal("§c目标不是AI玩家"));
                                            return 0;
                                        }
                                        if (ai.shouldAcceptRequest(task, "")) {
                                            ai.executeTask(task);
                                            context.getSource().sendMessage(Text.literal("§a已向AI发送请求"));
                                        } else {
                                            context.getSource().sendMessage(Text.literal("§cAI拒绝了你的请求"));
                                        }
                                        return 1;
                                    })
                            )
                    )
            );
        });

        // Tick 更新
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AIConfig.ConfigData config = AIConfig.getConfig();
            if (!config.enableAI) {
                if (!aiPlayers.isEmpty()) {
                    clearAllAIs(server.getCommandSource());
                }
                return;
            }
            aiPlayers.forEach(AIPlayer::tick);
        });
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}