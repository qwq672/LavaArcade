package awa.qwq672.lavaarcade.game;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

public class GameScoreboard {
    private static final int SIDEBAR_SLOT = 1;
    private static ScoreboardObjective objective = null;

    public static void createObjective(MinecraftServer server, String displayName) {
        Scoreboard scoreboard = server.getScoreboard();
        if (objective != null) {
            scoreboard.removeObjective(objective);
            objective = null;
        }
        objective = scoreboard.addObjective(
                "lavaarcade_game",
                ScoreboardCriterion.DUMMY,
                Text.literal(displayName),
                ScoreboardCriterion.RenderType.INTEGER
        );
    }

    public static void setScore(MinecraftServer server, String playerName, int score) {
        if (objective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardPlayerScore scoreObj = scoreboard.getPlayerScore(playerName, objective);
        scoreObj.setScore(score);
    }

    public static void addScore(MinecraftServer server, String playerName, int delta) {
        if (objective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardPlayerScore scoreObj = scoreboard.getPlayerScore(playerName, objective);
        scoreObj.setScore(scoreObj.getScore() + delta);
    }

    public static void showToPlayer(ServerPlayerEntity player) {
        if (objective == null) return;
        if (player.getScoreboard().getObjectiveForSlot(SIDEBAR_SLOT) != objective) {
            player.getScoreboard().setObjectiveSlot(SIDEBAR_SLOT, objective);
        }
    }

    public static void hideFromPlayer(ServerPlayerEntity player) {
        if (objective == null) return;
        if (player.getScoreboard().getObjectiveForSlot(SIDEBAR_SLOT) == objective) {
            player.getScoreboard().setObjectiveSlot(SIDEBAR_SLOT, null);
        }
    }

    public static void showToAll(MinecraftServer server) {
        if (objective == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            showToPlayer(player);
        }
    }

    public static void hideFromAll(MinecraftServer server) {
        if (objective == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            hideFromPlayer(player);
        }
    }

    public static void removeObjective(MinecraftServer server) {
        if (objective == null) return;
        server.getScoreboard().removeObjective(objective);
        objective = null;
    }

    public static void updateScores(MinecraftServer server, Map<String, Integer> scores) {
        if (objective == null) return;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            setScore(server, entry.getKey(), entry.getValue());
        }
    }
}