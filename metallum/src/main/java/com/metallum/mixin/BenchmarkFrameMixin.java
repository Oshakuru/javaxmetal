package com.metallum.mixin;

import com.metallum.benchmark.BenchmarkRecorder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class BenchmarkFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void metallum$recordFrame(boolean renderLevel, CallbackInfo ci) {
        BenchmarkRecorder.frame((Minecraft) (Object) this);
    }
}
