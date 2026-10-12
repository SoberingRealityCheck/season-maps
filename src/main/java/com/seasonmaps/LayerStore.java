package com.seasonmaps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server side. Holds the Layer for each live MapItemSavedData and keeps them on disk.
 * This is the dishonest edge: all the file io is here, Layer itself has none.
 *
 * Why not store it inside the vanilla map data? The map codec is a fixed record,
 * and the data object doesn't know its own map id. So the layer hangs off the data
 * object in memory (WeakHashMap, dies with the map) and is saved by the map's
 * dimension + center + scale instead. Two map ids over the same square share a file.
 * That's fine because biome is a property of the place.
 *
 * Hack: files go in <world>/seasonmaps/, not the vanilla data dir.
 */
public final class LayerStore {
    private static final Map<MapItemSavedData, Layer> LIVE = new WeakHashMap<>();

    private LayerStore() {}

    public static synchronized Layer of(MinecraftServer server, MapItemSavedData data) {
        Layer layer = LIVE.get(data);
        if (layer == null) {
            layer = load(file(server, data));
            LIVE.put(data, layer);
        }
        return layer;
    }

    /** Writes every dirty layer. Called every few minutes and on stop. */
    public static synchronized void saveDirty(MinecraftServer server) {
        for (Map.Entry<MapItemSavedData, Layer> e : LIVE.entrySet()) {
            Layer layer = e.getValue();
            if (!layer.dirty) continue;
            layer.dirty = false;
            try {
                Path path = file(server, e.getKey());
                Files.createDirectories(path.getParent());
                Files.write(path, layer.encode());
            } catch (IOException ex) {
                layer.dirty = true; // try again next time
                SeasonMapsMod.LOGGER.warn("Could not save a map biome layer", ex);
            }
        }
    }

    private static Path file(MinecraftServer server, MapItemSavedData d) {
        String dim = d.dimension.identifier().toString().replace(':', '_');
        return server.getWorldPath(LevelResource.ROOT).resolve("seasonmaps")
                .resolve(dim + "_" + d.centerX + "_" + d.centerZ + "_" + d.scale + ".bin");
    }

    private static Layer load(Path path) {
        try {
            if (Files.exists(path)) {
                Layer layer = Layer.decode(Files.readAllBytes(path));
                if (layer != null) return layer;
            }
        } catch (IOException ex) {
            SeasonMapsMod.LOGGER.warn("Could not read a map biome layer, starting empty", ex);
        }
        return new Layer();
    }
}
