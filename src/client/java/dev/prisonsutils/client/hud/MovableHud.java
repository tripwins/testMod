package dev.prisonsutils.client.hud;

import dev.prisonsutils.client.guard.GuardWarningHud;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/** A HUD element the player can drag/scale in {@link HudEditorScreen}. */
public interface MovableHud {
    String name();

    HudAnchor anchor();

    /** Scaled width/height of the element's current (sample) content, for the editor box. */
    int contentW(int screenW);
    int contentH();

    /** Draw a representative sample at the element's live position/scale/alignment. */
    void drawSample(DrawContext ctx);

    static List<MovableHud> all() {
        return List.of(GuardWarningHud.MOVABLE, CrosshairPopup.MOVABLE, CoordsHud.MOVABLE);
    }
}
