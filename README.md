# Season Maps

Biome-tinted, season-aware colors for vanilla maps. Fabric, Minecraft 26.2. Needs Serene Seasons.

Vanilla maps store a material per pixel (grass, plant, water...) and no biome, so every
grass pixel is the same green. This mod adds the missing biome.

## How it works

**Server, as you map.** Vanilla's `MapItem.update` scans one pixel at a time. We hook the
`updateColor` call and also write down the biome at that spot. For grass and plant pixels it
writes down the surface block too (oak leaves, spruce leaves, a tulip...). That's one lookup
per pixel, once, and the layer is saved in `<world>/seasonmaps/`.

**Server to client.** Any map packet the server sends (vanilla's or Ultimate Map Atlases')
is followed by the biome layer if the player hasn't seen the current one. About 300 bytes to
2 KB deflated.

**Client, when the map is drawn.** Vanilla keeps a texture per map. We redraw it when the
layer changes or the Serene Seasons sub-season changes. Water pixels get the biome water color.
Grass and plant pixels ask the game's own `BlockColors` what that block is tinted: spruce and
birch leaves are fixed colors, oak is biome foliage, cherry leaves and flowers get none. Serene
Seasons registers its changes into the same place, so they come through. Everything else stays
vanilla. Colors are cached per biome and block, and thrown away when the season changes.

Ultimate Map Atlases draws through the same vanilla texture, so atlases get tint for free.

## Limits

- Maps made before the mod have no layer. They stay vanilla until re-scanned.
- Locked maps and zoomed-out copies get a new map file, so they start without a layer.
- Two map ids over the same square share one layer file. Fine, biome belongs to the place.
- No throttle on layer sends yet. Every map packet with a changed layer carries it.
- The tint strength (`Tint.STRENGTH`) was picked by eye.
- Plant pixels with no recorded block (old maps, scale 1+ where the corner block disagrees)
  fall back to the biome foliage color.
- Swamp grass is one shade per swamp (the in-game color is noise on x/z).

## Dev

```
JAVA_HOME=<a JDK 25> ./gradlew build
JAVA_HOME=<a JDK 25> ./gradlew runServer              # SelfTest: scans a map, checks the layer, prints PASS/FAIL
JAVA_HOME=<a JDK 25> ./gradlew runClient -PclientTest  # ClientSelfTest: checks tint rules, opens a window briefly
JAVA_HOME=<a JDK 25> ./gradlew runClient               # play. /seasonmapstint prints the tint per block here
```

`libs/` needs `SereneSeasons-fabric-26.2-*.jar` and `GlitchCore-fabric-26.2-*.jar` (not committed).
`SelfTest` only runs with `-Dseasonmaps.selftest=true`, which `runServer` sets in `build.gradle`.
