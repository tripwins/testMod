package dev.prisonsutils.client.damage;

import dev.prisonsutils.config.Config;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Floating damage numbers above entities you hit. Shows one decimal. */
public final class DamageIndicatorManager {
    private static final List<DamageEntry> ENTRIES = new ArrayList<>();
    private static final java.util.Random RNG = new java.util.Random();
    private static final long LIFETIME_MS = 1500;

    private DamageIndicatorManager() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(DamageIndicatorManager::onRender);
    }

    public static void addIndicator(LivingEntity entity, double amount, boolean isCritical, boolean isPlayer) {
        if (!Config.get().damageIndicatorsEnabled) return;
        ENTRIES.add(new DamageEntry(entity, amount, isCritical, isPlayer));
    }

    private static void onRender(WorldRenderContext context) {
        if (ENTRIES.isEmpty()) return;
        if (!Config.get().damageIndicatorsEnabled) { ENTRIES.clear(); return; }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        TextRenderer font = client.textRenderer;
        long now = System.currentTimeMillis();
        VertexConsumerProvider.Immediate immediate =
                client.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = context.matrices();
        if (matrices == null) return;

        ENTRIES.removeIf(DamageEntry::isExpired);

        for (DamageEntry entry : ENTRIES) {
            long elapsed = now - entry.startTime;
            float elapsedSeconds = elapsed / 1000.0f;
            float verticalMovement = calculateVerticalMovement(elapsedSeconds);

            Vec3d entityPos = entry.getCurrentEntityPos();
            double distance = entityPos.distanceTo(cam);

            float horizontalOffset = entry.horizontalOffset * 0.6f;
            float verticalOffset = entry.verticalOffset * 0.6f;
            float depthOffset = entry.depthOffset * 0.6f;

            float yawRad = (float) Math.toRadians(camera.getYaw());
            float pitchRad = (float) Math.toRadians(camera.getPitch());
            double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double fwdY = -Math.sin(pitchRad);
            double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

            float dynamicFrontOffset = 1.2f * (float) Math.max(1.0, distance * 0.3);
            Vec3d pos = entityPos.add(
                    fwdX * dynamicFrontOffset + horizontalOffset,
                    (entry.entityHeight * 0.5f) + verticalOffset + verticalMovement,
                    fwdZ * dynamicFrontOffset + depthOffset);

            matrices.push();
            matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            matrices.multiply(new Quaternionf().fromAxisAngleDeg(0f, 1f, 0f, -camera.getYaw()));
            matrices.multiply(new Quaternionf().fromAxisAngleDeg(1f, 0f, 0f, camera.getPitch()));

            float scale = 0.035f * (float) (1.0 + 1.0 / (distance + 1.0));
            scale = Math.min(scale, 0.065f);

            int entryIndex = 0;
            for (DamageEntry e2 : ENTRIES) {
                if (!e2.entityUuid.equals(entry.entityUuid)) continue;
                if (e2 == entry) break;
                if (e2.startTime > entry.startTime) entryIndex++;
            }
            scale *= (float) Math.pow(0.5, entryIndex);

            float animProgress = elapsedSeconds / 1.5f;
            if (animProgress < 0.2f) scale *= 0.8f + animProgress;
            else if (animProgress > 0.7f) scale *= 1.0f - (animProgress - 0.7f) * 0.7f;

            matrices.scale(-scale, -scale, scale);

            String text;
            if (entry.amount == 0) text = "Hit!";
            else text = String.format("-%.1f", entry.amount);
            if (entry.isCritical) text += "!";

            int color = textColor(entry, animProgress);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float width = font.getWidth(text);
            font.draw(text, -width / 2f, -font.fontHeight / 2f, color, true, matrix,
                    immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);

            matrices.pop();
        }
        immediate.draw();
    }

    private static float calculateVerticalMovement(float elapsedTime) {
        if (elapsedTime < 0.2f) return elapsedTime * 2.5f;
        return 0.5f - (elapsedTime - 0.2f) * 0.4f;
    }

    private static int textColor(DamageEntry entry, float progress) {
        int rgb = entry.isPlayer ? 0xFF0000 : 0xFFFFFF;
        int alpha = (int) (255.0f * (1.0f - Math.max(0.0f, (progress - 0.7f) / 0.3f)));
        return (alpha << 24) | rgb;
    }

    private static final class DamageEntry {
        final UUID entityUuid;
        final WeakReference<Entity> entityRef;
        Vec3d entityPos;
        final float entityHeight;
        final long startTime;
        final double amount;
        final boolean isCritical;
        final boolean isPlayer;
        final float horizontalOffset;
        final float verticalOffset;
        final float depthOffset;

        DamageEntry(LivingEntity entity, double amount, boolean isCritical, boolean isPlayer) {
            this.entityUuid = entity.getUuid();
            this.entityRef = new WeakReference<>(entity);
            this.entityPos = entity.getEntityPos();
            this.entityHeight = entity.getHeight();
            this.amount = amount;
            this.isCritical = isCritical;
            this.isPlayer = isPlayer;
            this.startTime = System.currentTimeMillis();
            this.horizontalOffset = RNG.nextFloat() * 2.0f - 1.0f;
            this.verticalOffset = RNG.nextFloat() * 2.0f - 1.0f;
            this.depthOffset = RNG.nextFloat() * 2.0f - 1.0f;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > LIFETIME_MS;
        }

        Vec3d getCurrentEntityPos() {
            Entity e = entityRef.get();
            if (e != null && e.isAlive()) entityPos = e.getEntityPos();
            return entityPos;
        }
    }
}
