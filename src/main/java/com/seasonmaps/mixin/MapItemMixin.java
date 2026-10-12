package com.seasonmaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.seasonmaps.Layer;
import com.seasonmaps.LayerStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla MapItem.update scans one pixel at a time and calls data.updateColor(x, y, color).
 * We let that happen, then also write down the biome at that pixel.
 */
@Mixin(MapItem.class)
public abstract class MapItemMixin {
    @WrapOperation(
            method = "update",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z"))
    private boolean seasonmaps$recordBiome(MapItemSavedData data, int imgX, int imgY, byte color,
                                           Operation<Boolean> original,
                                           @Local(argsOnly = true) Level level) {
        boolean changed = original.call(data, imgX, imgY, color);
        // Nether-style dimensions have no sky to tint. Client-side calls never happen here
        // (update only runs on the server) but the cast keeps that honest.
        if (level instanceof ServerLevel server && !level.dimensionType().hasCeiling()) {
            int scale = 1 << data.scale;
            // Same top-left block of the pixel's square that vanilla used to fetch the chunk.
            int x = (data.centerX / scale + imgX - 64) * scale;
            int z = (data.centerZ / scale + imgY - 64) * scale;
            // getChunk here is a no-op load: vanilla fetched this exact chunk a few lines up.
            // Never call level.getHeight or neighbors, in 26.x that can load chunks and hang the tick.
            LevelChunk chunk = server.getChunk(x >> 4, z >> 4);
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            String biome = chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey()
                    .map(key -> key.identifier().toString()).orElse(null);
            if (biome != null) {
                Layer layer = LayerStore.of(server.getServer(), data);
                layer.set(imgX, imgY, biome, tintableBlock(level, chunk, x, y, z, color));
            }
        }
        return changed;
    }

    /**
     * The surface block's id, but only when it is what picked this pixel's color and it is a
     * grass or plant color (the ones the client may tint). Otherwise null.
     *
     * Same walk down as vanilla: skip blocks whose map color is NONE. At map scale above 0 vanilla
     * votes across a square of blocks and we only look at its corner, so the corner block can
     * disagree with the winning color. Then we return null and the client falls back to the
     * plain grass/plant tint. Wrong-ish, never a crash.
     */
    @Unique
    private static String tintableBlock(Level level, LevelChunk chunk, int x, int y, int z, byte packed) {
        int colorId = (packed & 0xFF) >> 2;
        if (colorId != MapColor.GRASS.id && colorId != MapColor.PLANT.id) return null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        BlockState state = chunk.getBlockState(pos);
        while (state.getMapColor(level, pos) == MapColor.NONE && pos.getY() > level.getMinY()) {
            pos.setY(pos.getY() - 1);
            state = chunk.getBlockState(pos);
        }
        if (state.getMapColor(level, pos).id != colorId) return null;
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
