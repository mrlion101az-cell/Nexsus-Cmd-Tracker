package com.nexusuniverse.cmdtracker.command;

import com.nexusuniverse.cmdtracker.gui.CmdFinderGui;
import com.nexusuniverse.cmdtracker.model.CommandBlockEntry;
import com.nexusuniverse.cmdtracker.scan.ScanManager;
import com.nexusuniverse.cmdtracker.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CmdFinderCommand implements CommandExecutor {

    private final ScanManager scanManager;
    private final StorageManager storage;
    private final CmdFinderGui gui;

    // The last /cmdfinder list results shown to each player, so "/cmdfinder tp <n>" can
    // reference them by the number printed in chat without needing a stable global id.
    private final Map<UUID, List<CommandBlockEntry>> lastListing = new ConcurrentHashMap<>();

    public CmdFinderCommand(ScanManager scanManager, StorageManager storage, CmdFinderGui gui) {
        this.scanManager = scanManager;
        this.storage = storage;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "scan" -> handleScan(sender, args);
            case "stop" -> scanManager.stop(sender);
            case "status" -> sender.sendMessage(ChatColor.AQUA + scanManager.status());
            case "rescan" -> handleRescan(sender, args);
            case "gui" -> handleGui(sender);
            case "list" -> handleList(sender, args);
            case "tp" -> handleTp(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleScan(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args);
        if (world == null) return;
        scanManager.startFullScan(world, sender);
    }

    private void handleRescan(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args);
        if (world == null) return;
        scanManager.rescanLoadedChunks(world, sender);
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can open the GUI -- try /cmdfinder list instead.");
            return;
        }
        player.openInventory(gui.buildFilterMenu());
    }

    private void handleList(CommandSender sender, String[] args) {
        String filterKey = CmdFinderGui.ALL;
        if (args.length > 1) {
            String requested = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            filterKey = matchFilterName(requested);
        }

        List<CommandBlockEntry> entries = gui.entriesFor(filterKey);
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No command blocks found for \"" + filterKey + "\".");
            return;
        }

        if (sender instanceof Player player) {
            lastListing.put(player.getUniqueId(), entries);
        }

        sender.sendMessage(ChatColor.AQUA + "" + entries.size() + " command block(s) -- \"" + filterKey + "\":");
        int shown = 0;
        for (CommandBlockEntry entry : entries) {
            shown++;
            String preview = entry.command().length() > 50 ? entry.command().substring(0, 50) + "..." : entry.command();
            sender.sendMessage(ChatColor.GRAY + "" + shown + ". " + ChatColor.WHITE + entry.world() + " "
                    + entry.x() + "," + entry.y() + "," + entry.z() + ChatColor.GRAY + " -- " + preview);
            if (shown >= 50) {
                sender.sendMessage(ChatColor.DARK_GRAY + "(" + (entries.size() - shown) + " more -- use the GUI with /cmdfinder gui to see the rest)");
                break;
            }
        }
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GRAY + "Use /cmdfinder tp <number> to teleport to one of these.");
        }
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can teleport.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /cmdfinder tp <number> -- run /cmdfinder list first.");
            return;
        }
        List<CommandBlockEntry> listing = lastListing.get(player.getUniqueId());
        if (listing == null) {
            sender.sendMessage(ChatColor.RED + "Run /cmdfinder list first, then /cmdfinder tp <number>.");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "\"" + args[1] + "\" isn't a number.");
            return;
        }
        if (index < 0 || index >= listing.size()) {
            sender.sendMessage(ChatColor.RED + "That's not a valid number from your last /cmdfinder list.");
            return;
        }
        CommandBlockEntry entry = listing.get(index);
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World \"" + entry.world() + "\" isn't loaded right now.");
            return;
        }
        player.teleport(new org.bukkit.Location(world, entry.x() + 0.5, entry.y() + 1, entry.z() + 0.5));
        sender.sendMessage(ChatColor.GREEN + "Teleported to " + entry.x() + ", " + entry.y() + ", " + entry.z() + ".");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /cmdfinder remove <world> <x> <y> <z> -- for pruning an entry that's stale (block was removed by hand outside the game, etc).");
            return;
        }
        try {
            String world = args[1];
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            storage.removeAt(world, x, y, z);
            storage.save();
            sender.sendMessage(ChatColor.GREEN + "Removed (if it existed).");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Coordinates must be numbers.");
        }
    }

    private World resolveWorld(CommandSender sender, String[] args) {
        // an explicit world name as the last argument overrides the sender's current world
        if (args.length > 1) {
            World named = Bukkit.getWorld(args[args.length - 1]);
            if (named != null) return named;
        }
        if (sender instanceof Player player) return player.getWorld();
        sender.sendMessage(ChatColor.RED + "Console needs an explicit world name: /cmdfinder scan <world>");
        return null;
    }

    private String matchFilterName(String requested) {
        String lower = requested.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("all")) return CmdFinderGui.ALL;
        if (lower.equals("other")) return CmdFinderGui.OTHER;
        for (String key : new String[]{"Teleport", "Time & Weather", "Give & Item", "Summon", "Effect"}) {
            if (key.toLowerCase(Locale.ROOT).contains(lower) || lower.contains(key.toLowerCase(Locale.ROOT).split(" ")[0])) {
                return key;
            }
        }
        return CmdFinderGui.ALL;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "NexusCmdTracker");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder scan [world]" + ChatColor.GRAY + " -- full scan of every generated chunk (paced, background)");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder stop" + ChatColor.GRAY + " -- cancel a scan in progress");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder status" + ChatColor.GRAY + " -- scan progress / how many are known");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder rescan" + ChatColor.GRAY + " -- fast refresh of currently-loaded chunks only");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder gui" + ChatColor.GRAY + " -- browse/filter/teleport via inventory GUI");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder list [filter]" + ChatColor.GRAY + " -- chat listing (filters: teleport, time, give, summon, effect, other)");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder tp <number>" + ChatColor.GRAY + " -- teleport to a result from your last /cmdfinder list");
        sender.sendMessage(ChatColor.AQUA + "/cmdfinder remove <world> <x> <y> <z>" + ChatColor.GRAY + " -- prune a stale entry");
    }
}
