package com.metallum.mixin;

import com.metallum.visual.VisualState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
abstract class VisualAtlasReloadMixin {
    @Inject(method = "upload", at = @At("TAIL"))
    private void metallum$invalidateMaterials(CallbackInfo ci) { VisualState.invalidate(); }
}
