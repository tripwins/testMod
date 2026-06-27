package dev.prisonsutils.client.render;

import dev.prisonsutils.client.ping.PingManager;
import dev.prisonsutils.client.ping.PingManager.Ping;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws active {@link PingManager} markers in the world: a slim translucent beacon-style column
 * (extending both up and down through the marked point), a sonar ring that repeatedly expands and
 * fades, a small fixed halo, and a billboarded name label on a colored background. The live preview
 * (while the key is held) renders at full opacity; committed pings
 * fade out over the last fifth of their life.
 */
public final class PingRenderer {
    private static final int RING_SEGMENTS = 48;
    private static final float BEAM_EXTENT = 256.0f;  // beam reaches this far up AND down
    private static final float BEAM_CORE_HALF = 0.06f; // bright inner column half-width (blocks)
    private static final float BEAM_GLOW_HALF = 0.16f; // faint outer glow half-width (blocks)
    private static final int BEAM_CORE_ALPHA = 140;    // translucent core, scaled by lifetime fade
    private static final int BEAM_GLOW_ALPHA = 45;     // fainter outer glow
    private static final float RING_WIDTH = 3.0f;
    private static final float HALO_RADIUS = 0.5f;
    private static final float PULSE_PERIOD_MS = 1200.0f;
    private static final float PULSE_MAX_RADIUS = 2.4f;
    private static final float RING_Y = 0.06f;
    private static final float LABEL_SCALE = 0.025f;
    private static final float LABEL_HEIGHT = 1.6f;   // metres above the marked point
    private static final float LABEL_REF_DIST = 16.0f;  // distance at which the label sits at LABEL_SCALE
    private static final float LABEL_MAX_FACTOR = 8.0f; // cap: label never grows past this ×LABEL_SCALE

    private PingRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PingRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        List<Ping> pings = PingManager.pings();
        Ping preview = PingManager.preview();
        if (pings.isEmpty() && preview == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        MatrixStack matrices = context.matrices();
        if (player == null || matrices == null) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        TextRenderer font = client.textRenderer;
        long now = System.currentTimeMillis();
        VertexConsumerProvider.Immediate immediate =
                client.getBufferBuilders().getEntityVertexConsumers();

        for (int i = 0; i < pings.size(); i++) {
            Ping ping = pings.get(i);
            long age = now - ping.createdAt();
            float life = age / (float) PingManager.LIFETIME_MS;
            if (life > 1.0f) continue;
            float fade = life > 0.8f ? 1.0f - (life - 0.8f) / 0.2f : 1.0f;
            renderOne(matrices, immediate, font, camera, cam, ping, age, fade);
        }
        if (preview != null) {
            renderOne(matrices, immediate, font, camera, cam,
                    preview, now - preview.createdAt(), 1.0f);
        }
        immediate.draw();
    }

    private static void renderOne(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                  TextRenderer font, Camera camera, Vec3d cam,
                                  Ping ping, long age, float fade) {
        int color = ping.color();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int baseA = (color >>> 24) & 0xFF;
        if (baseA == 0) baseA = 0xFF;
        int a = (int) (baseA * fade);
        if (a <= 0) return;

        Vec3d p = ping.pos();
        double bx = p.x - cam.x;
        double by = p.y - cam.y;
        double bz = p.z - cam.z;

        // Keep far pings visible: pull anything past the view frustum in to just inside it, along the
        // camera→ping line, so it never clips out. The label still shows the true distance.
        double dist = Math.sqrt(bx * bx + by * by + bz * bz);
        double maxDist = renderClampDistance();
        double renderDist = dist;
        if (dist > maxDist && dist > 1.0e-3) {
            double s = maxDist / dist;
            bx *= s; by *= s; bz *= s;
            renderDist = maxDist;
        }

        matrices.push();
        matrices.translate(bx, by, bz);
        MatrixStack.Entry entry = matrices.peek();

        // Slim translucent beacon-style column through the marked point: faint outer glow + core.
        VertexConsumer beam = immediate.getBuffer(RenderLayers.debugFilledBox());
        beamColumn(beam, entry, BEAM_GLOW_HALF, r, g, b, (int) (BEAM_GLOW_ALPHA * fade));
        beamColumn(beam, entry, BEAM_CORE_HALF, r, g, b, (int) (BEAM_CORE_ALPHA * fade));

        // Sonar pulse: a ground ring whose radius grows over each period and fades as it expands.
        VertexConsumer lines = immediate.getBuffer(RenderLayers.lines());
        float pulse = (age % (long) PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
        ring(lines, entry, pulse * PULSE_MAX_RADIUS, r, g, b, (int) (a * (1.0f - pulse)), RING_WIDTH);

        // Fixed halo marking the exact spot.
        ring(lines, entry, HALO_RADIUS, r, g, b, a, RING_WIDTH);

        matrices.pop();

        drawLabel(matrices, immediate, font, camera, bx, by + LABEL_HEIGHT, bz,
                ping.name(), color, fade, labelScale(renderDist));
    }

    private static void drawLabel(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                  TextRenderer font, Camera camera, double x, double y, double z,
                                  String name, int color, float fade, float scale) {
        int textA = (int) (255 * fade);
        if (textA <= 4) return;
        int textArgb = (textA << 24) | 0xFFFFFF;            // white text, faded
        int bgArgb = ((int) (0xE6 * fade) << 24) | (color & 0xFFFFFF); // mostly-opaque colored box

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(new Quaternionf().fromAxisAngleDeg(0f, 1f, 0f, -camera.getYaw()));
        matrices.multiply(new Quaternionf().fromAxisAngleDeg(1f, 0f, 0f, camera.getPitch()));
        matrices.scale(-scale, -scale, scale);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float w1 = font.getWidth(name);
        font.draw(name, -w1 / 2f, 0f, textArgb, false, matrix, immediate,
                TextRenderer.TextLayerType.SEE_THROUGH, bgArgb, 0xF000F0);
        matrices.pop();
    }

    /** Distance (blocks) to pull far pings in to, kept just inside the render-distance far plane. */
    private static double renderClampDistance() {
        int chunks = MinecraftClient.getInstance().options.getViewDistance().getValue();
        return Math.max(32.0, chunks * 16 * 0.9);
    }

    /** Label world-scale: grows with distance to keep on-screen size roughly constant, then capped. */
    private static float labelScale(double renderDist) {
        float scale = (float) (LABEL_SCALE * Math.max(1.0, renderDist / LABEL_REF_DIST));
        return Math.min(scale, LABEL_SCALE * LABEL_MAX_FACTOR);
    }

    private static void ring(VertexConsumer c, MatrixStack.Entry e, float radius,
                             int r, int g, int b, int a, float width) {
        if (a <= 0 || radius <= 0.001f) return;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double a2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            float x2 = (float) (Math.cos(a2) * radius);
            float z2 = (float) (Math.sin(a2) * radius);
            emit(c, e, x1, RING_Y, z1, x2, RING_Y, z2, r, g, b, a, width);
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

    /** A thin vertical square column ({@code ±BEAM_EXTENT} tall) of translucent {@code debugFilledBox} quads. */
    private static void beamColumn(VertexConsumer c, MatrixStack.Entry e, float hw,
                                   int r, int g, int b, int a) {
        if (a <= 0) return;
        float lo = -BEAM_EXTENT, hi = BEAM_EXTENT;
        quad(c, e, -hw, lo, -hw, -hw, lo, hw, -hw, hi, hw, -hw, hi, -hw, r, g, b, a); // -X face
        quad(c, e, hw, lo, hw, hw, lo, -hw, hw, hi, -hw, hw, hi, hw, r, g, b, a);     // +X face
        quad(c, e, hw, lo, -hw, -hw, lo, -hw, -hw, hi, -hw, hw, hi, -hw, r, g, b, a); // -Z face
        quad(c, e, -hw, lo, hw, hw, lo, hw, hw, hi, hw, -hw, hi, hw, r, g, b, a);     // +Z face
    }

    /** Emits a POSITION_COLOR quad both ways, so back-face culling never hides the slim column. */
    private static void quad(VertexConsumer c, MatrixStack.Entry e,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        c.vertex(e, x1, y1, z1).color(r, g, b, a);
        c.vertex(e, x2, y2, z2).color(r, g, b, a);
        c.vertex(e, x3, y3, z3).color(r, g, b, a);
        c.vertex(e, x4, y4, z4).color(r, g, b, a);
        c.vertex(e, x4, y4, z4).color(r, g, b, a);
        c.vertex(e, x3, y3, z3).color(r, g, b, a);
        c.vertex(e, x2, y2, z2).color(r, g, b, a);
        c.vertex(e, x1, y1, z1).color(r, g, b, a);
    }
}
