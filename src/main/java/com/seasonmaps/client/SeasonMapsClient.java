package com.seasonmaps.client;

import com.seasonmaps.Layer;
import com.seasonmaps.LayerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SeasonMapsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LayerPayload.ID, (payload, context) -> {
            Layer layer = Layer.decode(payload.data());
            if (layer != null) ClientLayers.put(payload.mapId(), layer);
        });
        // Map ids restart per world, so drop everything when we leave one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientLayers.clear());
    }
}
