package awa.qwq672.lavaarcade.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.*;

public class SpeechManager {
    private static final Random RANDOM = new Random();
    private static int idleCounter = 0;
    // 记录每个假人上次问好的时间（tick）
    private static final Map<UUID, Integer> lastGreetingTick = new HashMap<>();



    // 主动让假人说一句话（通过消息模板）
    public static void say(AIPlayer ai, String key, Map<String, String> placeholders) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableSpeech) return;
        String message = SpeechLoader.getRandomMessage(key, placeholders);
        if (message == null || message.equals("???")) return;
        String formatted = "§7[AI] " + ai.getEntity().getName().getString() + "§r: " + message;
        ai.getEntity().getServer().getPlayerManager().broadcast(Text.literal(formatted), false);
    }

    // 定时闲聊
    public static void tick(MinecraftServer server, List<AIPlayer> aiPlayers) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableSpeech) return;

        idleCounter++;
        if (idleCounter >= 600) {
            idleCounter = 0;
            if (aiPlayers.isEmpty()) return;
            AIPlayer ai = aiPlayers.get(RANDOM.nextInt(aiPlayers.size()));
            // 使用 idle 模板
            say(ai, "idle", Collections.emptyMap());
        }
    }

    // 处理假人之间的问好回复（由消息监听器调用）
    public static void onGreetingMessage(AIPlayer sender, String message, AIPlayer receiver) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableSpeech) return;
        UUID receiverId = receiver.getEntity().getUuid();
        int currentTick = (int) (System.currentTimeMillis() / 50); // 粗略估计 tick 数（1 tick = 50ms）
        // 冷却 60 秒（1200 tick）
        if (lastGreetingTick.containsKey(receiverId) && currentTick - lastGreetingTick.get(receiverId) < 1200) {
            return;
        }
        // 根据性格决定回复概率
        double prob = 0.5; // 中立型
        switch (receiver.getPersonality()) {
            case FUNNY: prob = 0.8; break;
            case SERIOUS: prob = 0.3; break;
            default: prob = 0.5;
        }
        if (RANDOM.nextDouble() > prob) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", sender.getEntity().getName().getString());
        String reply = SpeechLoader.getRandomMessage("greeting.reply", placeholders);
        if (reply == null || reply.equals("???")) reply = "你好！";
        String formatted = "§7[AI] " + receiver.getEntity().getName().getString() + "§r: " + reply;
        receiver.getEntity().getServer().getPlayerManager().broadcast(Text.literal(formatted), false);
        lastGreetingTick.put(receiverId, currentTick);
    }

    // 直接发送任意文本（用于命令等）
    public static void sendPlain(AIPlayer ai, String text) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableSpeech) return;
        String formatted = "§7[AI] " + ai.getEntity().getName().getString() + "§r: " + text;
        ai.getEntity().getServer().getPlayerManager().broadcast(Text.literal(formatted), false);
    }

    public static void init() {
        SpeechLoader.init();
    }
}