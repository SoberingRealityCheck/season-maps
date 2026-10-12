package com.seasonmaps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Dev only. Run with: ./gradlew runClient -PclientTest
 * Opens the game, checks the tint rules against the game's own BlockColors, prints PASS/FAIL,
 * and quits. No world needed: biomes come from the built-in vanilla data, not a ClientLevel.
 * Does not look at pixels. It checks which rule each block gets, not how it looks.
 */
final class ClientSelfTest {
    private ClientSelfTest() {}

    static void run(Minecraft mc) {
        boolean pass = true;
        try {
            Biome plains = VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME)
                    .getOrThrow(Biomes.PLAINS).value();

            int spruce = Tint.blockTint(plains, "minecraft:spruce_leaves");
            int oak = Tint.blockTint(plains, "minecraft:oak_leaves");
            int cherry = Tint.blockTint(plains, "minecraft:cherry_leaves");
            int tulip = Tint.blockTint(plains, "minecraft:red_tulip");
            int grass = Tint.blockTint(plains, "minecraft:grass_block");
            int expectFoliage = BiomeColors.FOLIAGE_COLOR_RESOLVER.getColor(plains, 0, 0) & 0xFFFFFF;
            int expectGrass = BiomeColors.GRASS_COLOR_RESOLVER.getColor(plains, 0, 0) & 0xFFFFFF;

            // Vanilla's constants, from BlockColors.createDefault(): spruce -10380959, birch -8345771.
            pass &= check("spruce leaves are a fixed color, not biome foliage", spruce == (-10380959 & 0xFFFFFF), spruce);
            // Not checked here: birch. Serene's birch tint source reads the local player's level,
            // so it throws with no world loaded. Use /seasonmapstint in a world for birch, oak
            // and grass real values.
            // The resolvers return 0 with no world loaded (Serene bails), so these two only prove
            // we pass the resolver's answer through. The real values are in /seasonmapstint.
            pass &= check("oak leaves pass through the foliage resolver", oak == expectFoliage, oak);
            pass &= check("cherry leaves have no tint", cherry == -1, cherry);
            pass &= check("a tulip has no tint", tulip == -1, tulip);
            pass &= check("grass block passes through the grass resolver", grass == expectGrass, grass);

            // Client mixin applied? It only loads when a map first renders, so force it.
            boolean mixin = false;
            for (var m : Class.forName("net.minecraft.client.resources.MapTextureManager$MapInstance").getDeclaredMethods()) {
                if (m.getName().contains("seasonmaps$paintTinted")) mixin = true;
            }
            pass &= check("client mixin applied to MapInstance", mixin, mixin ? 1 : 0);
        } catch (Throwable t) {
            t.printStackTrace();
            pass = false;
        }
        System.out.println("[seasonmaps-clienttest] " + (pass ? "PASS" : "FAIL"));
        mc.stop();
    }

    private static boolean check(String what, boolean ok, int value) {
        System.out.println("[seasonmaps-clienttest] " + (ok ? "ok   " : "FAIL ") + what + " (value " + (value < 0 ? "none" : String.format("0x%06X", value)) + ")");
        return ok;
    }
}
