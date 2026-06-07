package awa.qwq672.lavaarcade.game;

import net.minecraft.util.math.BlockPos;
import java.util.List;

public class GameScript {
    public String name;
    public String type;
    public Region region;
    public List<BlockPos> spawnPoints;
    public EliminationRule eliminationRule;
    public WinCondition winCondition;
    public ScoreboardConfig scoreboard;
    public int timeLimit;
    public GameGear gameGear;
    public boolean invincibleInside = true;
    public boolean knockbackOnHit = true;
    public boolean fallDamage = false;
    public boolean allowSprint = true;
    public boolean allowSneak = true;
    public boolean respawnAfterElimination = false;
    public boolean healthRegen = false;
    public List<String> startCommands;
    public List<String> endCommands;
    public String onEliminate;
    public String onKill;
    public int minPlayers = 2;
    public int maxPlayers = 8;

    public static class Region {
        public String shape;
        public BlockPos from;
        public BlockPos to;
        public BlockPos center;
        public double radius;
    }

    public static class EliminationRule {
        public String condition;
        public String message;
    }

    public static class WinCondition {
        public String type;
        public int value;
    }

    public static class ScoreboardConfig {
        public String title;
        public int initialValue;
    }

    public static class GameGear {
        public boolean clearInventory;
        public List<ItemStackConfig> items;
    }

    public static class ItemStackConfig {
        public String item;
        public int count;
        public int slot = -1;
    }
}