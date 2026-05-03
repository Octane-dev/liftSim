package com.skiresort.liftsim;

import org.bukkit.Location;

/**
 * Defines a chairlift — its cable start, spacing rules, and chair templates.
 */
public class LiftDefinition {

    public final String name;
    public final String world;
    public final int cableStartX, cableStartY, cableStartZ;
    // Initial direction hint — vector from cable-start to cable-direction block (may be null)
    public final Double cableDirDX, cableDirDY, cableDirDZ;
    public final int cableSpacing;
    public final int terminalSpacing;
    public final int transitionBlocks;
    public final double cornerAngleThreshold;
    public final TemplateDefinition uphillTemplate;
    public final TemplateDefinition downhillTemplate;

    public LiftDefinition(String name, String world,
                          int cableStartX, int cableStartY, int cableStartZ,
                          Double cableDirDX, Double cableDirDY, Double cableDirDZ,
                          int cableSpacing, int terminalSpacing, int transitionBlocks,
                          double cornerAngleThreshold,
                          TemplateDefinition uphillTemplate,
                          TemplateDefinition downhillTemplate) {
        this.name               = name;
        this.world              = world;
        this.cableStartX        = cableStartX;
        this.cableStartY        = cableStartY;
        this.cableStartZ        = cableStartZ;
        this.cableDirDX         = cableDirDX;
        this.cableDirDY         = cableDirDY;
        this.cableDirDZ         = cableDirDZ;
        this.cableSpacing       = cableSpacing;
        this.terminalSpacing    = terminalSpacing;
        this.transitionBlocks   = transitionBlocks;
        this.cornerAngleThreshold = cornerAngleThreshold;
        this.uphillTemplate     = uphillTemplate;
        this.downhillTemplate   = downhillTemplate;
    }

    /** Template definition — origin, size, and offset relative to cable block */
    public static class TemplateDefinition {
        public final int originX, originY, originZ;
        public final int sizeX, sizeY, sizeZ;
        public final int offsetX, offsetY, offsetZ;

        public TemplateDefinition(int originX, int originY, int originZ,
                                  int sizeX, int sizeY, int sizeZ,
                                  int offsetX, int offsetY, int offsetZ) {
            this.originX = originX; this.originY = originY; this.originZ = originZ;
            this.sizeX   = sizeX;   this.sizeY   = sizeY;   this.sizeZ   = sizeZ;
            this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ;
        }
    }
}
