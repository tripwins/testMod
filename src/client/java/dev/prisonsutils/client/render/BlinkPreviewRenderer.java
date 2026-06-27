package dev.prisonsutils.client.render;

import dev.prisonsutils.config.Config;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Draws a ghost marker where the Blink trinket ({@code trinket_hook}) would teleport you — a
 * wireframe box at the landing block, found by raycasting along your look vector up to the
 * trinket's {@code trinket_blink_range_blocks}. Lunar-safe (no mixins; standard world render event).
 */
public final class BlinkPreviewRenderer {
    private static final String BUKKIT = "PublicBukkitValues";
    private static final String NS = "cosmicprisons:";
    private static final String BLINK_ID = "trinket_hook";

    private BlinkPreviewRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BlinkPreviewRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        if (!Config.get().blinkPreviewEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        int range = blinkRange(player.getMainHandStack());
        if (range <= 0) return;

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(range));
        var hit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        BlockPos target = hit.getType() == HitResult.Type.MISS
                ? BlockPos.ofFloored(end)
                : hit.getBlockPos().offset(hit.getSide());

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        if (consumers == null || matrices == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        int color = Config.get().blinkPreviewColor;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) a = 0xFF;

        matrices.push();
        matrices.translate(target.getX() - cam.x, target.getY() - cam.y, target.getZ() - cam.z);
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        MatrixStack.Entry entry = matrices.peek();
        drawBox(vc, entry, r, g, b, a, 3.0f);
        matrices.pop();
    }

    private static int blinkRange(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return 0;
        NbtCompound bukkit = custom.copyNbt().getCompound(BUKKIT).orElse(null);
        if (bukkit == null) return 0;
        if (!BLINK_ID.equals(bukkit.getString(NS + "custom_item_id").orElse(""))) return 0;
        return bukkit.getInt(NS + "trinket_blink_range_blocks").orElse(22);
    }

    /** 12 edges of a unit cube, origin-local (the matrix is already translated to the block). */
    private static void drawBox(VertexConsumer vc, MatrixStack.Entry e,
                                int r, int g, int b, int a, float w) {
        float[][] c = {
                {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}, // bottom
                {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}  // top
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] ed : edges) {
            float[] p1 = c[ed[0]], p2 = c[ed[1]];
            float dx = p2[0] - p1[0], dy = p2[1] - p1[1], dz = p2[2] - p1[2];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) { dx /= len; dy /= len; dz /= len; }
            vc.vertex(e, p1[0], p1[1], p1[2]).color(r, g, b, a).normal(e, dx, dy, dz).lineWidth(w);
            vc.vertex(e, p2[0], p2[1], p2[2]).color(r, g, b, a).normal(e, dx, dy, dz).lineWidth(w);
        }
    }
}
