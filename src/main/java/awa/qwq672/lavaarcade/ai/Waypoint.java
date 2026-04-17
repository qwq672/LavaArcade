package awa.qwq672.lavaarcade.ai;

import net.minecraft.util.math.BlockPos;

public class Waypoint {
    public final int x, z;
    public final BlockPos blockPos;
    public boolean requiresJump = false;

    public Waypoint(int x, int z) {
        this.x = x;
        this.z = z;
        this.blockPos = new BlockPos(x, 0, z);
    }

    public Waypoint(BlockPos pos) {
        this.x = pos.getX();
        this.z = pos.getZ();
        this.blockPos = pos;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Waypoint)) return false;
        Waypoint other = (Waypoint) obj;
        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return 31 * x + z;
    }
}