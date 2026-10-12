package com.seasonmaps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The biome of every pixel on one map. Same 128x128 grid as the map colors.
 * idx[i] is 0 for "unknown", else palette index + 1. The palette is a list
 * of biome ids, so saved layers survive the biome registry changing order.
 *
 * Plain data plus a few pure functions. No disk, no network. See LayerStore
 * for the disk side.
 */
public final class Layer {
    public static final int SIZE = 128 * 128;
    // idx is one byte per pixel and 0 means unknown, so 255 biomes fit.
    // A map that crosses more than 255 biomes shows the rest as unknown
    // (vanilla color). Never seen in practice at map scale 0-4.
    private static final int MAX_PALETTE = 255;

    public final byte[] idx = new byte[SIZE];
    public final List<String> palette = new ArrayList<>();
    // Same idea for the surface block, but only filled for grass and plant pixels (grass,
    // leaves, flowers...). Lets the client ask the game what tint that block gets, so spruce
    // and birch leaves, flowers and crops don't all get one "plant" green. 0 = not recorded.
    public final byte[] blk = new byte[SIZE];
    public final List<String> blockPalette = new ArrayList<>();
    // Bumps on every real change. Server uses it to skip sends, client to skip re-uploads.
    public int version;
    public boolean dirty;
    // Server only: which version each player was last sent. Players who log out
    // stay in here until the map unloads. Tiny, and a rejoin just re-sends.
    private final Map<UUID, Integer> sentVersion = new HashMap<>();

    /** True if this player hasn't seen the current version. Records that they now have. */
    public boolean markSent(UUID player) {
        Integer last = sentVersion.put(player, version);
        return last == null || last != version;
    }

    /** Sets one pixel. block may be null. Returns true if it changed anything. */
    public boolean set(int px, int py, String biome, String block) {
        if (px < 0 || py < 0 || px >= 128 || py >= 128) return false;
        int i = px + py * 128;
        byte b = (byte) (intern(palette, biome) + 1);
        byte k = block == null ? 0 : (byte) (intern(blockPalette, block) + 1);
        if (idx[i] == b && blk[i] == k) return false;
        idx[i] = b;
        blk[i] = k;
        version++;
        dirty = true;
        return true;
    }

    /** Index of s in list, adding it if new. -1 if the list is full (pixel shows as unknown). */
    private static int intern(List<String> list, String s) {
        int p = list.indexOf(s);
        if (p >= 0) return p;
        if (list.size() >= MAX_PALETTE) return -1;
        list.add(s);
        return list.size() - 1;
    }

    /** Biome id at a pixel, or null for unknown. */
    public String biomeAt(int px, int py) {
        int v = idx[px + py * 128] & 0xFF;
        return v == 0 || v > palette.size() ? null : palette.get(v - 1);
    }

    /** Surface block id at a pixel, or null if none was recorded. */
    public String blockAt(int px, int py) {
        int v = blk[px + py * 128] & 0xFF;
        return v == 0 || v > blockPalette.size() ? null : blockPalette.get(v - 1);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
                out.writeByte(2); // format version
                out.writeShort(palette.size());
                for (String s : palette) out.writeUTF(s);
                out.write(idx);
                out.writeShort(blockPalette.size());
                for (String s : blockPalette) out.writeUTF(s);
                out.write(blk);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array can't fail
        }
    }

    /** Returns null on anything malformed. A bad layer is just "no tint", never a crash. */
    public static Layer decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int format = in.readByte();
            if (format != 1 && format != 2) return null;
            Layer layer = new Layer();
            int n = in.readUnsignedShort();
            if (n > MAX_PALETTE) return null;
            for (int i = 0; i < n; i++) layer.palette.add(in.readUTF());
            in.readFully(layer.idx);
            if (format == 2) { // format 1 files (biome only) just have no blocks
                int m = in.readUnsignedShort();
                if (m > MAX_PALETTE) return null;
                for (int i = 0; i < m; i++) layer.blockPalette.add(in.readUTF());
                in.readFully(layer.blk);
            }
            layer.version = 1; // 0 means "empty, nothing to send"
            return layer;
        } catch (IOException e) {
            return null;
        }
    }
}
