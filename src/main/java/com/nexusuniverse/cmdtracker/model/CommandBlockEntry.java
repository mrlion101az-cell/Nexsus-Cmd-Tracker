package com.nexusuniverse.cmdtracker.model;

/**
 * One known command block: where it is, what kind it is, and the command
 * text it held the last time we looked at it. There's no Bukkit event for
 * "a command block's text was edited through its GUI", so this can go
 * stale if someone edits one after it's been indexed -- {@code /cmdfinder
 * rescan} (loaded chunks only, fast) or a fresh {@code /cmdfinder scan}
 * is how that gets corrected.
 */
public record CommandBlockEntry(String world, int x, int y, int z, Type type, String command, long lastSeen) {

    public enum Type { IMPULSE, CHAIN, REPEATING }

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }
}
