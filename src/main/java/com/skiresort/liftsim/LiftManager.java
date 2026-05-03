package com.skiresort.liftsim;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LiftManager {

    private final LiftSim plugin;
    private final Map<String, LiftDefinition> definitions = new LinkedHashMap<>();

    // Tracks which blocks were placed for each loaded lift
    // Key = lift name, Value = list of {x,y,z,blockdata}
    private final Map<String, List<PlacedBlock>> loadedLifts = new HashMap<>();

    public LiftManager(LiftSim plugin) {
        this.plugin = plugin;
    }

    public void loadDefinitions() {
        definitions.clear();
        List<Map<?, ?>> liftList = plugin.getConfig().getMapList("lifts");
        for (Map<?, ?> m : liftList) {
            try {
                String name  = (String) m.get("name");
                String world = (String) m.get("world");

                Map<?, ?> cs = (Map<?, ?>) m.get("cable-start");
                int sx = ((Number) cs.get("x")).intValue();
                int sy = ((Number) cs.get("y")).intValue();
                int sz = ((Number) cs.get("z")).intValue();

                int cableSpacing     = ((Number) m.getOrDefault("cable-spacing", 24)).intValue();
                int terminalSpacing  = ((Number) m.getOrDefault("terminal-spacing", 4)).intValue();
                int transitionBlocks = ((Number) m.getOrDefault("transition-blocks", 6)).intValue();
                double cornerThresh  = ((Number) m.getOrDefault("corner-angle-threshold", 30)).doubleValue();

                Map<?, ?> templates = (Map<?, ?>) m.get("templates");
                LiftDefinition.TemplateDefinition uphill   = parseTemplate((Map<?, ?>) templates.get("uphill"));
                LiftDefinition.TemplateDefinition downhill = parseTemplate((Map<?, ?>) templates.get("downhill"));

                definitions.put(name, new LiftDefinition(name, world, sx, sy, sz,
                        cableSpacing, terminalSpacing, transitionBlocks, cornerThresh,
                        uphill, downhill));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load lift definition: " + e.getMessage());
            }
        }

        // Load state files for any lifts that were loaded before a restart
        File stateDir = new File(plugin.getDataFolder(), "state");
        for (String name : definitions.keySet()) {
            File stateFile = new File(stateDir, name + ".yml");
            if (stateFile.exists()) {
                List<PlacedBlock> blocks = loadStateFile(stateFile);
                if (blocks != null) loadedLifts.put(name, blocks);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private LiftDefinition.TemplateDefinition parseTemplate(Map<?, ?> m) {
        Map<String, Object> origin = (Map<String, Object>) m.get("origin");
        int ox = ((Number) origin.get("x")).intValue();
        int oy = ((Number) origin.get("y")).intValue();
        int oz = ((Number) origin.get("z")).intValue();
        int sx = ((Number) m.get("size-x")).intValue();
        int sy = ((Number) m.get("size-y")).intValue();
        int sz = ((Number) m.get("size-z")).intValue();
        Object offXObj = m.get("offset-x"); int offX = offXObj != null ? ((Number) offXObj).intValue() : 0;
        Object offYObj = m.get("offset-y"); int offY = offYObj != null ? ((Number) offYObj).intValue() : -3;
        Object offZObj = m.get("offset-z"); int offZ = offZObj != null ? ((Number) offZObj).intValue() : 0;
        return new LiftDefinition.TemplateDefinition(ox, oy, oz, sx, sy, sz, offX, offY, offZ);
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /**
     * Load a lift — scan the cable, find mount points, paste chairs, save state.
     * Returns a result message.
     */
    public String loadLift(String name) {
        LiftDefinition def = definitions.get(name);
        if (def == null) return "Unknown lift: " + name;
        if (loadedLifts.containsKey(name)) return "Lift '" + name + "' is already loaded.";

        World world = Bukkit.getWorld(def.world);
        if (world == null) return "World '" + def.world + "' not found.";

        // Scan the cable
        List<CableScanner.CableBlock> cable = CableScanner.scan(
                world, def.cableStartX, def.cableStartY, def.cableStartZ,
                def.cornerAngleThreshold, 100000);

        if (cable.isEmpty()) return "No cable blocks found from start point.";

        // Find the top terminal transition — first gray_wool section after initial cable
        int topTerminalIdx = findTopTerminal(cable);

        // Determine mount points with variable spacing
        List<MountPoint> mounts = findMountPoints(cable, topTerminalIdx,
                def.cableSpacing, def.terminalSpacing, def.transitionBlocks);

        if (mounts.isEmpty()) return "No mount points found along cable.";

        // Paste chairs at each mount point and record placed blocks
        List<PlacedBlock> placed = new ArrayList<>();
        int chairNum = 0;
        for (MountPoint mp : mounts) {
            LiftDefinition.TemplateDefinition tmpl = mp.downhill
                    ? def.downhillTemplate : def.uphillTemplate;
            List<PlacedBlock> chairBlocks = pasteTemplate(world, tmpl, mp.x, mp.y, mp.z, chairNum);
            placed.addAll(chairBlocks);
            chairNum++;
        }

        loadedLifts.put(name, placed);
        saveStateFile(name, placed);

        return "Loaded lift '" + name + "': " + chairNum + " chairs placed ("
                + placed.size() + " blocks).";
    }

    // ── Unload ───────────────────────────────────────────────────────────────

    public String unloadLift(String name) {
        if (!definitions.containsKey(name)) return "Unknown lift: " + name;
        List<PlacedBlock> placed = loadedLifts.get(name);
        if (placed == null) return "Lift '" + name + "' is not loaded.";

        World world = Bukkit.getWorld(definitions.get(name).world);
        if (world == null) return "World not found.";

        for (PlacedBlock pb : placed) {
            world.getBlockAt(pb.x, pb.y, pb.z).setType(Material.AIR, false);
        }

        loadedLifts.remove(name);
        File stateFile = new File(new File(plugin.getDataFolder(), "state"), name + ".yml");
        if (stateFile.exists()) stateFile.delete();

        return "Unloaded lift '" + name + "': " + placed.size() + " blocks removed.";
    }

    // ── Status / List ─────────────────────────────────────────────────────────

    public boolean isLoaded(String name) { return loadedLifts.containsKey(name); }

    public String getStatus(String name) {
        if (!definitions.containsKey(name)) return "Unknown lift: " + name;
        if (loadedLifts.containsKey(name)) {
            int blocks = loadedLifts.get(name).size();
            return "Lift '" + name + "' is LOADED (" + blocks + " blocks placed).";
        }
        return "Lift '" + name + "' is UNLOADED.";
    }

    public Collection<String> getLiftNames()  { return definitions.keySet(); }
    public int getLiftCount()                 { return definitions.size(); }

    // ── Cable path analysis ───────────────────────────────────────────────────

    /**
     * Find where the top terminal starts — the first gray_wool block
     * encountered after at least some cable (iron_bars) have been seen.
     */
    private int findTopTerminal(List<CableScanner.CableBlock> cable) {
        boolean seenCable = false;
        for (int i = 0; i < cable.size(); i++) {
            CableScanner.CableBlock cb = cable.get(i);
            if (cb.type == CableScanner.BlockType.CABLE) seenCable = true;
            if (seenCable && cb.type == CableScanner.BlockType.TERMINAL) return i;
        }
        return cable.size() / 2; // fallback
    }

    /**
     * Walk the cable path producing mount points with variable spacing.
     * Uphill side: from start to topTerminalIdx.
     * Downhill side: from topTerminalIdx to end.
     * Spacing ramps between cable and terminal spacing at transitions.
     */
    private List<MountPoint> findMountPoints(List<CableScanner.CableBlock> cable,
                                              int topTerminalIdx,
                                              int cableSpacing, int terminalSpacing,
                                              int transitionBlocks) {
        List<MountPoint> mounts = new ArrayList<>();

        // Process uphill then downhill
        processSegment(cable, 0, topTerminalIdx, false, cableSpacing, terminalSpacing,
                transitionBlocks, mounts);
        processSegment(cable, topTerminalIdx, cable.size(), true, cableSpacing, terminalSpacing,
                transitionBlocks, mounts);

        return mounts;
    }

    private void processSegment(List<CableScanner.CableBlock> cable,
                                 int from, int to, boolean downhill,
                                 int cableSpacing, int terminalSpacing,
                                 int transitionBlocks, List<MountPoint> mounts) {
        double accumulator = 0;
        double nextMount   = 0; // place first chair immediately

        for (int i = from; i < to; i++) {
            CableScanner.CableBlock cb = cable.get(i);
            if (cb.type == CableScanner.BlockType.CORNER) continue; // skip corners

            // Calculate effective spacing at this position
            double spacing = effectiveSpacing(cable, i, from, to,
                    cableSpacing, terminalSpacing, transitionBlocks);

            accumulator += 1.0;
            if (accumulator >= nextMount) {
                mounts.add(new MountPoint(cb.x, cb.y, cb.z, downhill));
                nextMount = accumulator + spacing;
            }
        }
    }

    /**
     * Compute the effective chair spacing at position i in the cable.
     * Ramps linearly between cableSpacing and terminalSpacing over transitionBlocks
     * at each cable/terminal boundary.
     */
    private double effectiveSpacing(List<CableScanner.CableBlock> cable, int i,
                                     int from, int to,
                                     int cableSpacing, int terminalSpacing,
                                     int transitionBlocks) {
        CableScanner.CableBlock cb = cable.get(i);
        boolean inTerminal = (cb.type == CableScanner.BlockType.TERMINAL);

        if (!inTerminal) {
            // Find distance to nearest terminal boundary
            int distToTerminal = Integer.MAX_VALUE;
            for (int d = 1; d <= transitionBlocks; d++) {
                int fwd = i + d, bwd = i - d;
                if (fwd < to && cable.get(fwd).type == CableScanner.BlockType.TERMINAL) {
                    distToTerminal = Math.min(distToTerminal, d);
                }
                if (bwd >= from && cable.get(bwd).type == CableScanner.BlockType.TERMINAL) {
                    distToTerminal = Math.min(distToTerminal, d);
                }
            }
            if (distToTerminal <= transitionBlocks) {
                double t = 1.0 - (distToTerminal / (double) transitionBlocks);
                return lerp(cableSpacing, terminalSpacing, t);
            }
            return cableSpacing;
        } else {
            // In terminal — find distance to nearest cable boundary
            int distToCable = Integer.MAX_VALUE;
            for (int d = 1; d <= transitionBlocks; d++) {
                int fwd = i + d, bwd = i - d;
                if (fwd < to && cable.get(fwd).type == CableScanner.BlockType.CABLE) {
                    distToCable = Math.min(distToCable, d);
                }
                if (bwd >= from && cable.get(bwd).type == CableScanner.BlockType.CABLE) {
                    distToCable = Math.min(distToCable, d);
                }
            }
            if (distToCable <= transitionBlocks) {
                double t = 1.0 - (distToCable / (double) transitionBlocks);
                return lerp(terminalSpacing, cableSpacing, t);
            }
            return terminalSpacing;
        }
    }

    private double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // ── Template pasting ──────────────────────────────────────────────────────

    private List<PlacedBlock> pasteTemplate(World world,
                                             LiftDefinition.TemplateDefinition tmpl,
                                             int mountX, int mountY, int mountZ,
                                             int chairIndex) {
        List<PlacedBlock> placed = new ArrayList<>();
        int destOriginX = mountX + tmpl.offsetX;
        int destOriginY = mountY + tmpl.offsetY;
        int destOriginZ = mountZ + tmpl.offsetZ;

        for (int dx = 0; dx < tmpl.sizeX; dx++) {
            for (int dy = 0; dy < tmpl.sizeY; dy++) {
                for (int dz = 0; dz < tmpl.sizeZ; dz++) {
                    Block src  = world.getBlockAt(tmpl.originX + dx, tmpl.originY + dy, tmpl.originZ + dz);
                    if (src.getType() == Material.AIR) continue; // don't paste air

                    int destX = destOriginX + dx;
                    int destY = destOriginY + dy;
                    int destZ = destOriginZ + dz;

                    Block dest = world.getBlockAt(destX, destY, destZ);
                    dest.setBlockData(src.getBlockData().clone(), false);
                    placed.add(new PlacedBlock(destX, destY, destZ, chairIndex));
                }
            }
        }
        return placed;
    }

    // ── State file I/O ────────────────────────────────────────────────────────

    private void saveStateFile(String liftName, List<PlacedBlock> blocks) {
        File stateDir  = new File(plugin.getDataFolder(), "state");
        File stateFile = new File(stateDir, liftName + ".yml");

        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (PlacedBlock pb : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", pb.x);
            entry.put("y", pb.y);
            entry.put("z", pb.z);
            entry.put("chair", pb.chairIndex);
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);

        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save state for " + liftName + ": " + e.getMessage());
        }
    }

    private List<PlacedBlock> loadStateFile(File stateFile) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        List<Map<?, ?>> blockList = yaml.getMapList("blocks");
        if (blockList == null) return null;

        List<PlacedBlock> blocks = new ArrayList<>();
        for (Map<?, ?> entry : blockList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) entry;
            int x  = ((Number) m.get("x")).intValue();
            int y  = ((Number) m.get("y")).intValue();
            int z  = ((Number) m.get("z")).intValue();
            int ch = ((Number) m.get("chair")).intValue();
            blocks.add(new PlacedBlock(x, y, z, ch));
        }
        return blocks;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static class MountPoint {
        final int x, y, z;
        final boolean downhill;
        MountPoint(int x, int y, int z, boolean downhill) {
            this.x = x; this.y = y; this.z = z; this.downhill = downhill;
        }
    }

    static class PlacedBlock {
        final int x, y, z, chairIndex;
        PlacedBlock(int x, int y, int z, int chairIndex) {
            this.x = x; this.y = y; this.z = z; this.chairIndex = chairIndex;
        }
    }
}
