package com.skiresort.liftsim;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LiftSim extends JavaPlugin {

    private LiftManager liftManager;
    private RegistrationManager registrationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File stateDir = new File(getDataFolder(), "state");
        if (!stateDir.exists()) stateDir.mkdirs();

        liftManager         = new LiftManager(this);
        registrationManager = new RegistrationManager(this);
        liftManager.loadDefinitions();

        LiftCommand liftCommand = new LiftCommand(this);
        getCommand("lift").setExecutor(liftCommand);
        getCommand("lift").setTabCompleter(liftCommand);

        getLogger().info("LiftSim enabled. " + liftManager.getLiftCount() + " lift(s) defined.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LiftSim disabled.");
    }

    public LiftManager         getLiftManager()         { return liftManager; }
    public RegistrationManager getRegistrationManager() { return registrationManager; }
}
