package com.seasonmaps.mixin;

import com.seasonmaps.Layer;
import com.seasonmaps.LayerPayload;
import com.seasonmaps.LayerStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every map update the server sends, vanilla's or Ultimate Map Atlases', goes through
 * send(). Hooking here means we don't care who built the packet.
 *
 * Known gap: we send the whole layer (about 1-2 KB deflated) alongside any map packet
 * when the layer changed since we last sent it to that player. No throttle yet.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void seasonmaps$sendLayer(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundMapItemDataPacket map)) return;
        if (!((Object) this instanceof ServerGamePacketListenerImpl listener)) return;
        ServerPlayer player = listener.player;
        if (!ServerPlayNetworking.canSend(player, LayerPayload.ID)) return;

        MapItemSavedData data = player.level().getMapData(map.mapId());
        if (data == null) return;
        Layer layer = LayerStore.of(player.level().getServer(), data);
        if (layer.version == 0) return; // nothing recorded yet
        if (!layer.markSent(player.getUUID())) return;

        ServerPlayNetworking.send(player, new LayerPayload(map.mapId().id(), layer.encode()));
    }
}
