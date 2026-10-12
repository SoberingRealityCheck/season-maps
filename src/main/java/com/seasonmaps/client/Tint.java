package com.seasonmaps.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;

/**
 * Turns a map pixel (vanilla color byte + biome id) into a season-tinted ARGB int.
 *
 * Only three vanilla map colors get tinted: GRASS, PLANT (leaves, plants) and WATER.
 * Everything else, stone, sand, snow, wood, stays exactly vanilla.
 *
 * The per-biome tint is worked out once per sub-season and cached. That cache is the
 * whole point of this mod: Xaero asked Serene Seasons for a color on every pixel of
 * every redraw. We ask once per biome per sub-season.
 */
public final class Tint {
    // How far to pull the vanilla map color toward the biome color. 0 = vanilla,
    // 1 = the biome's full world color (too bright on a paper map).
    // Hack: picked by eye, not measured. Make it a config if it matters.
    private static final float STRENGTH = 0.7f;

    private static int cachedKey = Integer.MIN_VALUE;
    // biome id -> { grass, foliage, water } as 0xRRGGBB, or an empty array if the biome is unknown
    private static final Map<String, int[]> CACHE = new HashMap<>();

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

    /** ARGB for one pixel. biome may be null (unknown), which gives plain vanilla. */
    public static int pixel(byte packed, String biome) {
        int vanilla = MapColor.getColorFromPackedId(packed);
        if (biome == null) return vanilla;

        int value = packed & 0xFF;
        int colorId = value >> 2;
        int slot = colorId == MapColor.GRASS.id ? 0 : colorId == MapColor.PLANT.id ? 1 : colorId == MapColor.WATER.id ? 2 : -1;
        if (slot < 0) return vanilla;

        int[] colors = colorsFor(biome);
        if (colors.length == 0) return vanilla;

        int base = MapColor.byId(colorId).col;
        int tinted = lerp(base, colors[slot], STRENGTH);
        return ARGB.scaleRGB(ARGB.opaque(tinted), MapColor.Brightness.byId(value & 3).modifier);
    }

    private static int[] colorsFor(String biomeId) {
        int key = seasonKey();
        if (key != cachedKey) {
            CACHE.clear();
            cachedKey = key;
        }
        return CACHE.computeIfAbsent(biomeId, Tint::compute);
    }

    private static int[] compute(String biomeId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new int[0];
        Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .get(ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId))).orElse(null);
        if (holder == null) return new int[0];

        // Same source of truth as the world renderer: the game's own ColorResolvers.
        // Serene Seasons replaces BiomeColors.GRASS_COLOR_RESOLVER and FOLIAGE_COLOR_RESOLVER
        // with its own at startup, so reading them here gets its tropical-vs-normal season
        // choice and other mods' overrides for free. Calling SeasonColorUtil ourselves
        // (what an earlier version did) skipped all of that.
        // Hack: x = z = 0. The swamp grass modifier is noise on x/z, so a whole swamp gets
        // one shade. Fine for a map.
        Biome biome = holder.value();
        int grass = BiomeColors.GRASS_COLOR_RESOLVER.getColor(biome, 0, 0);
        int foliage = BiomeColors.FOLIAGE_COLOR_RESOLVER.getColor(biome, 0, 0);
        int water = BiomeColors.WATER_COLOR_RESOLVER.getColor(biome, 0, 0);
        return new int[] {grass & 0xFFFFFF, foliage & 0xFFFFFF, water & 0xFFFFFF};
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int g = Math.round((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return r << 16 | g << 8 | b;
    }
}
