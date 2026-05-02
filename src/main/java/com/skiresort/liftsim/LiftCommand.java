package com.skiresort.liftsim;

import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.*;

public class LiftCommand implements CommandExecutor, TabCompleter {

    private final LiftSim plugin;

    public LiftCommand(LiftSim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("liftsim.manage")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "load" -> {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /lift load <name>"); return true; }
                String result = plugin.getLiftManager().loadLift(args[1]);
                sender.sendMessage(ChatColor.GREEN + "[LiftSim] " + result);
            }
            case "unload" -> {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /lift unload <name>"); return true; }
                String result = plugin.getLiftManager().unloadLift(args[1]);
                sender.sendMessage(ChatColor.GREEN + "[LiftSim] " + result);
            }
            case "status" -> {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /lift status <name>"); return true; }
                String result = plugin.getLiftManager().getStatus(args[1]);
                sender.sendMessage(ChatColor.AQUA + "[LiftSim] " + result);
            }
            case "list" -> {
                Collection<String> names = plugin.getLiftManager().getLiftNames();
                if (names.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "[LiftSim] No lifts defined in config.");
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + "[LiftSim] Defined lifts:");
                for (String name : names) {
                    boolean loaded = plugin.getLiftManager().isLoaded(name);
                    String status  = loaded
                            ? ChatColor.GREEN + "LOADED"
                            : ChatColor.RED + "UNLOADED";
                    sender.sendMessage("  " + ChatColor.WHITE + name + " " + status);
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getLiftManager().loadDefinitions();
                sender.sendMessage(ChatColor.GREEN + "[LiftSim] Config reloaded. "
                        + plugin.getLiftManager().getLiftCount() + " lift(s) defined.");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== LiftSim Commands ===");
        sender.sendMessage(ChatColor.WHITE + "/lift load <name>    " + ChatColor.GRAY + "Load chairs onto a lift");
        sender.sendMessage(ChatColor.WHITE + "/lift unload <name>  " + ChatColor.GRAY + "Remove chairs from a lift");
        sender.sendMessage(ChatColor.WHITE + "/lift status <name>  " + ChatColor.GRAY + "Check if a lift is loaded");
        sender.sendMessage(ChatColor.WHITE + "/lift list           " + ChatColor.GRAY + "List all defined lifts");
        sender.sendMessage(ChatColor.WHITE + "/lift reload         " + ChatColor.GRAY + "Reload config");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStart(args[0], Arrays.asList("load", "unload", "status", "list", "reload"));
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("reload")) {
            return filterStart(args[1], new ArrayList<>(plugin.getLiftManager().getLiftNames()));
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
