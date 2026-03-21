package awa.qwq672.lavaarcade.ai;

import java.util.Random;

public class AINameGenerator {
    private static final Random random = new Random();

    // 前缀（多种风格）
    private static final String[] PREFIXES = {
            "Dark", "Shadow", "Ice", "Fire", "Thunder", "Night", "Silent", "Crazy", "Cool", "Epic", "Pro", "Noob", "God", "King", "Master",
            "Lone", "Frost", "Storm", "Blaze", "Crimson", "Azure", "Ember", "Echo", "Raven", "Wolf", "Fox", "Phoenix", "Dragon", "Tiger",
            "Savage", "Mystic", "Cursed", "Sacred", "Void", "Chaos", "Nova", "Orion", "Sirius", "Vega"
    };

    // 后缀（多种）
    private static final String[] SUFFIXES = {
            "Creeper", "Steve", "Alex", "Warrior", "Knight", "Hunter", "Slayer", "Gamer", "Lord", "Dragon", "Wolf", "Fox", "Bear",
            "Miner", "Builder", "Explorer", "Ranger", "Mage", "Wizard", "Assassin", "Berserker", "Paladin", "Rogue", "Druid",
            "Viking", "Samurai", "Ninja", "Pirate", "Vampire", "Werewolf", "Angel", "Demon", "Ghost", "Spirit"
    };

    // 数字后缀
    private static final String[] NUMBERS = {
            "123", "007", "666", "777", "888", "999", "2024", "X", "XD", "MC",
            "42", "69", "420", "1337", "9000", "1000", "2000", "3000", "5000", "10000"
    };

    // 英文单词（用于组合）
    private static final String[] WORDS = {
            "Craft", "Mine", "Block", "Stone", "Iron", "Gold", "Diamond", "Nether", "End", "Dragon",
            "Sword", "Shield", "Armor", "Pickaxe", "Axe", "Bow", "Arrow", "TNT", "Redstone", "Lava"
    };

    // 中文拼音（风格）
    private static final String[] CHINESE = {
            "Zhang", "Wang", "Li", "Zhao", "Chen", "Lin", "Huang", "Liu", "Xu", "Sun",
            "Ming", "Wei", "Qiang", "Tao", "Lei", "Feng", "Long", "Hu", "Ying", "Mei"
    };

    public static String generateName() {
        int style = random.nextInt(8); // 增加风格数量
        switch (style) {
            case 0: return getRandom(PREFIXES) + getRandom(SUFFIXES);
            case 1: return getRandom(PREFIXES) + "_" + getRandom(NUMBERS);
            case 2: return getRandom(SUFFIXES) + "_" + (random.nextInt(9000) + 1000);
            case 3: return "xX_" + getRandom(PREFIXES) + "_Xx";
            case 4: return getRandom(PREFIXES) + getRandom(SUFFIXES) + random.nextInt(10);
            case 5: return getRandom(WORDS) + getRandom(SUFFIXES); // 如 "DiamondHunter"
            case 6: return getRandom(CHINESE) + getRandom(NUMBERS); // 中文拼音+数字
            case 7: return getRandom(PREFIXES).toLowerCase() + getRandom(SUFFIXES).toLowerCase(); // 全小写
            default: return getRandom(SUFFIXES) + random.nextInt(100);
        }
    }

    private static String getRandom(String[] array) {
        return array[random.nextInt(array.length)];
    }
}