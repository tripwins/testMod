package dev.prisonsutils.client.guard;

import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guard danger-zone ESP. Chunk-aligned iteration, top-face exposed filter, multi-sample
 * line-of-sight, three categories (danger / warning / no-LOS). Rebuild runs off-thread.
 */
public final class GuardRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils-GuardRender");

    private static final int RANGE_FRONT = 28;
    private static final int RANGE_BACK = 16;
    private static final int RANGE_SIDE = 28;
    private static final int WARNING_BLOCK_RANGE = 2;
    private static final int MAX_HEIGHT = 50;

    private static final int FACE_UP = 1;
    private static final int FACE_DOWN = 2;
    private static final int FACE_NORTH = 4;
    private static final int FACE_SOUTH = 8;
    private static final int FACE_EAST = 16;
    private static final int FACE_WEST = 32;

    private static final float DEFAULT_OPACITY = 0.8f;
    private static final float FACE_OFFSET = 0.005f;

    private static final long REBUILD_INTERVAL_MS = 800L;
    private static final int REBUILD_TRIGGER_MOVE_BLOCKS = 3;
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0 * 64.0;

    public record BlockHighlight(BlockPos pos, int facesMask, Category category) {}
    public enum Category { DANGER, WARNING, LOS_BLOCKED }

    private static volatile List<BlockHighlight> cached = List.of();
    private static volatile long lastBuiltVersion = -1L;
    private static volatile long lastBuiltMs = 0L;
    private static volatile BlockPos lastBuiltPlayerPos = null;
    private static final AtomicBoolean buildInFlight = new AtomicBoolean(false);
    private static final Thread WORKER;

    private static final double[][] SURFACE_OFFSETS = {
            {0.0, 0.0},
            {0.0, 0.45}, {0.0, -0.45},
            {0.45, 0.0}, {-0.45, 0.0},
            {0.45, 0.45}, {0.45, -0.45},
            {-0.45, 0.45}, {-0.45, -0.45}
    };

    private static final Map<Long, Map<Long, Boolean>> losCache = new HashMap<>();

    static {
        WORKER = new Thread(GuardRenderer::workerLoop, "prisonsutils-guard-builder");
        WORKER.setDaemon(true);
    }

    private GuardRenderer() {}

    public static void register() {
        WORKER.start();
        ClientTickEvents.END_CLIENT_TICK.register(GuardRenderer::onTick);
        WorldRenderEvents.AFTER_ENTITIES.register(GuardRenderer::onRender);
    }

    public static int visibleCount() {
        return cached.size();
    }

    public static void onVisibilityEnabled() {
        lastBuiltMs = 0L;
        lastBuiltPlayerPos = null;
        requestRebuild();
    }

    private static boolean isVisible() {
        return Config.get().guardViewEnabled;
    }

    /** Danger zones are needed when the ESP is shown OR the HUD warning is on. */
    private static boolean needsData() {
        return Config.get().guardViewEnabled || Config.get().guardWarningEnabled;
    }

    /**
     * The guard threat at the local player's column, for the HUD warning:
     * {@code 2} = in a danger zone, {@code 1} = close (warning band), {@code 0} = safe.
     * Uses a generous Y window so it stays on while jumping.
     */
    private static final double CLOSE_BLOCK_DIST_SQ = 3.0 * 3.0; // within 3 blocks of a zone block
    private static final double CLOSE_GUARD_DIST_SQ = 5.0 * 5.0;  // within 5 blocks of a guard

    public static int playerZone() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        double pxd = mc.player.getX();
        double pzd = mc.player.getZ();
        int px = (int) Math.floor(pxd);
        int pz = (int) Math.floor(pzd);
        int py = (int) Math.floor(mc.player.getY());

        int best = 0;
        for (BlockHighlight h : cached) {
            BlockPos p = h.pos();
            if (p.getY() < py - 4 || p.getY() > py + 2) continue; // vertical window (tolerate jumping)

            // Standing on a danger block → full danger.
            if (h.category() == Category.DANGER && p.getX() == px && p.getZ() == pz) return 2;

            // Within 3 blocks of any danger/warning block → "close".
            if (h.category() == Category.DANGER || h.category() == Category.WARNING) {
                double dx = p.getX() + 0.5 - pxd, dz = p.getZ() + 0.5 - pzd;
                if (dx * dx + dz * dz <= CLOSE_BLOCK_DIST_SQ) best = 1;
            }
        }
        if (best == 0) {
            // Or near any guard entity at all.
            for (Guard g : GuardManager.guards()) {
                double dx = g.pos().getX() + 0.5 - pxd, dz = g.pos().getZ() + 0.5 - pzd;
                if (dx * dx + dz * dz <= CLOSE_GUARD_DIST_SQ) { best = 1; break; }
            }
        }
        return best;
    }

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!needsData()) {
            if (!cached.isEmpty()) cached = List.of();
            return;
        }
        if (GuardManager.guards().isEmpty()) {
            if (!cached.isEmpty()) cached = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        BlockPos playerPos = client.player.getBlockPos();
        boolean guardsChanged = GuardManager.version() != lastBuiltVersion;
        boolean playerMoved = lastBuiltPlayerPos == null
                || playerPos.getSquaredDistance(lastBuiltPlayerPos)
                        > (long) REBUILD_TRIGGER_MOVE_BLOCKS * REBUILD_TRIGGER_MOVE_BLOCKS;
        boolean stale = now - lastBuiltMs > REBUILD_INTERVAL_MS;
        if (guardsChanged || playerMoved || stale) {
            requestRebuild();
        }
    }

    private static void requestRebuild() {
        synchronized (buildInFlight) {
            buildInFlight.set(true);
            buildInFlight.notifyAll();
        }
    }

    private static void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (buildInFlight) {
                    while (!buildInFlight.get()) buildInFlight.wait();
                    buildInFlight.set(false);
                }
                rebuild();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LOG.error("Guard rebuild failed", t);
            }
        }
    }

    private static void rebuild() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (client.player == null || world == null) return;

        BlockPos playerPos = client.player.getBlockPos();
        long version = GuardManager.version();
        // Drop stale line-of-sight results when guards change. Done here (worker thread) so losCache
        // is only ever touched by this thread — clearing it from the tick thread was a data race.
        if (version != lastBuiltVersion) losCache.clear();
        Vec3d camPos = client.gameRenderer.getCamera().getCameraPos();

        Map<Long, MutableHighlight> blockMap = new HashMap<>();
        int[] stats = new int[4];
        for (Guard g : GuardManager.guards()) {
            try {
                collectGuardRangeBlocks(g, world, camPos, blockMap, stats);
            } catch (Throwable t) {
                LOG.error("Failed processing guard at {}", g.pos(), t);
            }
        }

        List<BlockHighlight> next = new ArrayList<>(blockMap.size());
        for (MutableHighlight h : blockMap.values()) {
            if (h.facesMask != 0) {
                next.add(new BlockHighlight(h.pos, h.facesMask, h.category));
            }
        }
        cached = next;
        lastBuiltVersion = version;
        lastBuiltPlayerPos = playerPos;
        lastBuiltMs = System.currentTimeMillis();
    }

    private static void collectGuardRangeBlocks(Guard guard, ClientWorld world, Vec3d camPos,
                                                Map<Long, MutableHighlight> out, int[] stats) {
        BlockPos gp = guard.pos();
        float yaw = guard.yaw();
        int maxRange = Math.max(Math.max(RANGE_FRONT, RANGE_BACK), RANGE_SIDE);
        int minY = gp.getY() - MAX_HEIGHT;
        int maxY = gp.getY() + MAX_HEIGHT;
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        int minChunkX = (gp.getX() - maxRange) >> 4;
        int maxChunkX = (gp.getX() + maxRange) >> 4;
        int minChunkZ = (gp.getZ() - maxRange) >> 4;
        int maxChunkZ = (gp.getZ() + maxRange) >> 4;

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                int chunkMinX = chunkX << 4;
                int chunkMaxX = chunkMinX + 15;
                int chunkMinZ = chunkZ << 4;
                int chunkMaxZ = chunkMinZ + 15;
                int startX = Math.max(chunkMinX, gp.getX() - maxRange);
                int endX = Math.min(chunkMaxX, gp.getX() + maxRange);
                int startZ = Math.max(chunkMinZ, gp.getZ() - maxRange);
                int endZ = Math.min(chunkMaxZ, gp.getZ() + maxRange);

                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        int dx = x - gp.getX();
                        int dz = z - gp.getZ();
                        if (dx == 0 && dz == 0) continue;

                        double forwardDistance = dx * forwardX + dz * forwardZ;
                        double sideDistance = Math.abs(dx * rightX + dz * rightZ);
                        double absForward = Math.abs(forwardDistance);

                        int dangerRange;
                        if (forwardDistance > 0) {
                            dangerRange = RANGE_FRONT;
                        } else if (absForward > 5.0) {
                            dangerRange = RANGE_BACK;
                        } else if (sideDistance <= absForward) {
                            dangerRange = RANGE_BACK;
                        } else {
                            dangerRange = RANGE_SIDE;
                        }

                        double euclidean = Math.sqrt(forwardDistance * forwardDistance + sideDistance * sideDistance);
                        double effectiveDanger = (forwardDistance > 0 && sideDistance < 0.5)
                                ? dangerRange - 1.0 : dangerRange;
                        double warningLimit = effectiveDanger + WARNING_BLOCK_RANGE;
                        boolean inDanger = euclidean < effectiveDanger;
                        boolean inWarning = euclidean >= dangerRange && euclidean <= warningLimit;
                        if (!inDanger && !inWarning) continue;

                        for (int y = minY; y <= maxY; y++) {
                            cursor.set(x, y, z);
                            double cdx = x + 0.5 - camPos.x;
                            double cdy = y + 0.5 - camPos.y;
                            double cdz = z + 0.5 - camPos.z;
                            if (cdx * cdx + cdy * cdy + cdz * cdz > MAX_RENDER_DISTANCE_SQ) continue;

                            if (chunk.getBlockState(cursor).isAir()) continue;
                            stats[0]++;

                            if (camPos.x > x && camPos.x < x + 1
                                    && camPos.y > y && camPos.y < y + 1
                                    && camPos.z > z && camPos.z < z + 1) continue;

                            neighbor.set(x, y + 1, z);
                            if (!isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) continue;
                            stats[1]++;

                            int mask = FACE_UP;
                            neighbor.set(x, y - 1, z);
                            if (isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) mask |= FACE_DOWN;
                            neighbor.set(x, y, z - 1);
                            if (isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) mask |= FACE_NORTH;
                            neighbor.set(x, y, z + 1);
                            if (isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) mask |= FACE_SOUTH;
                            neighbor.set(x - 1, y, z);
                            if (isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) mask |= FACE_WEST;
                            neighbor.set(x + 1, y, z);
                            if (isAirNeighbor(world, chunk, neighbor, chunkX, chunkZ)) mask |= FACE_EAST;
                            if (mask == 0) continue;

                            BlockPos pos = cursor.toImmutable();
                            long key = pos.asLong();

                            if (inWarning && !inDanger) {
                                stats[3]++;
                                MutableHighlight existing = out.get(key);
                                if (existing == null) {
                                    out.put(key, new MutableHighlight(pos, mask, Category.WARNING));
                                } else {
                                    existing.facesMask |= mask;
                                    if (existing.category == null) existing.category = Category.WARNING;
                                }
                            } else {
                                boolean clear = hasLineOfSightToTopFace(world, gp, pos);
                                Category cat = clear ? Category.DANGER : Category.LOS_BLOCKED;
                                if (clear) stats[2]++;
                                MutableHighlight existing = out.get(key);
                                if (existing == null) {
                                    out.put(key, new MutableHighlight(pos, mask, cat));
                                } else {
                                    existing.facesMask |= mask;
                                    if (cat == Category.DANGER) {
                                        existing.category = Category.DANGER;
                                    } else if (existing.category == Category.WARNING) {
                                        existing.category = Category.LOS_BLOCKED;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isAirNeighbor(ClientWorld world, WorldChunk chunk,
                                         BlockPos pos, int chunkX, int chunkZ) {
        if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
            return chunk.getBlockState(pos).isAir();
        }
        return world.getBlockState(pos).isAir();
    }

    private static boolean hasLineOfSightToTopFace(ClientWorld world, BlockPos guardPos, BlockPos target) {
        long gKey = guardPos.asLong();
        long tKey = target.asLong();
        Map<Long, Boolean> sub = losCache.get(gKey);
        if (sub != null) {
            Boolean cachedResult = sub.get(tKey);
            if (cachedResult != null) return cachedResult;
        }

        Vec3d[] eyes = buildGuardEyePoints(guardPos);
        double baseX = target.getX() + 0.5;
        double baseZ = target.getZ() + 0.5;
        double feetY = target.getY() + 1.0;

        for (double[] off : SURFACE_OFFSETS) {
            Vec3d[] samples = buildPlayerAabbSamples(baseX + off[0], baseZ + off[1], feetY);
            if (guardCanSeePlayerAabb(world, eyes, samples)) {
                losCache.computeIfAbsent(gKey, k -> new HashMap<>()).put(tKey, true);
                return true;
            }
        }
        losCache.computeIfAbsent(gKey, k -> new HashMap<>()).put(tKey, false);
        return false;
    }

    private static Vec3d[] buildGuardEyePoints(BlockPos gp) {
        double cx = gp.getX() + 0.5;
        double cy = gp.getY() + 2.62;
        double cz = gp.getZ() + 0.5;
        return new Vec3d[] {
                new Vec3d(cx, cy, cz),
                new Vec3d(cx - 0.2, cy, cz),
                new Vec3d(cx + 0.2, cy, cz)
        };
    }

    private static Vec3d[] buildPlayerAabbSamples(double posX, double posZ, double feetY) {
        double minX = posX - 0.45, maxX = posX + 0.45;
        double minZ = posZ - 0.45, maxZ = posZ + 0.45;
        double yLow = feetY + 0.2, yMid = feetY + 1.0, yHigh = feetY + 1.7;

        Vec3d[] arr = new Vec3d[1 + 12 + 12];
        int i = 0;
        arr[i++] = new Vec3d(posX, yMid, posZ);
        for (double y : new double[] { yLow, yMid, yHigh }) {
            arr[i++] = new Vec3d(minX, y, minZ);
            arr[i++] = new Vec3d(maxX, y, minZ);
            arr[i++] = new Vec3d(minX, y, maxZ);
            arr[i++] = new Vec3d(maxX, y, maxZ);
        }
        for (double y : new double[] { yLow, yMid, yHigh }) {
            arr[i++] = new Vec3d(posX, y, minZ);
            arr[i++] = new Vec3d(posX, y, maxZ);
            arr[i++] = new Vec3d(minX, y, posZ);
            arr[i++] = new Vec3d(maxX, y, posZ);
        }
        return arr;
    }

    private static boolean guardCanSeePlayerAabb(ClientWorld world, Vec3d[] eyes, Vec3d[] samples) {
        Entity probe = MinecraftClient.getInstance().player;
        if (probe == null) return false;
        for (Vec3d eye : eyes) {
            for (Vec3d sample : samples) {
                if (world.raycast(new RaycastContext(
                        eye, sample,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        probe)).getType() == HitResult.Type.MISS) return true;
            }
        }
        return false;
    }

    private static void onRender(WorldRenderContext context) {
        if (!isVisible()) return;
        List<BlockHighlight> highlights = cached;
        if (highlights.isEmpty() && GuardManager.guards().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        RenderLayer layer;
        try { layer = RenderLayers.debugFilledBox(); }
        catch (Throwable t) { layer = RenderLayers.debugQuads(); }
        VertexConsumer vc = consumers.getBuffer(layer);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int dangerC = Config.get().guardDangerColor;
        int warningC = Config.get().guardWarningColor;
        int nolosC = Config.get().guardNoLosColor;
        for (BlockHighlight h : highlights) {
            int c = switch (h.category) {
                case DANGER -> dangerC;
                case WARNING -> warningC;
                case LOS_BLOCKED -> nolosC;
            };
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;
            renderBoxFaces(vc, matrix, h.pos, h.facesMask, r, g, b, DEFAULT_OPACITY);
        }

        for (Guard guard : GuardManager.manualGuards()) emitGuardMarker(vc, matrix, guard.pos(), 1f);

        matrices.pop();
    }

    private static void renderBoxFaces(VertexConsumer vc, Matrix4f matrix, BlockPos pos,
                                       int mask, float r, float g, float b, float alpha) {
        float minX = pos.getX();
        float maxX = pos.getX() + 1f;
        float minY = pos.getY();
        float maxY = pos.getY() + 1f;
        float minZ = pos.getZ();
        float maxZ = pos.getZ() + 1f;

        if ((mask & FACE_UP) != 0) doubleQuad(vc, matrix,
                minX, maxY + FACE_OFFSET, minZ, maxX, maxY + FACE_OFFSET, minZ,
                maxX, maxY + FACE_OFFSET, maxZ, minX, maxY + FACE_OFFSET, maxZ, r, g, b, alpha);
        if ((mask & FACE_DOWN) != 0) doubleQuad(vc, matrix,
                minX, minY - FACE_OFFSET, maxZ, maxX, minY - FACE_OFFSET, maxZ,
                maxX, minY - FACE_OFFSET, minZ, minX, minY - FACE_OFFSET, minZ, r, g, b, alpha);
        if ((mask & FACE_NORTH) != 0) doubleQuad(vc, matrix,
                minX, minY, minZ - FACE_OFFSET, maxX, minY, minZ - FACE_OFFSET,
                maxX, maxY, minZ - FACE_OFFSET, minX, maxY, minZ - FACE_OFFSET, r, g, b, alpha);
        if ((mask & FACE_SOUTH) != 0) doubleQuad(vc, matrix,
                minX, maxY, maxZ + FACE_OFFSET, maxX, maxY, maxZ + FACE_OFFSET,
                maxX, minY, maxZ + FACE_OFFSET, minX, minY, maxZ + FACE_OFFSET, r, g, b, alpha);
        if ((mask & FACE_WEST) != 0) doubleQuad(vc, matrix,
                minX - FACE_OFFSET, minY, maxZ, minX - FACE_OFFSET, minY, minZ,
                minX - FACE_OFFSET, maxY, minZ, minX - FACE_OFFSET, maxY, maxZ, r, g, b, alpha);
        if ((mask & FACE_EAST) != 0) doubleQuad(vc, matrix,
                maxX + FACE_OFFSET, maxY, maxZ, maxX + FACE_OFFSET, maxY, minZ,
                maxX + FACE_OFFSET, minY, minZ, maxX + FACE_OFFSET, minY, maxZ, r, g, b, alpha);
    }

    private static void doubleQuad(VertexConsumer vc, Matrix4f m,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4,
                                   float r, float g, float b, float a) {
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a);
        vc.vertex(m, x3, y3, z3).color(r, g, b, a);
        vc.vertex(m, x4, y4, z4).color(r, g, b, a);
        vc.vertex(m, x4, y4, z4).color(r, g, b, a);
        vc.vertex(m, x3, y3, z3).color(r, g, b, a);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a);
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
    }

    private static void emitGuardMarker(VertexConsumer vc, Matrix4f matrix, BlockPos p, float yOff) {
        float x = p.getX();
        float y = p.getY() + yOff + FACE_OFFSET;
        float z = p.getZ();
        vc.vertex(matrix, x, y, z).color(1.0f, 0.84f, 0.0f, 0.78f);
        vc.vertex(matrix, x, y, z + 1f).color(1.0f, 0.84f, 0.0f, 0.78f);
        vc.vertex(matrix, x + 1f, y, z + 1f).color(1.0f, 0.84f, 0.0f, 0.78f);
        vc.vertex(matrix, x + 1f, y, z).color(1.0f, 0.84f, 0.0f, 0.78f);
    }

    private static final class MutableHighlight {
        final BlockPos pos;
        int facesMask;
        Category category;
        MutableHighlight(BlockPos pos, int facesMask, Category category) {
            this.pos = pos;
            this.facesMask = facesMask;
            this.category = category;
        }
    }
}
