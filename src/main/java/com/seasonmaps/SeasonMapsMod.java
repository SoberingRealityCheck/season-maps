package com.seasonmaps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeasonMapsMod implements ModInitializer {
    public static final String MOD_ID = "seasonmaps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int SAVE_EVERY_TICKS = 20 * 60 * 5;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(LayerPayload.ID, LayerPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % SAVE_EVERY_TICKS == 0) LayerStore.saveDirty(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(LayerStore::saveDirty);

        if (System.getProperty("seasonmaps.selftest") != null) {
            ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
        }
    }
}
