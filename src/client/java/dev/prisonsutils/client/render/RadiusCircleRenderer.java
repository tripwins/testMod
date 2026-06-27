package dev.prisonsutils.client.render;

import dev.prisonsutils.client.util.HeldItemAnalyzer;
import dev.prisonsutils.config.Config;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Draws a pet's radius (parsed from lore by {@link HeldItemAnalyzer}) as a ground ring that
 * climbs up and over blocks in the way — squared off as right-angle steps (a horizontal run then
 * a vertical riser, never sloped) — and never drops into holes below the player's feet. Two
 * vertical rings show the sphere extent. Color/scale come from config.
 */
public final class RadiusCircleRenderer {
    private static final int SEGMENTS = 64;
    private static final float Y_OFFSET = 0.05f;
    private static final int CLIMB_MAX = 4;     // how high the ring will climb over a wall

    private RadiusCircleRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RadiusCircleRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        if (!Config.get().petRadiusEnabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        Float detected = HeldItemAnalyzer.radiusFor(player.getMainHandStack());
        if (detected == null) {
            return;
        }
        float radius = detected;
        float width = Math.max(1.0f, Math.min(20.0f, Config.get().petRadiusThickness));

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        if (consumers == null || matrices == null) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        // Subtle "someone's inside" alert: if any other player is within the radius, pulse the ring
        // in the alert color. We never draw anything on the intruder, so their position isn't revealed.
        boolean intruder = Config.get().petRadiusAlertEnabled && otherPlayerInRadius(world, player, px, py, pz, radius);

        int color = intruder ? Config.get().petRadiusAlertColor : Config.get().petRadiusColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) a = 0xFF;
        if (intruder) {
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0);
            a = (int) (110 + 145 * pulse); // 110..255
        }

        matrices.push();
        matrices.translate(px - cam.x, py - cam.y, pz - cam.z);
        VertexConsumer consumer = consumers.getBuffer(RenderLayers.lines());
        MatrixStack.Entry entry = matrices.peek();

        // Ground ring: y at each point climbs over terrain but never drops into holes (relative to feet).
        float[] lx = new float[SEGMENTS];
        float[] ly = new float[SEGMENTS];
        float[] lz = new float[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            double ang = 2.0 * Math.PI * i / SEGMENTS;
            float cx = (float) (Math.cos(ang) * radius);
            float cz = (float) (Math.sin(ang) * radius);
            lx[i] = cx;
            lz[i] = cz;
            ly[i] = (float) (surfaceY(world, px + cx, pz + cz, py) - py) + Y_OFFSET;
        }
        // Right-angle steps: a horizontal run at each point's height, then a vertical riser to the
        // next point's height. No diagonal segments — the ring steps up/down squarely over blocks.
        for (int i = 0; i < SEGMENTS; i++) {
            int j = (i + 1) % SEGMENTS;
            emit(consumer, entry, lx[i], ly[i], lz[i], lx[j], ly[i], lz[j], r, g, b, a, width);
            if (ly[j] != ly[i]) {
                emit(consumer, entry, lx[j], ly[i], lz[j], lx[j], ly[j], lz[j], r, g, b, a, width);
            }
        }

        // Vertical rings (XY and ZY planes), centered on the player.
        ring(consumer, entry, radius, true, r, g, b, a, width);
        ring(consumer, entry, radius, false, r, g, b, a, width);

        matrices.pop();
    }

    /** True if any player other than {@code self} is within {@code radius} (3D) of the centre. */
    private static boolean otherPlayerInRadius(ClientWorld world, ClientPlayerEntity self,
                                               double px, double py, double pz, float radius) {
        double r2 = radius * radius;
        for (net.minecraft.entity.player.PlayerEntity other : world.getPlayers()) {
            if (other == self || other.isSpectator()) continue;
            double dx = other.getX() - px;
            double dy = other.getY() - py;
            double dz = other.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz <= r2) return true;
        }
        return false;
    }

    /**
     * Ground height at the column under (wx,wz), measured from the player's feet. If the feet-level
     * block is solid (a wall/step we're walking into), climb to the top of that solid stack, capped
     * at CLIMB_MAX. Otherwise stay level with the feet — the ring only ever climbs up, never drops
     * into holes or follows ledges down. Only blocks with an actual collision shape count as ground,
     * so grass, flowers and other non-collidable "transparent" blocks don't push the ring up.
     */
    private static double surfaceY(ClientWorld world, double wx, double wz, double feetY) {
        int bx = (int) Math.floor(wx);
        int bz = (int) Math.floor(wz);
        int feet = (int) Math.floor(feetY);
        BlockPos.Mutable m = new BlockPos.Mutable();

        if (isGround(world, m.set(bx, feet, bz))) {
            // Wall/step: climb to the top of the solid stack (first non-ground block above feet).
            for (int y = feet + 1; y <= feet + CLIMB_MAX; y++) {
                if (!isGround(world, m.set(bx, y, bz))) {
                    return y;
                }
            }
            return feet + CLIMB_MAX + 1.0;
        }
        // Open column at feet: hold the feet height (never drop into pits or over ledges).
        return feet;
    }

    /** A block counts as ground only if it's not air/fluid and has a real collision shape. */
    private static boolean isGround(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    private static void ring(VertexConsumer consumer, MatrixStack.Entry entry, float radius,
                             boolean xyPlane, int r, int g, int b, int a, float width) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = 2.0 * Math.PI * i / SEGMENTS;
            double a2 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
            float c1 = (float) (Math.cos(a1) * radius);
            float s1 = (float) (Math.sin(a1) * radius);
            float c2 = (float) (Math.cos(a2) * radius);
            float s2 = (float) (Math.sin(a2) * radius);
            if (xyPlane) {
                emit(consumer, entry, c1, s1, 0f, c2, s2, 0f, r, g, b, a, width);
            } else {
                emit(consumer, entry, 0f, s1, c1, 0f, s2, c2, r, g, b, a, width);
            }
        }
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0f) { dx /= len; dy /= len; dz /= len; }
        consumer.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, dx, dy, dz).lineWidth(width);
        consumer.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, dx, dy, dz).lineWidth(width);
    }
}
