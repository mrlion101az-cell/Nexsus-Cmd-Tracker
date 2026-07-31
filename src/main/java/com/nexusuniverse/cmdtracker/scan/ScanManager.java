package com.nexusuniverse.cmdtracker.scan;

import com.nexusuniverse.cmdtracker.model.CommandBlockEntry;
import com.nexusuniverse.cmdtracker.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Two ways command blocks get indexed:
 *  1. A full scan ({@link #startFullScan}) -- paced across ticks so it doesn't stall the
 *     server, walks every chunk the region files say has ever been generated.
 *  2. Passively, all the time -- {@link ChunkLoadEvent}/{@link BlockPlaceEvent}/
 *     {@link BlockBreakEvent} keep the index self-healing as people play, catching
 *     anything a scan missed and anything placed or removed since.
 */
public class ScanManager implements Listener {

    private static final Set<Material> COMMAND_BLOCK_TYPES = EnumSet.of(
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK);

    private final Plugin plugin;
    private final StorageManager storage;

    private BukkitTask activeTask;
    private Deque<int[]> queue;
    private World scanningWorld;
    private CommandSender initiator;
    private int scannedCount;
    private int foundCount;
    private int totalCount;

    public ScanManager(Plugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean isScanning() {
        return activeTask != null;
    }

    /** Full scan: every chunk the world's region files say has ever been generated. */
    public void startFullScan(World world, CommandSender initiator) {
        if (isScanning()) {
            initiator.sendMessage(ChatColor.RED + "A scan is already running -- use /cmdfinder stop first if you want to restart it.");
            return;
        }

        List<File> regionDirs = findRegionDirs(world);
        if (regionDirs.isEmpty()) {
            initiator.sendMessage(ChatColor.RED + "Couldn't find a region folder for \"" + world.getName() + "\" -- is that the right world?");
            return;
        }

        this.queue = new ArrayDeque<>();
        for (File dir : regionDirs) {
            queue.addAll(RegionFileReader.discoverChunks(dir, plugin.getLogger()));
        }
        this.scanningWorld = world;
        this.initiator = initiator;
        this.scannedCount = 0;
        this.foundCount = 0;
        this.totalCount = queue.size();

        initiator.sendMessage(ChatColor.YELLOW + "Scanning " + totalCount + " generated chunks in \"" + world.getName()
                + "\"... this runs in the background, the server will stay responsive.");

        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("scan.chunks-per-tick", 4));
        int progressInterval = Math.max(1, plugin.getConfig().getInt("scan.progress-message-interval", 500));
        activeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(chunksPerTick, progressInterval), 20L, 1L);
    }

    public void stop(CommandSender sender) {
        if (!isScanning()) {
            sender.sendMessage(ChatColor.RED + "No scan is currently running.");
            return;
        }
        activeTask.cancel();
        activeTask = null;
        storage.save();
        sender.sendMessage(ChatColor.YELLOW + "Scan stopped early at " + scannedCount + "/" + totalCount
                + " chunks (" + foundCount + " command block(s) found so far, saved).");
    }

    public String status() {
        if (!isScanning()) return "No scan currently running. " + storage.size() + " command block(s) known.";
        return "Scanning \"" + scanningWorld.getName() + "\": " + scannedCount + "/" + totalCount
                + " chunks (" + foundCount + " found so far).";
    }

    /** Fast refresh: only re-checks chunks that are already loaded right now (no disk I/O for new chunks). */
    public void rescanLoadedChunks(World world, CommandSender initiator) {
        int found = 0;
        int checked = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            found += scanChunk(chunk);
            checked++;
        }
        storage.save();
        initiator.sendMessage(ChatColor.GREEN + "Rescanned " + checked + " currently-loaded chunk(s) in \""
                + world.getName() + "\" -- " + found + " command block(s) found/updated.");
    }

    private void tick(int chunksPerTick, int progressInterval) {
        for (int i = 0; i < chunksPerTick; i++) {
            int[] coords = queue.poll();
            if (coords == null) {
                finish();
                return;
            }
            processChunk(coords[0], coords[1]);
        }
        if (scannedCount % progressInterval < chunksPerTick && initiator != null) {
            initiator.sendMessage(ChatColor.GRAY + "NexusCmdTracker scan: " + scannedCount + "/" + totalCount
                    + " chunks (" + foundCount + " found so far)...");
        }
    }

    private void processChunk(int chunkX, int chunkZ) {
        boolean wasLoaded = scanningWorld.isChunkLoaded(chunkX, chunkZ);
        Chunk chunk = scanningWorld.getChunkAt(chunkX, chunkZ);
        foundCount += scanChunk(chunk);
        if (!wasLoaded) {
            chunk.unload(false);
        }
        scannedCount++;
    }

    /** Scans one chunk's tile entities for command blocks, indexing anything found. Returns how many were found. */
    private int scanChunk(Chunk chunk) {
        int found = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CommandBlock commandBlock) {
                storage.upsert(new CommandBlockEntry(
                        chunk.getWorld().getName(), state.getX(), state.getY(), state.getZ(),
                        typeOf(state.getType()), commandBlock.getCommand(), System.currentTimeMillis()));
                found++;
            }
        }
        return found;
    }

    private void finish() {
        activeTask.cancel();
        activeTask = null;
        storage.save();
        if (initiator != null) {
            initiator.sendMessage(ChatColor.GREEN + "Scan complete: " + foundCount + " command block(s) found across "
                    + scannedCount + " chunks in \"" + scanningWorld.getName() + "\".");
        }
    }

    private static CommandBlockEntry.Type typeOf(Material material) {
        return switch (material) {
            case CHAIN_COMMAND_BLOCK -> CommandBlockEntry.Type.CHAIN;
            case REPEATING_COMMAND_BLOCK -> CommandBlockEntry.Type.REPEATING;
            default -> CommandBlockEntry.Type.IMPULSE;
        };
    }

    /**
     * A world's command-block-bearing chunks can live in more than one folder depending on
     * server layout (a single-world setup keeps the nether/end under DIM-1/DIM1 inside the
     * overworld's folder; a multi-world setup gives each dimension its own top-level world
     * folder with its own region/ directory) -- check both rather than assuming one.
     */
    private List<File> findRegionDirs(World world) {
        List<File> dirs = new java.util.ArrayList<>();
        File worldFolder = world.getWorldFolder();
        for (String candidate : new String[]{"region", "DIM-1/region", "DIM1/region"}) {
            File dir = new File(worldFolder, candidate);
            if (dir.isDirectory()) dirs.add(dir);
        }
        return dirs;
    }

    // ---- passive indexing, keeps the index self-healing during normal play ----

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
        // no immediate save here -- the autosave task in the main plugin class debounces this
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!COMMAND_BLOCK_TYPES.contains(event.getBlockPlaced().getType())) return;
        BlockState state = event.getBlockPlaced().getState();
        if (state instanceof CommandBlock commandBlock) {
            storage.upsert(new CommandBlockEntry(
                    event.getBlock().getWorld().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(),
                    typeOf(state.getType()), commandBlock.getCommand(), System.currentTimeMillis()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!COMMAND_BLOCK_TYPES.contains(event.getBlock().getType())) return;
        storage.removeAt(event.getBlock().getWorld().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
    }
}
