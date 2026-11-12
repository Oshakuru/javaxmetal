package com.metallum.mixin;

import com.metallum.render.MetalWorldEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class VisualBlockChangeMixin {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void metallum$materialChanged(BlockPos pos, BlockState previous, BlockState next, CallbackInfo ci) {
        if (previous != next) MetalWorldEffects.invalidateCell(this, pos);
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void metallum$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        MetalWorldEffects.invalidateChunk(this, pos.x(), pos.z());
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void metallum$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        MetalWorldEffects.invalidateChunk(this, chunk.getPos().x(), chunk.getPos().z());
    }
}
