package awa.qwq672.lavaarcade.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static final Path GAME_SCRIPTS_DIR = Paths.get("config", "lavaarcade", "games");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, GameSession> activeSessions = new ConcurrentHashMap<>();
    private static boolean eventRegistered = false;

    public static void init() {
        try {
            Files.createDirectories(GAME_SCRIPTS_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
        registerDeathEvent();
    }

    private static void registerDeathEvent() {
        if (eventRegistered) return;
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity && source.getAttacker() instanceof ServerPlayerEntity) {
                ServerPlayerEntity victim = (ServerPlayerEntity) entity;
                ServerPlayerEntity killer = (ServerPlayerEntity) source.getAttacker();
                recordKill(killer, victim);
            }
        });
        eventRegistered = true;
    }

    public static GameScript loadScript(String scriptName) throws IOException {
        Path scriptFile = GAME_SCRIPTS_DIR.resolve(scriptName + ".json");
        if (!Files.exists(scriptFile)) {
            throw new FileNotFoundException("脚本不存在: " + scriptName);
        }
        try (Reader reader = Files.newBufferedReader(scriptFile)) {
            return GSON.fromJson(reader, GameScript.class);
        }
    }

    public static void startGame(MinecraftServer server, String scriptName, List<ServerPlayerEntity> players) {
        try {
            GameScript script = loadScript(scriptName);
            if (players.size() < script.minPlayers) {
                broadcastError(server, "玩家不足，需要至少 " + script.minPlayers + " 人");
                return;
            }
            if (players.size() > script.maxPlayers) {
                broadcastError(server, "玩家过多，最多 " + script.maxPlayers + " 人");
                return;
            }
            GameSession session = new GameSession(server, script, players);
            activeSessions.put(session.id, session);
        } catch (Exception e) {
            e.printStackTrace();
            broadcastError(server, "启动游戏失败: " + e.getMessage());
        }
    }

    public static void stopGame(MinecraftServer server, UUID gameId) {
        GameSession session = activeSessions.remove(gameId);
        if (session != null) {
            session.endGame(null);
        } else {
            broadcastError(server, "没有进行中的游戏");
        }
    }

    public static void tick(MinecraftServer server) {
        for (GameSession session : activeSessions.values()) {
            session.tick();
        }
    }

    public static void recordKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        for (GameSession session : activeSessions.values()) {
            if (session.getPlayers().contains(killer) && session.getPlayers().contains(victim)) {
                session.recordKill(killer, victim);
                break;
            }
        }
    }

    public static boolean isPlayerInGame(ServerPlayerEntity player) {
        return activeSessions.values().stream().anyMatch(s -> s.getPlayers().contains(player) && !s.getEliminated().contains(player));
    }

    public static boolean isPlayerEliminated(ServerPlayerEntity player) {
        for (GameSession session : activeSessions.values()) {
            if (session.getPlayers().contains(player) && session.getEliminated().contains(player)) {
                return true;
            }
        }
        return false;
    }

    private static void broadcastError(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.literal("§c[游戏] " + message), false);
    }

    public static void removeSession(UUID id) {
        activeSessions.remove(id);
    }

    public static Collection<GameSession> getActiveSessions() {
        return activeSessions.values();
    }
}