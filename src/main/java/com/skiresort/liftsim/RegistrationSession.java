package com.skiresort.liftsim;

/**
 * Holds in-progress lift registration data for a single player.
 */
public class RegistrationSession {

    public final String liftName;

    // Cable start
    public Integer cableStartX, cableStartY, cableStartZ;

    // Uphill template — two opposite corners
    public Integer uphillOriginX, uphillOriginY, uphillOriginZ;
    public Integer uphillCornerX, uphillCornerY, uphillCornerZ;

    // Downhill template — two opposite corners
    public Integer downhillOriginX, downhillOriginY, downhillOriginZ;
    public Integer downhillCornerX, downhillCornerY, downhillCornerZ;

    // Spacing
    public int cableSpacing       = 24;
    public int terminalSpacing    = 4;
    public int transitionBlocks   = 6;
    public double cornerThreshold = 30.0;

    // Optional manual offset overrides (null = auto-calculate)
    public Integer uphillOffsetX, uphillOffsetY, uphillOffsetZ;
    public Integer downhillOffsetX, downhillOffsetY, downhillOffsetZ;

    public RegistrationSession(String liftName) {
        this.liftName = liftName;
    }

    /** Returns true if the minimum required fields are set */
    public boolean isComplete() {
        return cableStartX != null
                && uphillOriginX != null && uphillCornerX != null
                && downhillOriginX != null && downhillCornerX != null;
    }

    /** Human-readable session summary */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("§aRegistration session: §f").append(liftName).append("\n");
        sb.append(field("cable-start",      cableStartX, cableStartY, cableStartZ));
        sb.append(field("uphill-origin",    uphillOriginX, uphillOriginY, uphillOriginZ));
        sb.append(field("uphill-corner",    uphillCornerX, uphillCornerY, uphillCornerZ));
        sb.append(field("downhill-origin",  downhillOriginX, downhillOriginY, downhillOriginZ));
        sb.append(field("downhill-corner",  downhillCornerX, downhillCornerY, downhillCornerZ));
        sb.append("§7  cable-spacing: §f").append(cableSpacing).append("\n");
        sb.append("§7  terminal-spacing: §f").append(terminalSpacing).append("\n");
        sb.append("§7  transition-blocks: §f").append(transitionBlocks).append("\n");
        sb.append("§7  corner-threshold: §f").append(cornerThreshold).append("°\n");
        if (isComplete()) sb.append("§aReady to confirm. Run /lift confirm");
        else              sb.append("§cNot complete yet — set all required fields.");
        return sb.toString();
    }

    private String field(String name, Integer x, Integer y, Integer z) {
        if (x == null) return "§c  " + name + ": §7not set\n";
        return "§7  " + name + ": §f" + x + " " + y + " " + z + "\n";
    }

    /**
     * Derive a TemplateDefinition from the two opposite corners.
     * Normalises so origin is always min corner, size is always positive.
     */
    public LiftDefinition.TemplateDefinition buildTemplate(boolean uphill) {
        int ox = uphill ? uphillOriginX : downhillOriginX;
        int oy = uphill ? uphillOriginY : downhillOriginY;
        int oz = uphill ? uphillOriginZ : downhillOriginZ;
        int cx = uphill ? uphillCornerX : downhillCornerX;
        int cy = uphill ? uphillCornerY : downhillCornerY;
        int cz = uphill ? uphillCornerZ : downhillCornerZ;

        int minX = Math.min(ox, cx), maxX = Math.max(ox, cx);
        int minY = Math.min(oy, cy), maxY = Math.max(oy, cy);
        int minZ = Math.min(oz, cz), maxZ = Math.max(oz, cz);

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        // Use manual offset if set, otherwise auto-calculate
        int offX, offY, offZ;
        if (uphill && uphillOffsetX != null) {
            offX = uphillOffsetX; offY = uphillOffsetY; offZ = uphillOffsetZ;
        } else if (!uphill && downhillOffsetX != null) {
            offX = downhillOffsetX; offY = downhillOffsetY; offZ = downhillOffsetZ;
        } else {
            offX = -(sizeX / 2);
            offY = -sizeY; // template sits below the cable block
            offZ = -(sizeZ / 2);
        }

        return new LiftDefinition.TemplateDefinition(
                minX, minY, minZ, sizeX, sizeY, sizeZ, offX, offY, offZ);
    }
}
