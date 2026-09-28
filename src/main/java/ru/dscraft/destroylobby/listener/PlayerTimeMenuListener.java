package ru.dscraft.destroylobby.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.List;
import java.util.Locale;

/**
 * /ptime без аргументов - меню выбора времени в чате (ptime-menu в config.yml),
 * пункты кликабельные и запускают /ptime <время> от Essentials. С аргументами команда
 * уходит в Essentials как обычно.
 */
public class PlayerTimeMenuListener implements Listener {

    private final DestroyLobbyPlugin plugin;

    public PlayerTimeMenuListener(DestroyLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("ptime-menu.enabled", true)) return;
        String[] parts = event.getMessage().trim().substring(1).split("\\s+");
        if (parts.length != 1) return;
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (!label.equals("ptime") && !label.equals("essentials:ptime") && !label.equals("eptime")) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("essentials.ptime")) return; // пусть ответит "Нет такой команды"

        event.setCancelled(true);
        List<String> lines = plugin.getConfig().getStringList("ptime-menu.lines");
        for (String line : lines) player.sendMessage(ColorUtil.parse(line));
    }
}
