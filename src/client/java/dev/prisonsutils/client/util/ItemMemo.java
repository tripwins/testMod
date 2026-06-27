package dev.prisonsutils.client.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.item.ItemStack;

/**
 * Per-item memo cache for expensive item parses (NBT deep-copies, lore scans) that overlays and tints
 * would otherwise redo every frame for every visible item.
 *
 * <p>Keyed by stack <i>identity</i>: a slot/hotbar/dropped-item stack is the same object across frames,
 * and the server hands you a fresh {@link ItemStack} object whenever an item actually changes — so a
 * changed item misses the cache and recomputes immediately. A short TTL is only a backstop for the
 * rare in-place mutation, which keeps results visually live (they refresh a few times a second instead
 * of 60+). Identity keying avoids any {@code ItemStack.equals} cost entirely.
 *
 * <p>Render-thread only (all callers run during rendering); not synchronized.
 */
public final class ItemMemo<V> {
    private final long ttlMs;
    private final Map<ItemStack, Object[]> cache = new IdentityHashMap<>(); // stack -> {V value, Long time}
    private long lastSweep;

    public ItemMemo(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /** Returns the cached value for {@code stack}, or computes + stores it. Caches null results too. */
    public V get(ItemStack stack, Function<ItemStack, V> compute) {
        if (stack == null || stack.isEmpty()) {
            return compute.apply(stack);
        }
        long now = System.currentTimeMillis();
        Object[] entry = cache.get(stack);
        if (entry != null && now - (long) entry[1] < ttlMs) {
            @SuppressWarnings("unchecked")
            V cached = (V) entry[0];
            return cached;
        }
        V value = compute.apply(stack);
        cache.put(stack, new Object[]{value, now});
        if (now - lastSweep > 3000L) {
            sweep(now);
            lastSweep = now;
        }
        return value;
    }

    /** Drop entries that are well past their TTL (their stacks are gone or idle), and hard-cap size. */
    private void sweep(long now) {
        cache.values().removeIf(e -> now - (long) e[1] > ttlMs + 3000L);
        if (cache.size() > 8192) {
            cache.clear();
        }
    }
}
