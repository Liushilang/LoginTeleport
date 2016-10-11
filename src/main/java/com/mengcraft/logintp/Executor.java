package com.mengcraft.logintp;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Executor implements CommandExecutor, Listener {

    private final List<Location> loc = new ArrayList<>();
    private final Main main;
    private final Config config;

    private Iterator<Location> it;
    private int c = -1;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (!Player.class.isInstance(sender) || !sender.isOp()) {
                throw new RuntimeException("You are not operator!");
            }
            return execute(Player.class.cast(sender), args);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.DARK_RED + e.getMessage());
        }
        return false;
    }

    @EventHandler
    public void handle(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission("logintp.bypass")) {
            main.run(() -> process(event.getPlayer()));
        }
    }

    @EventHandler
    public void handle(PlayerRespawnEvent event) {
        if (config.isPortalQuit() && !event.getPlayer().hasPermission("logintp.bypass")) {
            Location location = nextLocation();
            if (!Main.nil(location)) {
                event.setRespawnLocation(location);
            }
        }
    }

    @EventHandler
    public void handle(PlayerQuitEvent event) {
        if (config.isPortalQuit() && !event.getPlayer().hasPermission("logintp.bypass")) {
            process(event.getPlayer());
        }
    }

    @EventHandler
    public void handle(EntityDamageEvent event) {
        if (config.isPortalVoid() && event.getEntityType() == EntityType.PLAYER && event.getCause() == DamageCause.VOID) {
            event.setCancelled(true);
            process(Player.class.cast(event.getEntity()));
        }
    }

    private void process(Player player) {
        Location location = nextLocation();
        if (!Main.nil(location)) {
            process(player, location);
        }
    }

    private Location nextLocation() {
        if (Main.nil(it) || !it.hasNext()) {
            if (loc.isEmpty()) {
                return null;
            }
            it = loc.iterator();
        }
        return it.next();
    }

    private void process(Player player, Location location) {
        if (!Main.nil(location.getWorld())) {
            Entity vehicle = player.getVehicle();
            if (!Main.nil(vehicle)) {
                vehicle.eject();
                vehicle.teleport(location);
                main.run(() -> {
                    vehicle.setPassenger(player);
                });
            }
            player.teleport(location);
        }
    }

    public void load() {
        // Low version compatible code.
        String state = main.getConfig().getString("default", null);
        if (state != null) {
            add(convert(state));
            main.getConfig().set("default", null);
        }
        // For multiple location code.
        if (!loc.isEmpty()) loc.clear();
        for (String string : main.getConfig().getStringList("locations")) {
            add(convert(string));
        }
        config.load();
    }

    private void add(Location where) {
        if (where.getWorld() != null) {
            loc.add(where);
        }
    }

    private Location convert(String string) {
        Location where = new Location(null, 0, 0, 0);
        try {
            Iterator it = ((List) new JSONParser().parse(string)).iterator();
            String worldName = (String) it.next();
            World world = main.getServer().getWorld(worldName);
            // Check if world be removed.
            if (world != null) {
                where.setWorld(world);

                where.setX((double) it.next());
                where.setY((double) it.next());
                where.setZ((double) it.next());

                where.setYaw((float) (double) it.next());
                where.setPitch((float) (double) it.next());
            }
        } catch (ParseException e) {
            main.getLogger().warning(e.toString());
        }
        return where;
    }

    public Executor(Main main, Config config) {
        this.main = main;
        this.config = config;
    }

    private boolean execute(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(new String[]{
                    ChatColor.GOLD + "/logintp next",
                    ChatColor.GOLD + "/logintp del",
                    ChatColor.GOLD + "/logintp add",
                    ChatColor.GOLD + "/logintp save",
                    ChatColor.GOLD + "/logintp load",
                    ChatColor.GOLD + "/logintp list"
            });
        } else if (args[0].equals("next")) {
            if (loc.size() != 0) {
                process(p, loc.get(c()));
            }
        } else if (args[0].equals("del")) {
            if (loc.size() != 0) {
                loc.remove(c != -1 ? (c != 0 ? c-- : 0) : 0);
                p.sendMessage(ChatColor.GOLD + "Done! Please save it.");
            }
        } else if (args[0].equals("add")) {
            loc.add(p.getLocation());
            c = loc.size() - 1;
            p.sendMessage(ChatColor.GOLD + "Done! Please save it.");
        } else if (args[0].equals("save")) {
            List<String> array = new ArrayList<>();
            for (Location where : loc) {
                if (where.getWorld() != null) array.add(convert(where));
            }
            main.getConfig().set("locations", array);
            main.saveConfig();

            p.sendMessage(ChatColor.GOLD + "Done!");
        } else if (args[0].equals("load")) {
            main.reloadConfig();
            load();
            p.sendMessage(ChatColor.GOLD + "Done!");
        } else if (args[0].equals("list")) {
            for (Location loc : this.loc) {
                p.sendMessage(ChatColor.GOLD + convert(loc));
            }
        } else {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private String convert(Location where) {
        JSONArray array = new JSONArray();
        array.add(where.getWorld().getName());
        array.add(where.getX());
        array.add(where.getY());
        array.add(where.getZ());
        array.add(where.getYaw());
        array.add(where.getPitch());

        return array.toJSONString();
    }

    private int c() {
        if (c + 1 != loc.size()) {
            return (c = c + 1);
        }
        return (c = 0);
    }

}
