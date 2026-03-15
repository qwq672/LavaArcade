package awa.qwq672.lavaarcade.ai;

import java.util.Random;

public class AINameGenerator {
    private static final Random random = new Random();

    private static final String[] PREFIXES = {"Dark", "Shadow", "Ice", "Fire", "Thunder", "Night", "Silent", "Crazy", "Cool", "Epic", "Pro", "Noob", "God", "King", "Master"};
    private static final String[] SUFFIXES = {"Creeper", "Steve", "Alex", "Warrior", "Knight", "Hunter", "Slayer", "Gamer", "Lord", "Dragon", "Wolf", "Fox", "Bear"};
    private static final String[] NUMBERS = {"123", "007", "666", "777", "888", "999", "2024", "X", "XD", "MC"};

    public static String generateName() {
        int style = random.nextInt(5);
        switch (style) {
            case 0: return getRandom(PREFIXES) + getRandom(SUFFIXES);
            case 1: return getRandom(PREFIXES) + "_" + getRandom(NUMBERS);
            case 2: return getRandom(SUFFIXES) + "_" + (random.nextInt(9000) + 1000);
            case 3: return "xX_" + getRandom(PREFIXES) + "_Xx";
            case 4: return getRandom(PREFIXES) + getRandom(SUFFIXES) + random.nextInt(10);
            default: return getRandom(SUFFIXES) + random.nextInt(100);
        }
    }

    private static String getRandom(String[] array) {
        return array[random.nextInt(array.length)];
    }
}