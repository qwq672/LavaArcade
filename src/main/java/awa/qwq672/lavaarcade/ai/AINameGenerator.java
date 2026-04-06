package awa.qwq672.lavaarcade.ai;

import java.util.Random;

public class AINameGenerator {
    private static final Random RANDOM = new Random();

    // ==================== 扩展词库（总计约320个词根） ====================
    // 动词 (40)
    private static final String[] VERBS = {
            "kill", "love", "play", "stop", "cry", "get", "win", "simp", "eat", "hunt",
            "slay", "farm", "mine", "craft", "build", "fight", "run", "fly", "jump", "rage",
            "smash", "crush", "blast", "burn", "freeze", "heal", "steal", "hide", "seek", "destroy",
            "create", "grow", "harvest", "tame", "ride", "swim", "climb", "dig", "explode", "loot"
    };
    // 名词 (60)
    private static final String[] NOUNS = {
            "cat", "dog", "fire", "storm", "shadow", "flame", "king", "lord", "beast", "gamer",
            "warrior", "knight", "dragon", "wolf", "fox", "bear", "creeper", "steve", "alex", "hero",
            "demon", "angel", "ghost", "phoenix", "titan", "giant", "wizard", "mage", "rogue", "hunter",
            "archer", "berserker", "paladin", "druid", "necromancer", "elemental", "golem", "skeleton", "zombie", "spider",
            "bat", "raven", "owl", "snake", "shark", "lion", "tiger", "eagle", "falcon", "hawk",
            "panda", "koala", "sloth", "otter", "fox", "lynx", "panther", "leopard", "cheetah", "hyena"
    };
    // 形容词 (50)
    private static final String[] ADJECTIVES = {
            "sad", "happy", "angry", "dark", "crazy", "dead", "sweet", "icy", "silent", "epic",
            "pro", "noob", "god", "king", "master", "cool", "wild", "fierce", "mighty", "swift",
            "brave", "loyal", "wise", "cruel", "vicious", "ferocious", "gentle", "calm", "stormy", "shining",
            "shadowy", "mystic", "ancient", "eternal", "infinite", "legendary", "mythic", "divine", "infernal", "celestial",
            "frozen", "burning", "thundering", "poisonous", "radiant", "gloomy", "chaotic", "orderly", "void", "light"
    };
    // 游戏术语 (40)
    private static final String[] GAME_TERMS = {
            "mc", "pvp", "bedwars", "craft", "mine", "block", "sword", "potato", "diamond", "nether",
            "ender", "creeper", "zombie", "skeleton", "piglin", "wither", "dragon", "elytra", "beacon", "redstone",
            "enchant", "anvil", "potion", "totem", "shulker", "trident", "crossbow", "shield", "axe", "pickaxe",
            "hoe", "shears", "fishing", "carrot", "golden", "apple", "chorus", "pearl", "obsidian", "quartz"
    };
    // 短名词/品牌词 (40)
    private static final String[] SHORT_WORDS = {
            "nexus", "vortex", "solar", "lunar", "nova", "echo", "flux", "zen", "aura", "rune",
            "zephyr", "ember", "frost", "spark", "blaze", "shade", "valor", "chaos", "prime", "zero",
            "alpha", "beta", "gamma", "delta", "omega", "sigma", "tau", "phi", "psi", "karma",
            "nimbus", "stratus", "cirrus", "cumulus", "tempest", "cyclone", "typhoon", "monsoon", "breeze", "gust"
    };
    // 情绪短语 (30)
    private static final String[] EMOTIONAL_PHRASES = {
            "plz", "lol", "omg", "wtf", "oof", "yay", "rip", "gg", "ez", "lmao",
            "dontcry", "nofear", "justwin", "getgood", "tooez", "whyme", "sadlife", "gameover", "nevergiveup", "lasttry",
            "plzhelp", "imsorry", "forgive", "revenge", "justice", "peace", "lovewins", "hate", "crybaby", "angrybird"
    };
    // 数字后缀库 (30)
    private static final String[] SUFFIX_NUMBERS = {
            "123", "007", "666", "777", "888", "999", "2024", "2025", "2026", "69", "420", "1337", "404", "101", "22",
            "11", "55", "88", "100", "1000", "2000", "3000", "5000", "9999", "12", "34", "56", "78", "90", "1234"
    };
    // 字母池 (26)
    private static final char[] LETTERS = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    // 前缀/后缀特殊字符 (用于战队风格)
    private static final String[] CLAN_PREFIXES = {
            "xX_", "Xx_", "_xX", "_Xx", "|", "//", "\\\\", "[]", "{}", "()"
    };
    private static final String[] CLAN_SUFFIXES = {
            "_Xx", "_xX", "Xx_", "xX_", "|", "//", "\\\\", "[]", "{}", "()"
    };

    // 风格权重总和100
    private static final int STYLE_CLASSIC = 10;      // 形容词+名词+数字
    private static final int STYLE_CREATIVE = 12;     // 驼峰或下划线组合
    private static final int STYLE_SHORT = 12;        // 4-6字母短名
    private static final int STYLE_UNDERSCORE = 12;   // 单词_单词[数字]
    private static final int STYLE_RANDOM = 10;       // 随机乱码/键盘行走
    private static final int STYLE_EMOTIONAL = 8;     // 短句/情绪词
    private static final int STYLE_REPEAT = 8;        // 叠字
    private static final int STYLE_SUFFIX = 10;       // 单词+数字后缀
    private static final int STYLE_CLAN = 8;          // 战队风格 xX_Name_Xx
    private static final int STYLE_INITIALS = 5;      // 首字母缩写
    // 随机大小写混合

    public static String generateName() {
        int r = RANDOM.nextInt(100);
        int sum = 0;
        if (r < (sum += STYLE_CLASSIC)) return generateClassic();
        if (r < (sum += STYLE_CREATIVE)) return generateCreative();
        if (r < (sum += STYLE_SHORT)) return generateShort();
        if (r < (sum += STYLE_UNDERSCORE)) return generateUnderscore();
        if (r < (sum += STYLE_RANDOM)) return generateRandom();
        if (r < (sum += STYLE_EMOTIONAL)) return generateEmotional();
        if (r < (sum += STYLE_REPEAT)) return generateRepeat();
        if (r < (sum += STYLE_SUFFIX)) return generateSuffix();
        if (r < (sum += STYLE_CLAN)) return generateClan();
        if (r < sum + STYLE_INITIALS) return generateInitials();
        return generateMixedCase(); // STYLE_MIXEDCASE
    }

    // 古典风格：形容词+名词+数字（可选）
    private static String generateClassic() {
        String adj = randomCapitalized(ADJECTIVES);
        String noun = randomCapitalized(NOUNS);
        boolean addNumber = RANDOM.nextBoolean();
        if (addNumber) {
            return adj + noun + randomNumber(1, 999);
        } else {
            return adj + noun;
        }
    }

    // 创意风格：驼峰式或下划线组合，可能带游戏术语
    private static String generateCreative() {
        int type = RANDOM.nextInt(4);
        return switch (type) {
            case 0 -> // 动词+名词
                    capitalize(VERBS[RANDOM.nextInt(VERBS.length)]) + capitalize(NOUNS[RANDOM.nextInt(NOUNS.length)]);
            case 1 -> // 形容词+游戏术语
                    capitalize(ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)]) + capitalize(GAME_TERMS[RANDOM.nextInt(GAME_TERMS.length)]);
            case 2 -> // 名词_名词
                    randomLower(NOUNS) + "_" + randomLower(NOUNS);
            default -> // 游戏术语+数字
                    randomLower(GAME_TERMS) + randomNumber(10, 99);
        };
    }

    // 短名风格：4-6字母，全小写，避免常见词
    private static String generateShort() {
        int len = 4 + RANDOM.nextInt(3); // 4-6
        StringBuilder sb = new StringBuilder();
        boolean vowelLast = RANDOM.nextBoolean();
        for (int i = 0; i < len; i++) {
            char c = LETTERS[RANDOM.nextInt(LETTERS.length)];
            // 简单的元音/辅音交替提升可读性
            if (vowelLast && isVowel(c)) {
                c = LETTERS[RANDOM.nextInt(LETTERS.length)];
            }
            sb.append(c);
            vowelLast = isVowel(c);
        }
        return sb.toString().toLowerCase();
    }

    // 下划线风格：单词_单词 或 单词_单词_数字
    private static String generateUnderscore() {
        int type = RANDOM.nextInt(3);
        String first = randomLower(NOUNS);
        String second = randomLower(ADJECTIVES);
        String third = randomLower(GAME_TERMS);
        return switch (type) {
            case 0 -> first + "_" + second;
            case 1 -> first + "_" + second + "_" + randomNumber(10, 99);
            default -> first + "_" + third;
        };
    }

    // 随机乱码/键盘行走风格
    private static String generateRandom() {
        int type = RANDOM.nextInt(4);
        switch (type) {
            case 0: // 纯数字+字母混合，如 3ziwz
                int len = 5 + RANDOM.nextInt(4); // 5-8
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    if (RANDOM.nextBoolean()) {
                        sb.append(RANDOM.nextInt(10));
                    } else {
                        sb.append(LETTERS[RANDOM.nextInt(LETTERS.length)]);
                    }
                }
                return sb.toString();
            case 1: // 键盘行：qwert, asdfg, zxcvb 变体
                String[] rows = {"qwert", "asdfg", "zxcvb", "12345", "qwerty", "asdfgh", "zxcvbn"};
                String base = rows[RANDOM.nextInt(rows.length)];
                if (RANDOM.nextBoolean()) {
                    return base.substring(0, 3 + RANDOM.nextInt(3));
                } else {
                    return base + (RANDOM.nextBoolean() ? "0" : "9");
                }
            case 2: // 乱码带下划线如 G0LD_oVZGK_01_26
                return randomUppercase(3) + randomNumber(10, 99) + "_" + randomUppercase(5) + "_" + randomNumber(10, 99);
            default: // 纯数字串
                return String.valueOf(100000 + RANDOM.nextInt(900000));
        }
    }

    // 情绪短语风格
    private static String generateEmotional() {
        int type = RANDOM.nextInt(4);
        return switch (type) {
            case 0 -> {
                String[] phrases = {"dontcry", "plzwin", "getgood", "justdie", "nofear", "sadboy", "happygirl", "laughing", "whyso", "icrievrtim"};
                yield phrases[RANDOM.nextInt(phrases.length)] + (RANDOM.nextBoolean() ? randomNumber(10, 99) : "");
            }
            case 1 -> // 情绪词+数字
                    EMOTIONAL_PHRASES[RANDOM.nextInt(EMOTIONAL_PHRASES.length)] + randomNumber(1, 999);
            case 2 -> // 否定+动词
                    (RANDOM.nextBoolean() ? "not" : "no") + VERBS[RANDOM.nextInt(VERBS.length)];
            default -> {
                String[] interj = {"ahh", "ohh", "eek", "wow", "yay", "boo", "meh", "hmm"};
                yield interj[RANDOM.nextInt(interj.length)] + randomNumber(10, 99);
            }
        };
    }

    // 叠字风格
    private static String generateRepeat() {
        String word = SHORT_WORDS[RANDOM.nextInt(SHORT_WORDS.length)];
        int repeat = 2 + RANDOM.nextInt(2); // 2 or 3 times
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(word).repeat(repeat));
        if (RANDOM.nextBoolean()) {
            sb.append(randomNumber(10, 99));
        }
        // 限制长度不超过16
        String result = sb.toString();
        if (result.length() > 16) {
            result = result.substring(0, 16);
        }
        return result;
    }

    // 单词+数字后缀风格
    private static String generateSuffix() {
        String base;
        int subType = RANDOM.nextInt(5);
        base = switch (subType) {
            case 0 -> randomLower(NOUNS);
            case 1 -> randomLower(ADJECTIVES);
            case 2 -> randomLower(GAME_TERMS);
            case 3 -> randomLower(SHORT_WORDS);
            default -> randomLower(VERBS);
        };
        String suffix = SUFFIX_NUMBERS[RANDOM.nextInt(SUFFIX_NUMBERS.length)];
        if (RANDOM.nextBoolean()) {
            return base + "_" + suffix;
        } else {
            return base + suffix;
        }
    }

    // 战队风格：xX_Name_Xx 或类似
    private static String generateClan() {
        String prefix = CLAN_PREFIXES[RANDOM.nextInt(CLAN_PREFIXES.length)];
        String suffix = CLAN_SUFFIXES[RANDOM.nextInt(CLAN_SUFFIXES.length)];
        String name;
        int type = RANDOM.nextInt(3);
        if (type == 0) {
            name = randomCapitalized(ADJECTIVES) + randomCapitalized(NOUNS);
        } else if (type == 1) {
            name = randomLower(NOUNS) + randomNumber(1, 999);
        } else {
            name = randomUppercase(3) + randomNumber(10, 99);
        }
        // 避免过长
        String result = prefix + name + suffix;
        if (result.length() > 16) {
            result = result.substring(0, 16);
        }
        return result;
    }

    // 首字母缩写风格，如 IDKFA, OMW
    private static String generateInitials() {
        int len = 3 + RANDOM.nextInt(4); // 3-6
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) ('A' + RANDOM.nextInt(26)));
        }
        if (RANDOM.nextBoolean()) {
            sb.append(randomNumber(10, 99));
        }
        return sb.toString();
    }

    // 随机大小写混合风格，如 LiKeThIs
    private static String generateMixedCase() {
        String base;
        int type = RANDOM.nextInt(4);
        base = switch (type) {
            case 0 -> randomLower(NOUNS);
            case 1 -> randomLower(ADJECTIVES);
            case 2 -> randomLower(GAME_TERMS);
            default -> randomLower(SHORT_WORDS);
        };
        StringBuilder sb = new StringBuilder();
        boolean upper = RANDOM.nextBoolean();
        for (char c : base.toCharArray()) {
            if (upper) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
            upper = !upper;
        }
        if (RANDOM.nextBoolean()) {
            sb.append(randomNumber(10, 99));
        }
        return sb.toString();
    }

    // ========== 辅助方法 ==========
    private static String randomCapitalized(String[] arr) {
        String s = arr[RANDOM.nextInt(arr.length)];
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static String randomLower(String[] arr) {
        return arr[RANDOM.nextInt(arr.length)].toLowerCase();
    }

    private static String randomUppercase(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + RANDOM.nextInt(26)));
        }
        return sb.toString();
    }

    private static String randomNumber(int min, int max) {
        return String.valueOf(min + RANDOM.nextInt(max - min + 1));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static boolean isVowel(char c) {
        c = Character.toLowerCase(c);
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}