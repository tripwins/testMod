package dev.prisonsutils.client.mining;

/** Shared flag: true while {@code MinecraftClient.doItemUse} is running. */
public final class MiningState {
    public static volatile boolean inDoItemUse = false;

    private MiningState() {}
}
