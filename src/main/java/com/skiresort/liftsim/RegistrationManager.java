package com.skiresort.liftsim;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RegistrationManager {

    private final LiftSim plugin;
    private final Map<UUID, RegistrationSession> sessions = new HashMap<>();

    public RegistrationManager(LiftSim plugin) {
        this.plugin = plugin;
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public RegistrationSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public RegistrationSession startSession(Player player, String liftName) {
        RegistrationSession session = new RegistrationSession(liftName);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void cancelSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    /**
     * Confirm and save a session to config.yml.
     * Returns a result message.
     */
    public String confirmSession(Player player) {
        RegistrationSession session = sessions.get(player.getUniqueId());
        if (session == null) return "No active registration session.";
        if (!session.isComplete()) return "Session not complete yet.\n" + session.summary();

        // Build the lift definition
        LiftDefinition.TemplateDefinition uphill   = session.buildTemplate(true);
        LiftDefinition.TemplateDefinition downhill = session.buildTemplate(false);

        // Write to config.yml
        String result = writeToConfig(session, uphill, downhill);
        if (result != null) return result;

        // Reload definitions
        plugin.reloadConfig();
        plugin.getLiftManager().loadDefinitions();

        sessions.remove(player.getUniqueId());
        return "Lift '" + session.liftName + "' registered and saved! Run /lift load "
                + session.liftName + " to deploy chairs.";
    }

    private String writeToConfig(RegistrationSession s,
                                  LiftDefinition.TemplateDefinition uphill,
                                  LiftDefinition.TemplateDefinition downhill) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        List<Map<String, Object>> lifts = new ArrayList<>();

        // Preserve existing lifts
        List<Map<?, ?>> existing = yaml.getMapList("lifts");
        for (Map<?, ?> m : existing) {
            if (s.liftName.equals(m.get("name"))) continue; // replace if already exists
            lifts.add(new LinkedHashMap<>(m));
        }

        // Build new lift entry
        Map<String, Object> liftEntry = new LinkedHashMap<>();
        liftEntry.put("name", s.liftName);
        liftEntry.put("world", plugin.getConfig().getString("world", "world"));

        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("x", s.cableStartX); cs.put("y", s.cableStartY); cs.put("z", s.cableStartZ);
        liftEntry.put("cable-start", cs);

        liftEntry.put("cable-spacing",       s.cableSpacing);
        liftEntry.put("terminal-spacing",    s.terminalSpacing);
        liftEntry.put("transition-blocks",   s.transitionBlocks);
        liftEntry.put("corner-angle-threshold", s.cornerThreshold);

        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("uphill",   templateMap(uphill));
        templates.put("downhill", templateMap(downhill));
        liftEntry.put("templates", templates);

        lifts.add(liftEntry);
        yaml.set("lifts", lifts);

        try {
            yaml.save(configFile);
            return null; // success
        } catch (IOException e) {
            return "Failed to save config: " + e.getMessage();
        }
    }

    private Map<String, Object> templateMap(LiftDefinition.TemplateDefinition t) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("x", t.originX); origin.put("y", t.originY); origin.put("z", t.originZ);
        m.put("origin", origin);
        m.put("size-x", t.sizeX);
        m.put("size-y", t.sizeY);
        m.put("size-z", t.sizeZ);
        m.put("offset-x", t.offsetX);
        m.put("offset-y", t.offsetY);
        m.put("offset-z", t.offsetZ);
        return m;
    }
}
