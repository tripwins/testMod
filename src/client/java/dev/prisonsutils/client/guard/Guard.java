package dev.prisonsutils.client.guard;

import net.minecraft.util.math.BlockPos;

public record Guard(BlockPos pos, float yaw) {
    /** Yaw normalized to [-180, 180]. */
    public float normalizedYaw() {
        float y = yaw % 360f;
        if (y > 180f) y -= 360f;
        if (y <= -180f) y += 360f;
        return y;
    }
}
