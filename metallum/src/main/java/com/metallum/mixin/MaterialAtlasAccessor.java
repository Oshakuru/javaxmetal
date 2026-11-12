package com.metallum.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(TextureAtlas.class)
public interface MaterialAtlasAccessor {
    @Accessor("texturesByName")
    Map<Identifier, TextureAtlasSprite> metallum$materialSprites();
}
