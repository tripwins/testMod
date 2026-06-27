package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.search.SearchManager;
import dev.prisonsutils.config.Config;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a search field above container GUIs that highlights matching items. The query is
 * kept in {@link SearchManager} and is NOT cleared when the screen closes, so it carries
 * over between inventories until the user clears it.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenSearchBarMixin extends Screen {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    @Unique private TextFieldWidget prisonsutils$searchField;

    protected HandledScreenSearchBarMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void prisonsutils$addSearchBar(CallbackInfo ci) {
        if (!Config.get().searchBarEnabled) return;

        int fieldW = Math.min(140, backgroundWidth - 20);
        int fieldX = x + (backgroundWidth - fieldW) / 2;
        int fieldY = y + backgroundHeight + 3;
        prisonsutils$searchField = new TextFieldWidget(
                this.textRenderer, fieldX, fieldY, fieldW, 18, Text.literal("Search"));
        prisonsutils$searchField.setMaxLength(64);
        prisonsutils$searchField.setPlaceholder(Text.literal("Search items..."));
        prisonsutils$searchField.setText(SearchManager.getSearchQuery());
        prisonsutils$searchField.setChangedListener(SearchManager::setSearchQuery);
        this.addDrawableChild(prisonsutils$searchField);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void prisonsutils$defocusSearch(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        // Clicking anywhere outside the search field stops typing into it.
        if (prisonsutils$searchField != null
                && !prisonsutils$searchField.isMouseOver(click.x(), click.y())) {
            prisonsutils$searchField.setFocused(false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$interceptKeyPress(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (prisonsutils$searchField == null || !prisonsutils$searchField.isFocused()) return;

        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) return;

        if (prisonsutils$searchField.keyPressed(input)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(true);
    }
}
