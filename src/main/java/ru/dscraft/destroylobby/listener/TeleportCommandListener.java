package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.Locale;

/**
 * /hub и /lobby - в лобби, /spawn - на спавн teleport.spawn-world из любого мира (в лобби - точка лобби).
 * /spawn перехватывается раньше Essentials (у него своя /spawn), это выключается teleport.override-spawn.
 */
public class TeleportCommandListener implements Listener, CommandExecutor {

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    public TeleportCommandListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /** /hub, /lobby, /spawn */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("spawn")) toWorldSpawn(player);
        else toLobby(player);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("teleport.override-spawn", true)) return;
        String cmd = event.getMessage().trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!cmd.equals("/spawn") && !cmd.equals("/essentials:spawn") && !cmd.equals("/espawn")) return;
        event.setCancelled(true);
        toWorldSpawn(event.getPlayer());
    }

    private void toLobby(Player player) {
        Location spawn = configManager.getLobbySpawnLocation();
        if (spawn == null) {
            player.sendMessage(ColorUtil.parse("<red>Лобби сейчас недоступно.</red>"));
            return;
        }
        player.teleport(spawn);
        send(player, "teleport.hub-message", "<gray>Вы телепортированы в <#7B8FFB>лобби</#7B8FFB>.</gray>");
    }

    private void toWorldSpawn(Player player) {
        World world = player.getWorld();
        String spawnWorld = plugin.getConfig().getString("teleport.spawn-world", "world_skypvp");
        World game = spawnWorld == null || spawnWorld.isEmpty() ? null : Bukkit.getWorld(spawnWorld);
        if (game == null) game = world;
        Location target = configManager.isLobbyWorld(world.getName())
                ? configManager.getLobbySpawnLocation()
                : game.getSpawnLocation().add(0.5, 0, 0.5);
        if (target == null) target = world.getSpawnLocation();
        player.teleport(target);
        send(player, "teleport.spawn-message", "<gray>Вы телепортированы на <#7B8FFB>спавн</#7B8FFB>.</gray>");
    }

    private void send(Player player, String path, String def) {
        String msg = plugin.getConfig().getString(path, def);
        if (msg != null && !msg.isEmpty()) player.sendMessage(ColorUtil.parse(msg));
    }
}
