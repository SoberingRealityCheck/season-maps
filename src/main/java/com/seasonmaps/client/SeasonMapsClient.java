package com.seasonmaps.client;

import com.seasonmaps.Layer;
import com.seasonmaps.LayerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

public final class SeasonMapsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LayerPayload.ID, (payload, context) -> {
            Layer layer = Layer.decode(payload.data());
            if (layer != null) ClientLayers.put(payload.mapId(), layer);
        });
        if (System.getProperty("seasonmaps.clienttest") != null) {
            ClientLifecycleEvents.CLIENT_STARTED.register(ClientSelfTest::run);
        }

        // /seasonmapstint: what tint each kind of block gets in the biome you are standing in.
        // For checking the tint rules without making a map.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("seasonmapstint").executes(ctx -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    var holder = mc.level.getBiome(mc.player.blockPosition());
                    Biome biome = holder.value();
                    String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
                    ctx.getSource().sendFeedback(Component.literal("biome " + id + ", season key " + Tint.seasonKey()));
                    for (String block : new String[] {"grass_block", "short_grass", "oak_leaves", "spruce_leaves",
                            "birch_leaves", "jungle_leaves", "cherry_leaves", "azalea_leaves", "red_tulip", "wheat"}) {
                        int c = Tint.blockTint(biome, "minecraft:" + block);
                        ctx.getSource().sendFeedback(Component.literal("  " + block + ": "
                                + (c == -1 ? "no tint" : String.format("#%06X", c))));
                    }
                    return 1;
                })));

        // Map ids restart per world, so drop everything when we leave one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientLayers.clear());
    }
}
