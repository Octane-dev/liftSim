package com.skiresort.liftsim;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class LiftCommand implements CommandExecutor, TabCompleter {

    private final LiftSim plugin;
    private final RegistrationManager regManager;

    public LiftCommand(LiftSim plugin) {
        this.plugin     = plugin;
        this.regManager = plugin.getRegistrationManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("liftsim.manage")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();

        // Commands that don't need a player
        switch (sub) {
            case "list"   -> { listLifts(sender);   return true; }
            case "reload" -> { reloadConfig(sender); return true; }
        }

        // All other commands need a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }

        switch (sub) {
            case "load"     -> cmdLoad(player, args);
            case "unload"   -> cmdUnload(player, args);
            case "status"   -> cmdStatus(player, args);
            case "register" -> cmdRegister(player, args);
            case "set"      -> cmdSet(player, args);
            case "confirm"  -> cmdConfirm(player);
            case "cancel"   -> cmdCancel(player);
            case "session"  -> cmdSession(player);
            default         -> sendHelp(sender);
        }

        return true;
    }

    // ── Lift management ───────────────────────────────────────────────────────

    private void cmdLoad(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Usage: /lift load <name>"); return; }
        p.sendMessage(ChatColor.AQUA + "[LiftSim] Loading '" + args[1] + "'...");
        String result = plugin.getLiftManager().loadLift(args[1]);
        p.sendMessage(ChatColor.GREEN + "[LiftSim] " + result);
    }

    private void cmdUnload(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Usage: /lift unload <name>"); return; }
        String result = plugin.getLiftManager().unloadLift(args[1]);
        p.sendMessage(ChatColor.GREEN + "[LiftSim] " + result);
    }

    private void cmdStatus(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Usage: /lift status <name>"); return; }
        p.sendMessage(ChatColor.AQUA + "[LiftSim] " + plugin.getLiftManager().getStatus(args[1]));
    }

    private void listLifts(CommandSender sender) {
        Collection<String> names = plugin.getLiftManager().getLiftNames();
        if (names.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[LiftSim] No lifts defined.");
            return;
        }
        sender.sendMessage(ChatColor.AQUA + "[LiftSim] Defined lifts:");
        for (String name : names) {
            boolean loaded = plugin.getLiftManager().isLoaded(name);
            String status  = loaded ? ChatColor.GREEN + "LOADED" : ChatColor.RED + "UNLOADED";
            sender.sendMessage("  " + ChatColor.WHITE + name + " " + ChatColor.RESET + status);
        }
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getLiftManager().loadDefinitions();
        sender.sendMessage(ChatColor.GREEN + "[LiftSim] Config reloaded. "
                + plugin.getLiftManager().getLiftCount() + " lift(s) defined.");
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void cmdRegister(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /lift register <name>");
            return;
        }
        String name = args[1];
        if (regManager.hasSession(p)) {
            p.sendMessage(ChatColor.YELLOW + "[LiftSim] You already have an active session for '"
                    + regManager.getSession(p).liftName + "'. Run /lift cancel first.");
            return;
        }
        regManager.startSession(p, name);
        p.sendMessage(ChatColor.GREEN + "[LiftSim] Started registration for '" + name + "'.");
        p.sendMessage(ChatColor.GRAY + "Use /lift set <property> to define the lift.");
        p.sendMessage(ChatColor.GRAY + "Run /lift session to see current values.");
        p.sendMessage(ChatColor.GRAY + "Run /lift confirm to save, /lift cancel to discard.");
        sendSetHelp(p);
    }

    private void cmdSet(Player p, String[] args) {
        if (!regManager.hasSession(p)) {
            p.sendMessage(ChatColor.RED + "[LiftSim] No active session. Run /lift register <name> first.");
            return;
        }

        if (args.length < 2) { sendSetHelp(p); return; }

        RegistrationSession session = regManager.getSession(p);
        String prop = args[1].toLowerCase();

        switch (prop) {
            case "cable-start" -> {
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.cableStartX = b.getX();
                session.cableStartY = b.getY();
                session.cableStartZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] cable-start set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ()
                        + " (" + b.getType() + ")");
            }
            case "cable-direction" -> {
                if (session.cableStartX == null) {
                    p.sendMessage(ChatColor.RED + "Set cable-start first.");
                    return;
                }
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.cableDirectionX = b.getX();
                session.cableDirectionY = b.getY();
                session.cableDirectionZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] cable-direction set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ()
                        + " (" + b.getType() + ")");
                // Show the direction vector for confirmation
                double dx = b.getX() - session.cableStartX;
                double dy = b.getY() - session.cableStartY;
                double dz = b.getZ() - session.cableStartZ;
                double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (len > 0) {
                    p.sendMessage(ChatColor.GRAY + "  Direction vector: "
                            + String.format("%.2f %.2f %.2f", dx/len, dy/len, dz/len));
                }
            }
            case "uphill-origin" -> {
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.uphillOriginX = b.getX();
                session.uphillOriginY = b.getY();
                session.uphillOriginZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] uphill-origin set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ());
            }
            case "uphill-corner" -> {
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.uphillCornerX = b.getX();
                session.uphillCornerY = b.getY();
                session.uphillCornerZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] uphill-corner set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ());
                if (session.uphillOriginX != null) {
                    int sx = Math.abs(b.getX() - session.uphillOriginX) + 1;
                    int sy = Math.abs(b.getY() - session.uphillOriginY) + 1;
                    int sz = Math.abs(b.getZ() - session.uphillOriginZ) + 1;
                    p.sendMessage(ChatColor.GRAY + "  Uphill template size: " + sx + "x" + sy + "x" + sz);
                }
            }
            case "downhill-origin" -> {
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.downhillOriginX = b.getX();
                session.downhillOriginY = b.getY();
                session.downhillOriginZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] downhill-origin set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ());
            }
            case "downhill-corner" -> {
                Block b = getTargetBlock(p);
                if (b == null) { p.sendMessage(ChatColor.RED + "Not looking at a block."); return; }
                session.downhillCornerX = b.getX();
                session.downhillCornerY = b.getY();
                session.downhillCornerZ = b.getZ();
                p.sendMessage(ChatColor.GREEN + "[LiftSim] downhill-corner set to "
                        + b.getX() + " " + b.getY() + " " + b.getZ());
                if (session.downhillOriginX != null) {
                    int sx = Math.abs(b.getX() - session.downhillOriginX) + 1;
                    int sy = Math.abs(b.getY() - session.downhillOriginY) + 1;
                    int sz = Math.abs(b.getZ() - session.downhillOriginZ) + 1;
                    p.sendMessage(ChatColor.GRAY + "  Downhill template size: " + sx + "x" + sy + "x" + sz);
                }
            }
            case "spacing" -> {
                if (args.length < 4) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /lift set spacing <cable> <terminal>");
                    return;
                }
                try {
                    session.cableSpacing    = Integer.parseInt(args[2]);
                    session.terminalSpacing = Integer.parseInt(args[3]);
                    p.sendMessage(ChatColor.GREEN + "[LiftSim] spacing set — cable: "
                            + session.cableSpacing + "  terminal: " + session.terminalSpacing);
                } catch (NumberFormatException e) {
                    p.sendMessage(ChatColor.RED + "Invalid numbers.");
                }
            }
            case "transition" -> {
                if (args.length < 3) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /lift set transition <blocks>");
                    return;
                }
                try {
                    session.transitionBlocks = Integer.parseInt(args[2]);
                    p.sendMessage(ChatColor.GREEN + "[LiftSim] transition-blocks set to "
                            + session.transitionBlocks);
                } catch (NumberFormatException e) {
                    p.sendMessage(ChatColor.RED + "Invalid number.");
                }
            }
            case "corner-threshold" -> {
                if (args.length < 3) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /lift set corner-threshold <degrees>");
                    return;
                }
                try {
                    session.cornerThreshold = Double.parseDouble(args[2]);
                    p.sendMessage(ChatColor.GREEN + "[LiftSim] corner-threshold set to "
                            + session.cornerThreshold + "°");
                } catch (NumberFormatException e) {
                    p.sendMessage(ChatColor.RED + "Invalid number.");
                }
            }
            case "offset" -> {
                // Allow manual override of template offset
                if (args.length < 6) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /lift set offset <uphill|downhill> <dx> <dy> <dz>");
                    return;
                }
                try {
                    boolean up = args[2].equalsIgnoreCase("uphill");
                    int dx = Integer.parseInt(args[3]);
                    int dy = Integer.parseInt(args[4]);
                    int dz = Integer.parseInt(args[5]);
                    if (up) { session.uphillOffsetX = dx; session.uphillOffsetY = dy; session.uphillOffsetZ = dz; }
                    else    { session.downhillOffsetX = dx; session.downhillOffsetY = dy; session.downhillOffsetZ = dz; }
                    p.sendMessage(ChatColor.GREEN + "[LiftSim] " + args[2] + " offset set to "
                            + dx + " " + dy + " " + dz);
                } catch (NumberFormatException e) {
                    p.sendMessage(ChatColor.RED + "Invalid numbers.");
                }
            }
            default -> {
                p.sendMessage(ChatColor.RED + "Unknown property: " + prop);
                sendSetHelp(p);
            }
        }
    }

    private void cmdConfirm(Player p) {
        if (!regManager.hasSession(p)) {
            p.sendMessage(ChatColor.RED + "[LiftSim] No active session.");
            return;
        }
        String result = regManager.confirmSession(p);
        p.sendMessage(ChatColor.GREEN + "[LiftSim] " + result);
    }

    private void cmdCancel(Player p) {
        if (!regManager.hasSession(p)) {
            p.sendMessage(ChatColor.RED + "[LiftSim] No active session.");
            return;
        }
        String name = regManager.getSession(p).liftName;
        regManager.cancelSession(p);
        p.sendMessage(ChatColor.YELLOW + "[LiftSim] Registration for '" + name + "' cancelled.");
    }

    private void cmdSession(Player p) {
        if (!regManager.hasSession(p)) {
            p.sendMessage(ChatColor.RED + "[LiftSim] No active session.");
            return;
        }
        p.sendMessage(regManager.getSession(p).summary());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Raycast to get the block the player is looking at (up to 10 blocks) */
    private Block getTargetBlock(Player p) {
        RayTraceResult result = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getEyeLocation().getDirection(), 10);
        if (result == null || result.getHitBlock() == null) return null;
        return result.getHitBlock();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== LiftSim Commands ===");
        sender.sendMessage(ChatColor.WHITE + "/lift load <name>        " + ChatColor.GRAY + "Load chairs onto a lift");
        sender.sendMessage(ChatColor.WHITE + "/lift unload <name>      " + ChatColor.GRAY + "Remove chairs from a lift");
        sender.sendMessage(ChatColor.WHITE + "/lift status <name>      " + ChatColor.GRAY + "Check if a lift is loaded");
        sender.sendMessage(ChatColor.WHITE + "/lift list               " + ChatColor.GRAY + "List all defined lifts");
        sender.sendMessage(ChatColor.WHITE + "/lift register <name>    " + ChatColor.GRAY + "Start registering a new lift");
        sender.sendMessage(ChatColor.WHITE + "/lift set <property>     " + ChatColor.GRAY + "Set a property in current session");
        sender.sendMessage(ChatColor.WHITE + "/lift session            " + ChatColor.GRAY + "Show current session state");
        sender.sendMessage(ChatColor.WHITE + "/lift confirm            " + ChatColor.GRAY + "Save and finish registration");
        sender.sendMessage(ChatColor.WHITE + "/lift cancel             " + ChatColor.GRAY + "Discard current registration");
        sender.sendMessage(ChatColor.WHITE + "/lift reload             " + ChatColor.GRAY + "Reload config");
    }

    private void sendSetHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "-- /lift set properties --");
        sender.sendMessage(ChatColor.WHITE + "cable-start         " + ChatColor.GRAY + "Look at first iron_bar on open cable");
        sender.sendMessage(ChatColor.WHITE + "cable-direction     " + ChatColor.GRAY + "Look at second iron_bar up the cable (for diagonal lifts)");
        sender.sendMessage(ChatColor.WHITE + "uphill-origin       " + ChatColor.GRAY + "Look at one corner of uphill template");
        sender.sendMessage(ChatColor.WHITE + "uphill-corner       " + ChatColor.GRAY + "Look at opposite corner of uphill template");
        sender.sendMessage(ChatColor.WHITE + "downhill-origin     " + ChatColor.GRAY + "Look at one corner of downhill template");
        sender.sendMessage(ChatColor.WHITE + "downhill-corner     " + ChatColor.GRAY + "Look at opposite corner of downhill template");
        sender.sendMessage(ChatColor.WHITE + "spacing <c> <t>     " + ChatColor.GRAY + "Cable and terminal spacing (e.g. 24 4)");
        sender.sendMessage(ChatColor.WHITE + "transition <n>      " + ChatColor.GRAY + "Transition ramp length in blocks");
        sender.sendMessage(ChatColor.WHITE + "corner-threshold <d>" + ChatColor.GRAY + "Corner detection angle in degrees");
        sender.sendMessage(ChatColor.WHITE + "offset <up|down> <x> <y> <z>" + ChatColor.GRAY + "Manual template offset override");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStart(args[0], Arrays.asList(
                    "load", "unload", "status", "list", "register",
                    "set", "session", "confirm", "cancel", "reload"));
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "load", "unload", "status" ->
                        filterStart(args[1], new ArrayList<>(plugin.getLiftManager().getLiftNames()));
                case "set" -> filterStart(args[1], Arrays.asList(
                        "cable-start", "cable-direction", "uphill-origin", "uphill-corner",
                        "downhill-origin", "downhill-corner",
                        "spacing", "transition", "corner-threshold", "offset"));
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")
                && args[1].equalsIgnoreCase("offset")) {
            return filterStart(args[2], Arrays.asList("uphill", "downhill"));
        }
        return Collections.emptyList();
    }

    private List<String> filterStart(String prefix, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
