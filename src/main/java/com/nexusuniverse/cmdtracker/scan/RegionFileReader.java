package com.nexusuniverse.cmdtracker.scan;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Reads the Anvil region file (.mca) header to find out which chunks in a
 * world have actually been generated, without loading a single one of them
 * through the Bukkit API. This is what keeps a full-world scan from wasting
 * time force-loading thousands of chunks of wilderness nobody's ever
 * visited -- only chunks the header says already exist get queued.
 *
 * Format (unchanged since the Anvil format replaced McRegion in 1.2, so
 * this is safe against version drift): each region file covers a 32x32
 * area of chunks and starts with a 4096-byte location table -- 1024
 * 4-byte big-endian entries, one per chunk, index = (chunkX &amp; 31) +
 * (chunkZ &amp; 31) * 32. An all-zero entry means that chunk has never
 * been generated; anything else means it's on disk.
 */
public final class RegionFileReader {

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    private RegionFileReader() {
    }

    /** Finds every generated chunk across every region file in the given region folder. */
    public static List<int[]> discoverChunks(File regionFolder, Logger logger) {
        List<int[]> chunks = new ArrayList<>();
        File[] files = regionFolder.listFiles();
        if (files == null) return chunks;

        for (File file : files) {
            Matcher matcher = REGION_FILE_PATTERN.matcher(file.getName());
            if (!matcher.matches()) continue;

            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            try {
                chunks.addAll(readRegionHeader(file, regionX, regionZ));
            } catch (IOException e) {
                logger.warning("NexusCmdTracker: couldn't read region file " + file.getName() + ", skipping it: " + e.getMessage());
            }
        }
        return chunks;
    }

    private static List<int[]> readRegionHeader(File file, int regionX, int regionZ) throws IOException {
        List<int[]> chunks = new ArrayList<>();
        byte[] header = new byte[4096];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 4096) return chunks; // truncated/empty region file
            raf.readFully(header);
        }

        for (int i = 0; i < 1024; i++) {
            int offset = i * 4;
            int entry = ((header[offset] & 0xFF) << 24) | ((header[offset + 1] & 0xFF) << 16)
                    | ((header[offset + 2] & 0xFF) << 8) | (header[offset + 3] & 0xFF);
            if (entry == 0) continue; // chunk never generated

            int localX = i % 32;
            int localZ = i / 32;
            chunks.add(new int[]{regionX * 32 + localX, regionZ * 32 + localZ});
        }
        return chunks;
    }
}
