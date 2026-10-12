package com.seasonmaps;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the biome layer for one map id. `data` is Layer.encode(). */
public record LayerPayload(int mapId, byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LayerPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SeasonMapsMod.MOD_ID, "layer"));
    public static final StreamCodec<FriendlyByteBuf, LayerPayload> CODEC =
            CustomPacketPayload.codec(LayerPayload::write, LayerPayload::new);

    public LayerPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readByteArray());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(mapId);
        buf.writeByteArray(data);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
