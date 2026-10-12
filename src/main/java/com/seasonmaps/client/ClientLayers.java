package com.seasonmaps.client;

import com.seasonmaps.Layer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Client side: the biome layer the server last sent for each map id. Render thread only. */
public final class ClientLayers {
    private static final Int2ObjectMap<Layer> LAYERS = new Int2ObjectOpenHashMap<>();
    private static int counter;

    private ClientLayers() {}

    public static void put(int mapId, Layer layer) {
        layer.version = ++counter; // client numbers its own versions, the server's don't matter here
        LAYERS.put(mapId, layer);
    }

    public static Layer get(int mapId) {
        return LAYERS.get(mapId);
    }

    public static void clear() {
        LAYERS.clear();
    }
}
