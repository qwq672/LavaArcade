package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

import awa.qwq672.lavaarcade.ai.SpeechManager;

public class NPCManager {
    private static final List<AIPlayer> aiPlayers = new ArrayList<>();
    private static final Random random = new Random();
    private static final Set<String> generatedNames = new HashSet<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCManager");
    private static boolean serverStarted = false;

    // ==================== 辅助方法 ====================
    private static double findSafeY(ServerWorld world, int x, int z, double fallbackY) {
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

    private static EntityPlayerMPFake spawnAI(ServerPlayerEntity referencePlayer, String name) {
        if (referencePlayer == null) return null;
        referencePlayer.getServer().getCommandManager().executeWithPrefix(
                referencePlayer.getServer().getCommandSource(),
                "/player " + name + " spawn"
        );
        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            ServerPlayerEntity fake = referencePlayer.getServer().getPlayerManager().getPlayer(name);
            if (fake instanceof EntityPlayerMPFake) {
                EntityPlayerMPFake fakePlayer = (EntityPlayerMPFake) fake;
                fakePlayer.setInvulnerable(false);
                fakePlayer.setHealth(20.0f);
                return fakePlayer;
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
        fake.setInvulnerable(false);
        LOGGER.info("包装并添加 AI: {}, 当前 aiPlayers 数量: {}", fake.getName().getString(), aiPlayers.size());
    }

    private static void generateAIsForPlayer(ServerPlayerEntity player) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableAI || config.aiCount <= 0) return;

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

    private static Collection<String> getAIPlayerNames() {
        return aiPlayers.stream()
                .map(ai -> ai.getEntity().getName().getString())
                .collect(Collectors.toList());
    }

    private static void applyActionToAI(AIPlayer ai, String action) {
        switch (action.toLowerCase()) {
            case "follow":
                ai.setBehavior(AIBehavior.FOLLOW);
                break;
            case "go_alone":
                ai.setBehavior(AIBehavior.EXPLORE);
                break;
            case "stop":
                ai.setBehavior(AIBehavior.IDLE);
                break;
            case "continue":
                ai.setBehavior(AIBehavior.FOLLOW);
                break;
            case "friendly":
                ai.setBehavior(AIBehavior.FRIENDLY);
                break;
            default:
                LOGGER.warn("未知动作: {}", action);
        }
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("§6===== LavaArcade 假人命令帮助 ====="));
        source.sendMessage(Text.literal("§e/lava §7- 显示本帮助"));
        source.sendMessage(Text.literal("§e/lava help §7- 显示本帮助"));
        source.sendMessage(Text.literal("§e/lava reloadai §7- 根据配置重新生成所有假人"));
        source.sendMessage(Text.literal("§e/lava moveai on/off §7- 全局开关假人移动"));
        source.sendMessage(Text.literal("§e/lava setfollow <距离> §7- 设置跟随距离 (1-20)"));
        source.sendMessage(Text.literal("§e/lava tools true/false §7- 允许/禁止假人使用工具"));
        source.sendMessage(Text.literal("§e/lava toolblocks true/false §7- 允许/禁止假人使用功能方块"));
        source.sendMessage(Text.literal("§e/lava pf <假人名字或@all> <动作> §7- 控制假人行为"));
        source.sendMessage(Text.literal("§e  动作: follow, go_alone, stop, continue, friendly"));
        source.sendMessage(Text.literal("§e  例如: /lava pf @all follow"));
        source.sendMessage(Text.literal("§e/spawnai §7- 手动生成一个假人"));
        return 1;
    }

    // ==================== 初始化 ====================
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

        // 玩家加入时补足AI
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player instanceof EntityPlayerMPFake) return;
            if (serverStarted) {
                server.execute(() -> generateAIsForPlayer(player));
            }
        });

        // 命令注册
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawnai")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            AIConfig.ConfigData config = AIConfig.getConfig();
                            if (!config.enableAI) {
                                context.getSource().sendMessage(Text.literal("§cAI 功能已禁用"));
                                return 0;
                            }
                            String name = AINameGenerator.generateName();
                            generatedNames.add(name);
                            EntityPlayerMPFake fake = spawnAI(player, name);
                            if (fake != null) {
                                teleportAIToSafePosition(player, name);
                                wrapAI(player, fake);
                                context.getSource().sendMessage(Text.literal("§a已生成 AI: " + name));
                            } else {
                                context.getSource().sendMessage(Text.literal("§c生成失败"));
                            }
                        }
                        return 1;
                    })
            );

            dispatcher.register(CommandManager.literal("lava")
                    .executes(ctx -> showHelp(ctx.getSource()))
                    .then(CommandManager.literal("help").executes(ctx -> showHelp(ctx.getSource())))
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
                    .then(CommandManager.literal("tools")
                            .then(CommandManager.literal("true")
                                    .executes(context -> {
                                        AIPlayer.setAllowTools(true);
                                        context.getSource().sendMessage(Text.literal("§aAI 允许使用工具"));
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("false")
                                    .executes(context -> {
                                        AIPlayer.setAllowTools(false);
                                        context.getSource().sendMessage(Text.literal("§aAI 禁止使用工具"));
                                        return 1;
                                    })
                            )
                    )
                    .then(CommandManager.literal("toolblocks")
                            .then(CommandManager.literal("true")
                                    .executes(context -> {
                                        AIPlayer.setAllowToolBlocks(true);
                                        context.getSource().sendMessage(Text.literal("§aAI 允许使用功能方块"));
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("false")
                                    .executes(context -> {
                                        AIPlayer.setAllowToolBlocks(false);
                                        context.getSource().sendMessage(Text.literal("§aAI 禁止使用功能方块"));
                                        return 1;
                                    })
                            )
                    )
                    .then(CommandManager.literal("pf")
                            .executes(ctx -> {
                                ctx.getSource().sendMessage(Text.literal("§c用法: /lava pf <假人名字或@all> <动作>"));
                                return 0;
                            })
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        List<String> names = new ArrayList<>(getAIPlayerNames());
                                        names.add("@all");
                                        return net.minecraft.command.CommandSource.suggestMatching(names, builder);
                                    })
                                    .then(CommandManager.literal("follow")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                if ("@all".equalsIgnoreCase(target)) {
                                                    aiPlayers.forEach(ai -> applyActionToAI(ai, "follow"));
                                                    context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 follow"));
                                                } else {
                                                    AIPlayer ai = aiPlayers.stream()
                                                            .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                                                            .findFirst().orElse(null);
                                                    if (ai == null) {
                                                        context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
                                                        return 0;
                                                    }
                                                    applyActionToAI(ai, "follow");
                                                    context.getSource().sendMessage(Text.literal("§a已命令 " + target + " follow"));
                                                }
                                                return 1;
                                            })
                                    )
                                    .then(CommandManager.literal("go_alone")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                if ("@all".equalsIgnoreCase(target)) {
                                                    aiPlayers.forEach(ai -> applyActionToAI(ai, "go_alone"));
                                                    context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 go_alone"));
                                                } else {
                                                    AIPlayer ai = aiPlayers.stream()
                                                            .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                                                            .findFirst().orElse(null);
                                                    if (ai == null) {
                                                        context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
                                                        return 0;
                                                    }
                                                    applyActionToAI(ai, "go_alone");
                                                    context.getSource().sendMessage(Text.literal("§a已命令 " + target + " go_alone"));
                                                }
                                                return 1;
                                            })
                                    )
                                    .then(CommandManager.literal("stop")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                if ("@all".equalsIgnoreCase(target)) {
                                                    aiPlayers.forEach(ai -> applyActionToAI(ai, "stop"));
                                                    context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 stop"));
                                                } else {
                                                    AIPlayer ai = aiPlayers.stream()
                                                            .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                                                            .findFirst().orElse(null);
                                                    if (ai == null) {
                                                        context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
                                                        return 0;
                                                    }
                                                    applyActionToAI(ai, "stop");
                                                    context.getSource().sendMessage(Text.literal("§a已命令 " + target + " stop"));
                                                }
                                                return 1;
                                            })
                                    )
                                    .then(CommandManager.literal("continue")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                if ("@all".equalsIgnoreCase(target)) {
                                                    aiPlayers.forEach(ai -> applyActionToAI(ai, "continue"));
                                                    context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 continue"));
                                                } else {
                                                    AIPlayer ai = aiPlayers.stream()
                                                            .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                                                            .findFirst().orElse(null);
                                                    if (ai == null) {
                                                        context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
                                                        return 0;
                                                    }
                                                    applyActionToAI(ai, "continue");
                                                    context.getSource().sendMessage(Text.literal("§a已命令 " + target + " continue"));
                                                }
                                                return 1;
                                            })
                                    )
                                    .then(CommandManager.literal("friendly")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                if ("@all".equalsIgnoreCase(target)) {
                                                    aiPlayers.forEach(ai -> applyActionToAI(ai, "friendly"));
                                                    context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 friendly"));
                                                } else {
                                                    AIPlayer ai = aiPlayers.stream()
                                                            .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                                                            .findFirst().orElse(null);
                                                    if (ai == null) {
                                                        context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
                                                        return 0;
                                                    }
                                                    applyActionToAI(ai, "friendly");
                                                    context.getSource().sendMessage(Text.literal("§a已命令 " + target + " friendly"));
                                                }
                                                return 1;
                                            })
                                    )
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
            for (AIPlayer ai : aiPlayers) {
                ai.tick();
            }
            SpeechManager.tick(server, aiPlayers);
        });

        // 伤害事件：重生和反击
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof EntityPlayerMPFake) {
                EntityPlayerMPFake fake = (EntityPlayerMPFake) entity;
                AIPlayer ai = aiPlayers.stream()
                        .filter(a -> a.getEntity().getUuid().equals(fake.getUuid()))
                        .findFirst()
                        .orElse(null);
                if (ai != null && source.getAttacker() instanceof LivingEntity) {
                    ai.onAttacked((LivingEntity) source.getAttacker());
                }

                AIConfig.ConfigData config = AIConfig.getConfig();
                // 只有致命伤害且开启重生时才拦截
                if (config.enableRespawn && entity.getHealth() - amount <= 0) {
                    entity.setHealth(20.0f);
                    entity.clearStatusEffects();
                    BlockPos spawnPos = entity.getServer().getWorld(World.OVERWORLD).getSpawnPos();
                    entity.teleport(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                    ((ServerPlayerEntity) entity).sendMessage(Text.literal("§e你重生了！"));
                    return false;
                }
                // 非致命伤害或未开启重生，允许伤害正常进行
                return true;
            }
            return true;
        });
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}