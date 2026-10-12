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

    /** Sets one pixel. Returns true if it changed anything. */
    public boolean set(int px, int py, String biome) {
        if (px < 0 || py < 0 || px >= 128 || py >= 128) return false;
        int p = palette.indexOf(biome);
        if (p < 0) {
            if (palette.size() >= MAX_PALETTE) return false;
            palette.add(biome);
            p = palette.size() - 1;
        }
        byte value = (byte) (p + 1);
        int i = px + py * 128;
        if (idx[i] == value) return false;
        idx[i] = value;
        version++;
        dirty = true;
        return true;
    }

    /** Biome id at a pixel, or null for unknown. */
    public String biomeAt(int px, int py) {
        int v = idx[px + py * 128] & 0xFF;
        return v == 0 || v > palette.size() ? null : palette.get(v - 1);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
                out.writeByte(1); // format version
                out.writeShort(palette.size());
                for (String s : palette) out.writeUTF(s);
                out.write(idx);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array can't fail
        }
    }

    /** Returns null on anything malformed. A bad layer is just "no tint", never a crash. */
    public static Layer decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            if (in.readByte() != 1) return null;
            Layer layer = new Layer();
            int n = in.readUnsignedShort();
            if (n > MAX_PALETTE) return null;
            for (int i = 0; i < n; i++) layer.palette.add(in.readUTF());
            in.readFully(layer.idx);
            layer.version = 1; // 0 means "empty, nothing to send"
            return layer;
        } catch (IOException e) {
            return null;
        }
    }
}
