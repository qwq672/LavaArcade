package awa.qwq672.lavaarcade.ai;

import awa.qwq672.lavaarcade.ai.AIPlayer;
import awa.qwq672.lavaarcade.ai.AIConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class SpeechManager {
    private static final Random RANDOM = new Random();
    private static final List<String> DEFAULT_MESSAGES = List.of(
            "有人吗？", "这天气不错。", "一起挖矿吗？", "我好无聊。", "大家好！"
    );
    private static int tickCounter = 0;

    public static void tick(MinecraftServer server, List<AIPlayer> aiPlayers) {
        AIConfig.ConfigData config = AIConfig.getConfig();
        if (!config.enableSpeech) return;

        tickCounter++;
        if (tickCounter >= 600) { // 30秒
            tickCounter = 0;
            if (aiPlayers.isEmpty()) return;
            AIPlayer ai = aiPlayers.get(RANDOM.nextInt(aiPlayers.size()));
            String msg = DEFAULT_MESSAGES.get(RANDOM.nextInt(DEFAULT_MESSAGES.size()));
            ai.getEntity().sendMessage(Text.literal("§7[AI] " + ai.getEntity().getName().getString() + "§r: " + msg));
        }
    }
}