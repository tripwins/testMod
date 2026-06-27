package dev.prisonsutils.client.notification;

import java.util.List;
import java.util.Objects;

public final class Notification {
    public static final long DEFAULT_FADE_IN_MILLIS = 200L;
    public static final long DEFAULT_HOLD_MILLIS = 5000L;
    public static final long DEFAULT_FADE_OUT_MILLIS = 800L;

    public final String title;
    public final List<String> lines;
    public final int accentColor;
    public final long createdAtMillis;
    public final long fadeInMillis;
    public final long holdMillis;
    public final long fadeOutMillis;

    public Notification(String title, List<String> lines, int accentColor) {
        this(title, lines, accentColor, DEFAULT_FADE_IN_MILLIS, DEFAULT_HOLD_MILLIS, DEFAULT_FADE_OUT_MILLIS);
    }

    public Notification(String title, List<String> lines, int accentColor,
                        long fadeInMillis, long holdMillis, long fadeOutMillis) {
        this.title = Objects.requireNonNull(title, "title");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        this.accentColor = accentColor;
        this.createdAtMillis = System.currentTimeMillis();
        this.fadeInMillis = Math.max(0L, fadeInMillis);
        this.holdMillis = Math.max(0L, holdMillis);
        this.fadeOutMillis = Math.max(0L, fadeOutMillis);
    }

    public long lifetimeMillis() {
        return fadeInMillis + holdMillis + fadeOutMillis;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis - createdAtMillis >= lifetimeMillis();
    }

    public float alpha(long nowMillis) {
        long age = nowMillis - createdAtMillis;
        if (age < 0L) return 0f;
        if (age < fadeInMillis) return fadeInMillis == 0 ? 1f : (float) age / fadeInMillis;
        long fadeOutStart = fadeInMillis + holdMillis;
        if (age < fadeOutStart) return 1f;
        long fadeOutAge = age - fadeOutStart;
        if (fadeOutAge >= fadeOutMillis) return 0f;
        return fadeOutMillis == 0 ? 1f : 1f - (float) fadeOutAge / fadeOutMillis;
    }
}
