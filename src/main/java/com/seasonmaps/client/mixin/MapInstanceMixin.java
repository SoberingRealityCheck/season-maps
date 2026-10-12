package com.seasonmaps.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.seasonmaps.Layer;
import com.seasonmaps.client.ClientLayers;
import com.seasonmaps.client.Tint;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla redraws a map texture only when its colors change. Here we also redraw when
 * the biome layer changes or the sub-season changes, and we paint with the tinted color.
 * Maps with no layer fall straight through to vanilla.
 *
 * This is the only hook the drawing needs. Held maps, item frames and Ultimate Map Atlases
 * all draw through this texture (the atlas calls getMapRenderer().extractRenderState).
 */
@Mixin(targets = "net.minecraft.client.resources.MapTextureManager$MapInstance")
public abstract class MapInstanceMixin {
    @Shadow private MapItemSavedData data;
    @Shadow private DynamicTexture texture;
    @Shadow private boolean requiresUpload;
    @Shadow private Identifier location;

    @Unique private int seasonmaps$seenVersion = -1;
    @Unique private int seasonmaps$seenSeason = Integer.MIN_VALUE;

    @Inject(method = "updateTextureIfNeeded", at = @At("HEAD"), cancellable = true)
    private void seasonmaps$paintTinted(CallbackInfo ci) {
        Layer layer = ClientLayers.get(mapId());
        if (layer == null) return;

        int season = Tint.seasonKey();
        if (layer.version != seasonmaps$seenVersion || season != seasonmaps$seenSeason) requiresUpload = true;
        if (!requiresUpload) return; // vanilla would do nothing too

        NativeImage pixels = texture.getPixels();
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                pixels.setPixel(x, y, Tint.pixel(data.colors[x + y * 128], layer.biomeAt(x, y)));
            }
        }
        texture.upload();
        requiresUpload = false;
        seasonmaps$seenVersion = layer.version;
        seasonmaps$seenSeason = season;
        ci.cancel();
    }

    /** Vanilla names the texture "map/<id>" and keeps no id field. This is the hack that reads it back. */
    @Unique
    private int mapId() {
        return Integer.parseInt(location.getPath().substring("map/".length()));
    }
}
