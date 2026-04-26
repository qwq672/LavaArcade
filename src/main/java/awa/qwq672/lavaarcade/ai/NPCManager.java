package awa.qwq672.lavaarcade.ai;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.util.*;
import java.util.stream.Collectors;

public class NPCManager {
    private static final List<AIPlayer> aiPlayers = new ArrayList<>();
    private static final Random random = new Random();
    private static final Set<String> generatedNames = new HashSet<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCManager");
    private static boolean serverStarted = false;
    private static final Map<UUID, Long> pendingGiveAll = new HashMap<>();

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
        source.sendMessage(Text.literal("§e/lava inventory <假人> §7- 打开假人背包（开发中）"));
        source.sendMessage(Text.literal("§e/lava giveitem <假人> [物品] [数量] [玩家] §7- 假人给予物品"));
        source.sendMessage(Text.literal("§e/lava pf <假人名字或#all> <动作> §7- 控制假人行为"));
        source.sendMessage(Text.literal("§e  动作: follow, go_alone, stop, continue, friendly"));
        source.sendMessage(Text.literal("§e  例如: /lava pf #all follow"));
        source.sendMessage(Text.literal("§e/spawnai §7- 手动生成一个假人"));
        return 1;
    }

    // 执行 giveitem 核心逻辑
    private static int executeGiveItem(CommandContext<ServerCommandSource> context, String targetName, String itemId, int amount, ServerPlayerEntity targetPlayer) {
        if (targetPlayer == null) {
            context.getSource().sendMessage(Text.literal("§c目标玩家不存在"));
            return 0;
        }
        if ("#all".equalsIgnoreCase(targetName)) {
            UUID playerId = context.getSource().getPlayer().getUuid();
            long now = System.currentTimeMillis();
            Long pending = pendingGiveAll.get(playerId);
            if (pending == null || (now - pending) > 30000) {
                pendingGiveAll.put(playerId, now);
                context.getSource().sendMessage(Text.literal("§e警告：批量给所有假人物品可能造成大量物品转移。请在30秒内再次执行此命令以确认。"));
                return 1;
            } else {
                pendingGiveAll.remove(playerId);
                for (AIPlayer ai : aiPlayers) {
                    giveItemToAI(ai, itemId, amount, targetPlayer, context);
                }
                context.getSource().sendMessage(Text.literal("§a已向所有假人执行给予物品命令"));
                return 1;
            }
        }
        AIPlayer ai = aiPlayers.stream()
                .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (ai == null) {
            context.getSource().sendMessage(Text.literal("§c未找到 AI: " + targetName));
            return 0;
        }
        return giveItemToAI(ai, itemId, amount, targetPlayer, context);
    }

    private static int giveItemToAI(AIPlayer ai, String itemId, int amount, ServerPlayerEntity targetPlayer, CommandContext<ServerCommandSource> context) {
        ItemStack sourceStack;
        if (itemId == null) {
            sourceStack = ai.getEntity().getMainHandStack().copy();
            if (sourceStack.isEmpty()) {
                context.getSource().sendMessage(Text.literal("§c假人 " + ai.getEntity().getName().getString() + " 手上没有物品"));
                return 0;
            }
            amount = (amount == -1) ? sourceStack.getCount() : amount;
        } else {
            Item item = Registries.ITEM.get(new Identifier(itemId));
            if (item == null || item == net.minecraft.item.Items.AIR) {
                context.getSource().sendMessage(Text.literal("§c无效的物品ID: " + itemId));
                return 0;
            }
            sourceStack = new ItemStack(item, amount);
        }
        int total = 0;
        for (int i = 0; i < ai.getEntity().getInventory().size(); i++) {
            ItemStack stack = ai.getEntity().getInventory().getStack(i);
            if (stack.getItem() == sourceStack.getItem()) {
                total += stack.getCount();
            }
        }
        int giveAmount = (amount == -1) ? sourceStack.getCount() : Math.min(amount, total);
        if (total == 0) {
            context.getSource().sendMessage(Text.literal("§c假人 " + ai.getEntity().getName().getString() + " 没有 " + sourceStack.getItem().getName().getString()));
            return 0;
        }
        if (giveAmount < amount && amount != -1) {
            context.getSource().sendMessage(Text.literal("§e假人只有 " + total + " 个，已全部给予"));
        }
        int remaining = giveAmount;
        for (int i = 0; i < ai.getEntity().getInventory().size() && remaining > 0; i++) {
            ItemStack stack = ai.getEntity().getInventory().getStack(i);
            if (stack.getItem() == sourceStack.getItem()) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
            }
        }
        ItemStack giveStack = sourceStack.copy();
        giveStack.setCount(giveAmount);
        targetPlayer.getInventory().insertStack(giveStack);
        context.getSource().sendMessage(Text.literal("§a假人 " + ai.getEntity().getName().getString() + " 给予了 " + targetPlayer.getName().getString() + " " + giveAmount + " 个 " + sourceStack.getItem().getName().getString()));
        return 1;
    }

    private static int executePfAction(CommandContext<ServerCommandSource> context, String target, String action) {
        if ("#all".equalsIgnoreCase(target)) {
            aiPlayers.forEach(ai -> applyActionToAI(ai, action));
            context.getSource().sendMessage(Text.literal("§a已对所有 AI 执行 " + action));
            return 1;
        }
        AIPlayer ai = aiPlayers.stream()
                .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(target))
                .findFirst().orElse(null);
        if (ai == null) {
            context.getSource().sendMessage(Text.literal("§c未找到 AI: " + target));
            return 0;
        }
        applyActionToAI(ai, action);
        context.getSource().sendMessage(Text.literal("§a已命令 " + target + " " + action));
        return 1;
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverStarted = true;
            server.execute(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    generateAIsForPlayer(player);
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player instanceof EntityPlayerMPFake) return;
            if (serverStarted) {
                server.execute(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    generateAIsForPlayer(player);
                });
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawnai")
                    .requires(s -> s.hasPermissionLevel(2))
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
                    .requires(s -> s.hasPermissionLevel(2))
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
                    .then(CommandManager.literal("inventory")
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                    .suggests((context, builder) -> net.minecraft.command.CommandSource.suggestMatching(getAIPlayerNames(), builder))
                                    .executes(context -> {
                                        String targetName = StringArgumentType.getString(context, "target");
                                        AIPlayer ai = aiPlayers.stream()
                                                .filter(a -> a.getEntity().getName().getString().equalsIgnoreCase(targetName))
                                                .findFirst().orElse(null);
                                        if (ai == null) {
                                            context.getSource().sendMessage(Text.literal("§c未找到 AI: " + targetName));
                                            return 0;
                                        }
                                        // 背包功能暂未完全实现，显示提示
                                        context.getSource().sendMessage(Text.literal("§a背包功能开发中，暂未完全实现"));
                                        return 1;
                                    })
                            )
                    )
                    .then(CommandManager.literal("giveitem")
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                    .suggests((context, builder) -> net.minecraft.command.CommandSource.suggestMatching(getAIPlayerNames(), builder))
                                    .executes(context -> {
                                        String targetName = StringArgumentType.getString(context, "target");
                                        return executeGiveItem(context, targetName, null, -1, context.getSource().getPlayer());
                                    })
                                    .then(CommandManager.argument("item", StringArgumentType.word())
                                            .executes(context -> {
                                                String targetName = StringArgumentType.getString(context, "target");
                                                String itemId = StringArgumentType.getString(context, "item");
                                                return executeGiveItem(context, targetName, itemId, -1, context.getSource().getPlayer());
                                            })
                                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 2304))
                                                    .executes(context -> {
                                                        String targetName = StringArgumentType.getString(context, "target");
                                                        String itemId = StringArgumentType.getString(context, "item");
                                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                                        return executeGiveItem(context, targetName, itemId, amount, context.getSource().getPlayer());
                                                    })
                                                    .then(CommandManager.argument("player", StringArgumentType.word())
                                                            .suggests((context, builder) -> net.minecraft.command.CommandSource.suggestMatching(context.getSource().getServer().getPlayerManager().getPlayerNames(), builder))
                                                            .executes(context -> {
                                                                String targetName = StringArgumentType.getString(context, "target");
                                                                String itemId = StringArgumentType.getString(context, "item");
                                                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                                                String playerName = StringArgumentType.getString(context, "player");
                                                                ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                                                if (targetPlayer == null) {
                                                                    context.getSource().sendMessage(Text.literal("§c玩家 " + playerName + " 不在线"));
                                                                    return 0;
                                                                }
                                                                return executeGiveItem(context, targetName, itemId, amount, targetPlayer);
                                                            })
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(CommandManager.literal("pf")
                            .executes(ctx -> {
                                ctx.getSource().sendMessage(Text.literal("§c用法: /lava pf <假人名字或#all> <动作>"));
                                return 0;
                            })
                            .then(CommandManager.argument("target", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        List<String> names = new ArrayList<>(getAIPlayerNames());
                                        names.add("#all");
                                        return net.minecraft.command.CommandSource.suggestMatching(names, builder);
                                    })
                                    .then(CommandManager.literal("follow")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                return executePfAction(context, target, "follow");
                                            })
                                    )
                                    .then(CommandManager.literal("go_alone")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                return executePfAction(context, target, "go_alone");
                                            })
                                    )
                                    .then(CommandManager.literal("stop")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                return executePfAction(context, target, "stop");
                                            })
                                    )
                                    .then(CommandManager.literal("continue")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                return executePfAction(context, target, "continue");
                                            })
                                    )
                                    .then(CommandManager.literal("friendly")
                                            .executes(context -> {
                                                String target = StringArgumentType.getString(context, "target");
                                                return executePfAction(context, target, "friendly");
                                            })
                                    )
                            )
                    )
            );
        });

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
                if (config.enableRespawn && entity.getHealth() - amount <= 0) {
                    entity.setHealth(20.0f);
                    entity.clearStatusEffects();
                    BlockPos spawnPos = entity.getServer().getWorld(World.OVERWORLD).getSpawnPos();
                    entity.teleport(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
                    ((ServerPlayerEntity) entity).sendMessage(Text.literal("§e你重生了！"));
                    return false;
                }
                return true;
            }
            return true;
        });
    }

    public static List<AIPlayer> getAIPlayers() {
        return aiPlayers;
    }
}