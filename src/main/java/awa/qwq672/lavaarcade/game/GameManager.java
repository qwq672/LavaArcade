package awa.qwq672.lavaarcade.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("GameManager");
    private static final Path GAME_SCRIPTS_DIR = FabricLoader.getInstance().getGameDir()
            .resolve("LavaArcade").resolve("games");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, GameSession> activeSessions = new ConcurrentHashMap<>();
    private static final Map<String, String> scriptCache = new LinkedHashMap<>();
    private static boolean eventRegistered = false;

    public static void init() {
        try {
            Files.createDirectories(GAME_SCRIPTS_DIR);
        } catch (IOException e) {
            LOGGER.error("无法创建游戏脚本目录", e);
        }
        reloadScripts();
        registerDeathEvent();
    }

    public static void reloadScripts() {
        scriptCache.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(GAME_SCRIPTS_DIR, "*.json")) {
            for (Path p : stream) {
                String name = p.getFileName().toString().replace(".json", "");
                scriptCache.put(name, "external:" + p.toString());
            }
        } catch (IOException ignored) {}
        String[] builtinScripts = {"pvp_platform"};
        for (String name : builtinScripts) {
            if (!scriptCache.containsKey(name)) {
                String resourcePath = "/assets/lavaarcade/games/" + name + ".json";
                try (InputStream is = GameManager.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        scriptCache.put(name, "jar:" + resourcePath);
                    }
                } catch (IOException ignored) {}
            }
        }
        LOGGER.info("脚本扫描完成，找到 {} 个脚本", scriptCache.size());
    }

    public static GameScript loadScript(String scriptName) throws IOException {
        String source = scriptCache.get(scriptName);
        if (source == null) {
            throw new FileNotFoundException("脚本不存在: " + scriptName);
        }
        if (source.startsWith("external:")) {
            Path path = Paths.get(source.substring(9));
            try (Reader reader = Files.newBufferedReader(path)) {
                return GSON.fromJson(reader, GameScript.class);
            }
        } else if (source.startsWith("jar:")) {
            String resourcePath = source.substring(4);
            try (InputStream is = GameManager.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new FileNotFoundException("JAR 内资源不存在: " + resourcePath);
                }
                try (Reader reader = new InputStreamReader(is)) {
                    return GSON.fromJson(reader, GameScript.class);
                }
            }
        }
        throw new IOException("未知脚本来源: " + source);
    }

    public static Set<String> getAvailableScripts() {
        return scriptCache.keySet();
    }

    public static boolean hasActiveGame() {
        return !activeSessions.isEmpty();
    }

    public static void startGame(MinecraftServer server, String scriptName, List<ServerPlayerEntity> players) {
        if (hasActiveGame()) {
            broadcastError(server, "已有游戏正在进行中，请先停止");
            return;
        }
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
            LOGGER.error("启动游戏失败", e);
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
}