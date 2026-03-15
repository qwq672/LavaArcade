package awa.qwq672.lavaarcade.ai;

import java.util.Random;

public enum AIPersonality {
    FUNNY("整活型"),
    SERIOUS("严肃型"),
    NEUTRAL("中立型");

    public final String displayName;

    AIPersonality(String displayName) {
        this.displayName = displayName;
    }

    public static AIPersonality random() {
        Random rand = new Random();
        int val = rand.nextInt(100);
        if (val < 30) return FUNNY;
        if (val < 60) return SERIOUS;
        return NEUTRAL;
    }
}