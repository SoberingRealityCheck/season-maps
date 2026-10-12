package com.seasonmaps;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Dev only. `./gradlew runServer` sets -Dseasonmaps.selftest=true (see build.gradle).
 * Scans a real map at world spawn through the real MapItem.update, so it proves the
 * mixin applied and recorded biomes. Prints PASS or FAIL, then stops the server.
 * Does not cover the client half (needs a window).
 */
final class SelfTest {
    private SelfTest() {}

    static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            var spawn = level.getRespawnData().pos();
            MapItemSavedData data = MapItemSavedData.createFresh(spawn.getX(), spawn.getZ(), (byte) 2, true, false, Level.OVERWORLD);
            var player = FakePlayer.get(level);
            player.setPos(spawn.getX(), 80, spawn.getZ());
            MapItem map = (MapItem) Items.FILLED_MAP;

            // update() only scans every 16th column per call, so call it 16+ times.
            for (int i = 0; i < 20; i++) map.update(level, player, data);

            Layer layer = LayerStore.of(server, data);
            int known = 0;
            for (byte b : layer.idx) if (b != 0) known++;
            System.out.println("[seasonmaps-selftest] pixels with a biome: " + known + " of " + Layer.SIZE);
            System.out.println("[seasonmaps-selftest] palette: " + layer.palette + " blocks " + layer.blockPalette);

            Layer back = Layer.decode(layer.encode());
            boolean roundtrip = back != null && java.util.Arrays.equals(back.idx, layer.idx) && back.palette.equals(layer.palette)
                    && java.util.Arrays.equals(back.blk, layer.blk) && back.blockPalette.equals(layer.blockPalette);
            System.out.println("[seasonmaps-selftest] encode/decode roundtrip: " + roundtrip);
            System.out.println("[seasonmaps-selftest] encoded size: " + layer.encode().length + " bytes");

            LayerStore.saveDirty(server);
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("seasonmaps");
            long files;
            try (var s = Files.list(dir)) { files = s.count(); }
            System.out.println("[seasonmaps-selftest] files saved: " + files);

            // No player connects in this test, so force the packet class to load. A mixin that
            // can't apply (defaultRequire=1) throws here instead of at the first real player.
            boolean sendHook = false;
            for (var m : Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl").getDeclaredMethods()) {
                // mixin renames the handler to handler$xxx$seasonmaps$sendLayer
                if (m.getName().contains("seasonmaps$sendLayer")) sendHook = true;
            }
            System.out.println("[seasonmaps-selftest] send hook applied: " + sendHook);

            boolean pass = known > 1000 && roundtrip && files == 1 && sendHook;
            System.out.println("[seasonmaps-selftest] " + (pass ? "PASS" : "FAIL"));
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[seasonmaps-selftest] FAIL (exception)");
        }
        server.halt(false);
    }
}
