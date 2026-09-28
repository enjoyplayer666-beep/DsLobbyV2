package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.Locale;

/**
 * Неизвестная команда или команда, на которую у игрока нет права - одно сообщение
 * "Нет такой команды :/" (unknown-command.message) вместо стандартных сообщений сервера.
 * Недоступные команды ещё и не подсказываются по Tab.
 * <p>
 * Работает последним (HIGHEST, ignoreCancelled): команды, которые уже перехватили другие
 * обработчики (/spawn, /warps, /menu и т.п.), сюда не доходят.
 */
public class UnknownCommandListener implements Listener {

    private final DestroyLobbyPlugin plugin;

    public UnknownCommandListener(DestroyLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("unknown-command.enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        String msg = event.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return;

        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command != null && command.testPermissionSilent(player)) return;

        event.setCancelled(true);
        String text = plugin.getConfig().getString("unknown-command.message", "<#CCCCFF>Нет такой команды :/");
        player.sendMessage(ColorUtil.parse(text));
    }

    /** Список команд для Tab: убираем те, на которые нет права. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("unknown-command.hide-in-tab", true)) return;
        Player player = event.getPlayer();
        event.getCommands().removeIf(name -> {
            Command command = Bukkit.getCommandMap().getCommand(name);
            return command != null && !command.testPermissionSilent(player);
        });
    }
}
