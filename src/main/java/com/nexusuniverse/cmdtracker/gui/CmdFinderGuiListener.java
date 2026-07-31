package com.nexusuniverse.cmdtracker.gui;

import com.nexusuniverse.cmdtracker.model.CommandBlockEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CmdFinderGuiListener implements Listener {

    private final CmdFinderGui gui;

    public CmdFinderGuiListener(CmdFinderGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CmdFinderMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        if (holder.type() == CmdFinderMenuHolder.Type.FILTER_LIST) {
            String filterKey = gui.filterKeyOf(clicked);
            if (filterKey != null) {
                player.openInventory(gui.buildEntryPage(filterKey, 0));
            }
            return;
        }

        handleEntryPageClick(player, holder, event, clicked);
    }

    private void handleEntryPageClick(Player player, CmdFinderMenuHolder holder, InventoryClickEvent event, ItemStack clicked) {
        int slot = event.getSlot();

        if (slot == 49 && clicked.getType() == Material.BARRIER) {
            player.openInventory(gui.buildFilterMenu());
            return;
        }
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            player.openInventory(gui.buildEntryPage(holder.filterKey(), holder.page() - 1));
            return;
        }
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            player.openInventory(gui.buildEntryPage(holder.filterKey(), holder.page() + 1));
            return;
        }
        if (slot >= 45) return;

        List<CommandBlockEntry> entries = gui.entriesFor(holder.filterKey());
        int index = (holder.page() * 45) + slot;
        if (index < 0 || index >= entries.size()) return;
        CommandBlockEntry entry = entries.get(index);

        ClickType click = event.getClick();
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            player.sendMessage(ChatColor.AQUA + "[" + entry.world() + " " + entry.x() + "," + entry.y() + "," + entry.z() + "] "
                    + ChatColor.WHITE + (entry.command().isEmpty() ? ChatColor.DARK_GRAY + "(empty command)" : entry.command()));
            return;
        }

        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World \"" + entry.world() + "\" isn't loaded right now.");
            return;
        }
        player.closeInventory();
        player.teleport(new Location(world, entry.x() + 0.5, entry.y() + 1, entry.z() + 0.5));
        player.sendMessage(ChatColor.GREEN + "Teleported to the " + entry.type().name().toLowerCase() + " command block at "
                + entry.x() + ", " + entry.y() + ", " + entry.z() + ".");
    }
}
