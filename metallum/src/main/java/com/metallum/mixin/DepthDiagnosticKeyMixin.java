package com.metallum.mixin;

import com.metallum.DepthDiagnostic;
import com.metallum.benchmark.BenchmarkRecorder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class DepthDiagnosticKeyMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void metallum$toggleDepth(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && client.level != null && client.gui.screen() == null
                && action == InputConstants.PRESS) {
            if (event.key() == InputConstants.KEY_F6) {
                com.metallum.visual.VisualState.toggle();
                client.gui.chatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Material effects " + (com.metallum.visual.VisualState.enabled ? "ON" : "OFF") + " (F6)"), false);
                return;
            }
            if (event.key() == InputConstants.KEY_F10) {
                com.metallum.visual.VisualState.materials = !com.metallum.visual.VisualState.materials;
                com.metallum.Metallum.LOGGER.info("Material diagnostic {} (F10; requires F6 ON)",
                        com.metallum.visual.VisualState.materials ? "ON" : "OFF");
                client.gui.chatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Material diagnostic " + (com.metallum.visual.VisualState.materials ? "ON" : "OFF")
                                + " (F10; effects must be ON)"), false);
                return;
            }
        }
        if (window == client.getWindow().handle() && action == InputConstants.PRESS
                && event.key() == InputConstants.KEY_F7) {
            com.metallum.material.MaterialInspector.inspect(client);
            return;
        }
        if (window == client.getWindow().handle() && action == InputConstants.PRESS
                && event.key() == InputConstants.KEY_F9) {
            BenchmarkRecorder.toggle(client);
            return;
        }
        if (window == client.getWindow().handle() && client.level != null
                && action == InputConstants.PRESS && event.key() == InputConstants.KEY_F8) {
            DepthDiagnostic.toggle();
        }
    }
}
