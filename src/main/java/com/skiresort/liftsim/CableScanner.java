package com.skiresort.liftsim;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Scans along a chairlift cable from a start block, following iron_bars
 * and gray_wool, to produce a list of CableBlock entries describing each
 * block along the path with its type (CABLE, TERMINAL, CORNER) and
 * the direction the path was travelling when it passed through it.
 */
public class CableScanner {

    public enum BlockType { CABLE, TERMINAL, CORNER }

    public static class CableBlock {
        public final int x, y, z;
        public final BlockType type;
        public final double dx, dy, dz; // normalised direction vector at this point

        CableBlock(int x, int y, int z, BlockType type, double dx, double dy, double dz) {
            this.x = x; this.y = y; this.z = z;
            this.type = type;
            this.dx = dx; this.dy = dy; this.dz = dz;
        }
    }

    private static final Set<Material> CABLE_MATERIALS = EnumSet.of(
        Material.IRON_BARS,
        Material.GRAY_WOOL
    );

    /**
     * Walk the cable from the start block, returning an ordered list of
     * every cable block encountered. Stops when it returns to the start
     * (full loop) or runs out of connected cable blocks.
     *
     * @param world           the world
     * @param startX/Y/Z      starting iron_bar block
     * @param cornerThreshold angle change in degrees above which a block is a corner
     * @param maxBlocks       safety limit to prevent infinite loops
     */
    public static List<CableBlock> scan(World world,
                                        int startX, int startY, int startZ,
                                        double cornerThreshold, int maxBlocks) {
        List<CableBlock> path = new ArrayList<>();
        Set<String> visited  = new HashSet<>();

        int cx = startX, cy = startY, cz = startZ;
        int px = Integer.MIN_VALUE, py = Integer.MIN_VALUE, pz = Integer.MIN_VALUE; // previous

        // Direction tracking — start with no direction
        double lastDX = 0, lastDY = 0, lastDZ = 0;
        boolean hasLastDir = false;

        visited.add(key(cx, cy, cz));

        for (int step = 0; step < maxBlocks; step++) {
            Material mat = world.getBlockAt(cx, cy, cz).getType();
            BlockType blockType;

            // Find next block before classifying current
            int[] next = findNext(world, cx, cy, cz, px, py, pz, step == 0);

            // Calculate direction from previous to current
            double curDX = 0, curDY = 0, curDZ = 0;
            if (px != Integer.MIN_VALUE) {
                double len = Math.sqrt(Math.pow(cx-px,2)+Math.pow(cy-py,2)+Math.pow(cz-pz,2));
                if (len > 0) { curDX=(cx-px)/len; curDY=(cy-py)/len; curDZ=(cz-pz)/len; }
            }

            // Classify: corner if direction changed significantly
            if (mat == Material.GRAY_WOOL && hasLastDir && px != Integer.MIN_VALUE) {
                double dot = lastDX*curDX + lastDY*curDY + lastDZ*curDZ;
                double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
                blockType = angleDeg > cornerThreshold ? BlockType.CORNER : BlockType.TERMINAL;
            } else if (mat == Material.GRAY_WOOL) {
                blockType = BlockType.TERMINAL;
            } else {
                blockType = BlockType.CABLE;
            }

            path.add(new CableBlock(cx, cy, cz, blockType, curDX, curDY, curDZ));

            if (curDX != 0 || curDY != 0 || curDZ != 0) {
                lastDX = curDX; lastDY = curDY; lastDZ = curDZ;
                hasLastDir = true;
            }

            if (next == null) break;

            String nextKey = key(next[0], next[1], next[2]);
            if (visited.contains(nextKey)) break; // completed the loop

            visited.add(nextKey);
            px = cx; py = cy; pz = cz;
            cx = next[0]; cy = next[1]; cz = next[2];
        }

        return path;
    }

    /**
     * Find the next cable block adjacent to (cx,cy,cz), excluding the block
     * we just came from (px,py,pz) to prevent backtracking.
     * Searches the 3x3x3 neighbourhood.
     *
     * @param firstStep if true, only iron_bars are valid next blocks (not gray_wool).
     *                  This prevents the scanner from immediately entering the bottom
     *                  terminal on the very first step from cable-start.
     */
    private static int[] findNext(World world, int cx, int cy, int cz,
                                   int px, int py, int pz, boolean firstStep) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int nx = cx+dx, ny = cy+dy, nz = cz+dz;
                    if (nx == px && ny == py && nz == pz) continue; // no backtrack
                    Material mat = world.getBlockAt(nx, ny, nz).getType();
                    // On the first step, only allow iron_bars to force direction up the cable
                    if (firstStep && mat != Material.IRON_BARS) continue;
                    if (CABLE_MATERIALS.contains(mat)) return new int[]{nx, ny, nz};
                }
            }
        }
        return null;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
