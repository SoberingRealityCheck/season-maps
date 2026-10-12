package com.seasonmaps.client;

import com.seasonmaps.SeasonMapsMod;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;

/**
 * Turns a map pixel (vanilla color byte + biome id + surface block id) into an ARGB int.
 *
 * Where the tint comes from, in order:
 *  1. WATER pixels: the game's water color resolver for the biome.
 *  2. A recorded block (grass block, leaves, flowers, crops...): ask the game's own
 *     BlockColors what tint that block gets. Spruce and birch leaves are fixed colors, oak is
 *     biome foliage, cherry has none, flowers have none. We get all of that, and Serene
 *     Seasons' own birch/foliage changes, because Serene registers into the same BlockColors.
 *  3. A GRASS or PLANT pixel with no recorded block (old map, or the corner block disagreed at
 *     map scale > 0): plain biome grass or foliage color.
 *  4. Everything else stays vanilla.
 *
 * Results are cached per (biome, block) and thrown away when the season changes. That cache
 * is the whole point: Xaero asked Serene Seasons for a color on every pixel of every redraw.
 */
public final class Tint {
    // How far to pull the vanilla map color toward the tint. 0 = vanilla, 1 = the full
    // world color (too bright on a paper map). Hack: picked by eye, not measured.
    private static final float STRENGTH = 0.7f;
    private static final int NO_TINT = -1;

    private static int cachedKey = Integer.MIN_VALUE;
    // "biome|block" -> 0xRRGGBB, or NO_TINT
    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private Tint() {}

    /**
     * Changes when the sub-season or the tropical season does (Serene colors tropical
     * biomes from the tropical season). -1 = no seasons, no level yet.
     */
    public static int seasonKey() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !SeasonHelper.hasSeasons(level)) return -1;
        ISeasonState state = SeasonHelper.getSeasonState(level);
        return state.getSubSeason().ordinal() * 16 + state.getTropicalSeason().ordinal();
    }

    /** ARGB for one pixel. biome and block may be null, which gives plain vanilla. */
    public static int pixel(byte packed, String biome, String block) {
        int vanilla = MapColor.getColorFromPackedId(packed);
        if (biome == null) return vanilla;

        int value = packed & 0xFF;
        int colorId = value >> 2;
        int tint = tintFor(colorId, biome, block);
        if (tint == NO_TINT) return vanilla;

        int base = MapColor.byId(colorId).col;
        int mixed = lerp(base, tint, STRENGTH);
        return ARGB.scaleRGB(ARGB.opaque(mixed), MapColor.Brightness.byId(value & 3).modifier);
    }

    private static int tintFor(int colorId, String biome, String block) {
        boolean water = colorId == MapColor.WATER.id;
        boolean land = colorId == MapColor.GRASS.id || colorId == MapColor.PLANT.id;
        if (!water && !land) return NO_TINT;

        int key = seasonKey();
        if (key != cachedKey) {
            CACHE.clear();
            cachedKey = key;
        }
        String cacheKey = water ? biome + "|water" : biome + "|" + (block != null ? block : "#" + colorId);
        return CACHE.computeIfAbsent(cacheKey, k -> compute(colorId, biome, block));
    }

    private static int compute(int colorId, String biomeId, String blockId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return NO_TINT;
        Biome biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getValue(Identifier.parse(biomeId));
        if (biome == null) return NO_TINT;

        if (colorId == MapColor.WATER.id) return resolve(BiomeColors.WATER_COLOR_RESOLVER, biome);

        // Serene Seasons replaces BiomeColors.GRASS/FOLIAGE_COLOR_RESOLVER with its own at
        // startup, so these are the seasonal ones, same as the world renderer reads.
        if (blockId == null) {
            return resolve(colorId == MapColor.GRASS.id ? BiomeColors.GRASS_COLOR_RESOLVER : BiomeColors.FOLIAGE_COLOR_RESOLVER, biome);
        }
        return blockTint(biome, blockId);
    }

    static int blockTint(Biome biome, String blockId) {
        BlockState state = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)).defaultBlockState();
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        if (source == null) return NO_TINT; // cherry leaves, flowers, azalea...
        try {
            int color = source.colorInWorld(state, stubLevel(biome), BlockPos.ZERO) & 0xFFFFFF;
            return color == 0xFFFFFF ? NO_TINT : color; // white = "layer is not tinted"
        } catch (RuntimeException e) {
            // A mod's tint source asked the stub something it can't answer. Show vanilla color.
            if (WARNED.add(blockId)) SeasonMapsMod.LOGGER.warn("No map tint for {}: {}", blockId, e.toString());
            return NO_TINT;
        }
    }

    /**
     * A BlockAndTintGetter that only knows how to answer "what is the tint of this biome".
     * Vanilla tint sources call getBlockTint(pos, resolver), which normally averages the
     * biomes around pos. Here we skip the averaging and use our one biome.
     * Hack: a Proxy, so we don't have to implement the eight other methods. Anything else
     * throws, and blockTint() turns that into "no tint".
     */
    private static BlockAndTintGetter stubLevel(Biome biome) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[] {BlockAndTintGetter.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockTint")) {
                        return resolve((ColorResolver) args[1], biome);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static int resolve(ColorResolver resolver, Biome biome) {
        // Hack: x = z = 0. The swamp grass modifier is noise on x/z, so a whole swamp gets
        // one shade. Fine for a map.
        return resolver.getColor(biome, 0, 0) & 0xFFFFFF;
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int g = Math.round((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return r << 16 | g << 8 | b;
    }
}
