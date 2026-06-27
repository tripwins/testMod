package dev.prisonsutils.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the inventory window geometry. These fields are declared on HandledScreen,
 * so the accessor must target HandledScreen directly — Lunar's mixin engine will not
 * resolve them as inherited {@code @Shadow} fields against InventoryScreen.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int prisonsutils$getX();

    @Accessor("y")
    int prisonsutils$getY();

    @Accessor("backgroundWidth")
    int prisonsutils$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int prisonsutils$getBackgroundHeight();
}
