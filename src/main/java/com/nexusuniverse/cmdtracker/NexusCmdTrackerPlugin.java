package com.nexusuniverse.cmdtracker;

import com.nexusuniverse.cmdtracker.command.CmdFinderCommand;
import com.nexusuniverse.cmdtracker.gui.CmdFinderGui;
import com.nexusuniverse.cmdtracker.gui.CmdFinderGuiListener;
import com.nexusuniverse.cmdtracker.scan.ScanManager;
import com.nexusuniverse.cmdtracker.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusCmdTrackerPlugin extends JavaPlugin {

    private StorageManager storage;
    private ScanManager scanManager;

    @Override
    public void onEnable() {
        getLogger().info("=== NexusCmdTracker v" + getDescription().getVersion() + " starting ===");

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.storage = new StorageManager(this);
        this.scanManager = new ScanManager(this, storage);
        CmdFinderGui gui = new CmdFinderGui(this, storage);

        Bukkit.getPluginManager().registerEvents(scanManager, this);
        Bukkit.getPluginManager().registerEvents(new CmdFinderGuiListener(gui), this);

        CmdFinderCommand command = new CmdFinderCommand(scanManager, storage, gui);
        getCommand("cmdfinder").setExecutor(command);

        long autosaveTicks = Math.max(1, getConfig().getLong("storage.autosave-interval-minutes", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, storage::saveIfDirty, autosaveTicks, autosaveTicks);

        getLogger().info("NexusCmdTracker: ready. " + storage.size() + " command block(s) known from previous scans.");
    }

    @Override
    public void onDisable() {
        if (scanManager != null && scanManager.isScanning()) {
            scanManager.stop(Bukkit.getConsoleSender());
        }
        if (storage != null) {
            storage.save();
        }
    }
}
