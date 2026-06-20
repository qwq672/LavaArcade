package awa.qwq672.lavaarcade.ai;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIController {
    private static final Logger LOGGER = LoggerFactory.getLogger("AIController");
    private final ServerPlayerEntity bot;
    private final GameModeType mode;
    private final float threshold;
    private final int decisionInterval;
    private int tickCounter = 0;

    public AIController(ServerPlayerEntity bot, GameModeType mode) {
        this(bot, mode, 0.5f, 2);
    }

    public AIController(ServerPlayerEntity bot, GameModeType mode, float threshold, int interval) {
        this.bot = bot;
        this.mode = mode;
        this.threshold = threshold;
        this.decisionInterval = interval;
    }

    public void tick() {
        if (!ONNXInference.isLoaded()) return;
        if (++tickCounter % decisionInterval != 0) return;

        float[] state = AIStateExtractor.extractState(bot, mode);
        float[] action = ONNXInference.predict(state);
        if (action == null) return;
        applyActions(action);
    }

    private void applyActions(float[] action) {
        if (action.length < 8) return;
        boolean jump = action[0] > threshold;
        boolean sneak = action[1] > threshold;
        boolean attack = action[2] > threshold;
        boolean use = action[3] > threshold;
        boolean forward = action[4] > threshold;
        boolean back = action[5] > threshold;
        boolean left = action[6] > threshold;
        boolean right = action[7] > threshold;

        if (!(bot instanceof EntityPlayerMPFake)) return;
        EntityPlayerActionPack pack = ((ServerPlayerInterface) bot).getActionPack();

        pack.setForward(forward ? 1 : (back ? -1 : 0));
        pack.setStrafing(left ? -1 : (right ? 1 : 0));
        if (jump && bot.isOnGround()) {
            pack.start(ActionType.JUMP, Action.once());
        }
        pack.setSneaking(sneak);
        if (attack) {
            bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
        if (use) {
            bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }
}