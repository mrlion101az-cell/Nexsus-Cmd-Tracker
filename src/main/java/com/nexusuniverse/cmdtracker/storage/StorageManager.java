package com.nexusuniverse.cmdtracker.storage;

import com.nexusuniverse.cmdtracker.model.CommandBlockEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists every known command block to commandblocks.yml, keyed by
 * "world;x;y;z" so re-scanning the same block just updates it in place
 * instead of duplicating it. Writes are debounced behind a dirty flag --
 * callers don't need to worry about save() being expensive to call often.
 */
public class StorageManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, CommandBlockEntry> entries = new LinkedHashMap<>();
    private boolean dirty = false;

    public StorageManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "commandblocks.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("entries");
        if (section == null) return;

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(key);
            if (e == null) continue;
            try {
                CommandBlockEntry entry = new CommandBlockEntry(
                        e.getString("world", ""),
                        e.getInt("x"), e.getInt("y"), e.getInt("z"),
                        CommandBlockEntry.Type.valueOf(e.getString("type", "IMPULSE")),
                        e.getString("command", ""),
                        e.getLong("lastSeen", 0L));
                entries.put(key, entry);
                loaded++;
            } catch (Exception ex) {
                plugin.getLogger().warning("NexusCmdTracker: couldn't load commandblocks.yml entry \"" + key + "\", skipping it.");
            }
        }
        plugin.getLogger().info("NexusCmdTracker: loaded " + loaded + " known command block(s) from disk.");
    }

    /** Only actually writes to disk if something changed since the last save. */
    public void saveIfDirty() {
        if (!dirty) return;
        save();
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, CommandBlockEntry> e : entries.entrySet()) {
            String base = "entries." + e.getKey() + ".";
            CommandBlockEntry v = e.getValue();
            data.set(base + "world", v.world());
            data.set(base + "x", v.x());
            data.set(base + "y", v.y());
            data.set(base + "z", v.z());
            data.set(base + "type", v.type().name());
            data.set(base + "command", v.command());
            data.set(base + "lastSeen", v.lastSeen());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "NexusCmdTracker: failed to save commandblocks.yml", ex);
        }
    }

    public void upsert(CommandBlockEntry entry) {
        entries.put(entry.key(), entry);
        dirty = true;
    }

    public void remove(String key) {
        if (entries.remove(key) != null) dirty = true;
    }

    public void removeAt(String world, int x, int y, int z) {
        remove(CommandBlockEntry.key(world, x, y, z));
    }

    public Collection<CommandBlockEntry> all() {
        return entries.values();
    }

    public List<CommandBlockEntry> allSorted() {
        List<CommandBlockEntry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> {
            int worldCmp = a.world().compareTo(b.world());
            if (worldCmp != 0) return worldCmp;
            int xCmp = Integer.compare(a.x(), b.x());
            if (xCmp != 0) return xCmp;
            int zCmp = Integer.compare(a.z(), b.z());
            if (zCmp != 0) return zCmp;
            return Integer.compare(a.y(), b.y());
        });
        return list;
    }

    public int size() {
        return entries.size();
    }
}
