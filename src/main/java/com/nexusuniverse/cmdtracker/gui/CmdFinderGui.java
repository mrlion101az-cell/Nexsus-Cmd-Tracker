package com.nexusuniverse.cmdtracker.gui;

import com.nexusuniverse.cmdtracker.model.CommandBlockEntry;
import com.nexusuniverse.cmdtracker.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the two-level browsing GUI: a filter picker (by what the command
 * actually does, matched by keyword against its text -- not a stored
 * category, so it stays accurate even for command blocks indexed before a
 * filter existed), then a paginated list of matches within that filter.
 */
public class CmdFinderGui {

    private static final int ITEMS_PER_PAGE = 45;
    public static final String ALL = "All";
    public static final String OTHER = "Other";

    // Order matters for display, not matching -- "All" and "Other" are handled specially.
    private static final Map<String, List<String>> FILTERS = new LinkedHashMap<>();
    static {
        FILTERS.put("Teleport", List.of("/tp", "teleport"));
        FILTERS.put("Time & Weather", List.of("time set", "/time", "weather"));
        FILTERS.put("Give & Item", List.of("/give", "/clear"));
        FILTERS.put("Summon", List.of("/summon"));
        FILTERS.put("Effect", List.of("/effect"));
    }

    private final Plugin plugin;
    private final StorageManager storage;
    private final NamespacedKey filterTag;

    public CmdFinderGui(Plugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.filterTag = new NamespacedKey(plugin, "cmdfinder_filter");
    }

    public static boolean matches(String command, String filterKey) {
        String lower = command.toLowerCase(Locale.ROOT);
        if (filterKey.equals(ALL)) return true;
        if (filterKey.equals(OTHER)) {
            for (List<String> keywords : FILTERS.values()) {
                for (String kw : keywords) {
                    if (lower.contains(kw)) return false;
                }
            }
            return true;
        }
        List<String> keywords = FILTERS.get(filterKey);
        if (keywords == null) return false;
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    public List<CommandBlockEntry> entriesFor(String filterKey) {
        List<CommandBlockEntry> result = new ArrayList<>();
        for (CommandBlockEntry entry : storage.allSorted()) {
            if (matches(entry.command(), filterKey)) result.add(entry);
        }
        return result;
    }

    public Inventory buildFilterMenu() {
        List<String> keys = new ArrayList<>();
        keys.add(ALL);
        keys.addAll(FILTERS.keySet());
        keys.add(OTHER);

        int size = Math.min(54, Math.max(9, ((keys.size() + 8) / 9) * 9));
        CmdFinderMenuHolder holder = new CmdFinderMenuHolder(CmdFinderMenuHolder.Type.FILTER_LIST, null, 0);
        Inventory inv = Bukkit.createInventory(holder, size, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Command Block Finder");
        holder.setInventory(inv);

        int slot = 0;
        for (String key : keys) {
            int count = entriesFor(key).size();
            ItemStack item = new ItemStack(iconFor(key));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + key);
            meta.setLore(List.of(ChatColor.GRAY + "" + count + " command block" + (count == 1 ? "" : "s")));
            meta.getPersistentDataContainer().set(filterTag, PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        return inv;
    }

    public Inventory buildEntryPage(String filterKey, int page) {
        List<CommandBlockEntry> entries = entriesFor(filterKey);
        int totalPages = Math.max(1, (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        CmdFinderMenuHolder holder = new CmdFinderMenuHolder(CmdFinderMenuHolder.Type.ENTRY_PAGE, filterKey, page);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + filterKey
                + ChatColor.GRAY + " (" + (page + 1) + "/" + totalPages + ")");
        holder.setInventory(inv);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            CommandBlockEntry entry = entries.get(i);
            try {
                ItemStack item = new ItemStack(iconFor(entry.type()));
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.WHITE + entry.world() + ": " + entry.x() + ", " + entry.y() + ", " + entry.z());
                String cmd = entry.command();
                String preview = cmd.length() > 40 ? cmd.substring(0, 40) + "..." : cmd;
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + (preview.isEmpty() ? "(empty command)" : preview));
                lore.add(ChatColor.DARK_GRAY + entry.type().name().toLowerCase(Locale.ROOT) + " command block");
                lore.add(ChatColor.GREEN + "Left-click: teleport here");
                lore.add(ChatColor.YELLOW + "Right-click: show full command in chat");
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i - start, item);
            } catch (Exception ignored) {
                // one bad entry shouldn't take the whole page down
            }
        }

        if (page > 0) inv.setItem(45, navItem(Material.ARROW, "Previous Page"));
        inv.setItem(49, navItem(Material.BARRIER, "Back to Filters"));
        if (page < totalPages - 1) inv.setItem(53, navItem(Material.ARROW, "Next Page"));

        return inv;
    }

    public String filterKeyOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(filterTag, PersistentDataType.STRING);
    }

    private Material iconFor(String filterKey) {
        return switch (filterKey) {
            case "Teleport" -> Material.ENDER_PEARL;
            case "Time & Weather" -> Material.CLOCK;
            case "Give & Item" -> Material.CHEST;
            case "Summon" -> Material.ZOMBIE_SPAWN_EGG;
            case "Effect" -> Material.POTION;
            case OTHER -> Material.REPEATING_COMMAND_BLOCK;
            default -> Material.COMPASS;
        };
    }

    private Material iconFor(CommandBlockEntry.Type type) {
        return switch (type) {
            case CHAIN -> Material.CHAIN_COMMAND_BLOCK;
            case REPEATING -> Material.REPEATING_COMMAND_BLOCK;
            default -> Material.COMMAND_BLOCK;
        };
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }
}
