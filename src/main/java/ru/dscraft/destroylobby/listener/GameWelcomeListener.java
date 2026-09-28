package ru.dscraft.destroylobby.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.List;

/**
 * Портал лобби -> игровой мир (SkyPvP): чат очищается и выводится приветствие игрового мира
 * (game-welcome в config.yml). Строки - MiniMessage, "&lt;center&gt;" в начале - по центру чата.
 */
public class GameWelcomeListener implements Listener {

    private static final String CENTER = "<center>";
    /** Ширина чата в пикселях при стандартных настройках. */
    private static final int CHAT_WIDTH = 320;

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;

    public GameWelcomeListener(DestroyLobbyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("game-welcome.enabled", true)) return;
        // только переход из лобби в игровой мир, не SkyPvP -> незер и т.п.
        if (!configManager.isLobbyWorld(event.getFrom().getName())) return;
        if (!configManager.isGameWorld(player.getWorld().getName())) return;

        // через тик: другие плагины успевают написать своё при смене мира, и оно тоже очистится
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (cfg.getBoolean("game-welcome.clear-chat", true)) {
                int lines = Math.max(0, cfg.getInt("game-welcome.clear-lines", 100));
                for (int i = 0; i < lines; i++) player.sendMessage(Component.empty());
            }
            List<String> text = cfg.getStringList("game-welcome.lines");
            for (String line : text) player.sendMessage(render(line));
        }, 2L);
    }

    private static Component render(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        if (!raw.startsWith(CENTER)) return ColorUtil.parse(raw);
        Component parsed = ColorUtil.parse(raw.substring(CENTER.length()));
        int free = CHAT_WIDTH - width(ColorUtil.plain(parsed));
        int spaces = free > 0 ? free / 2 / 4 : 0; // пробел = 4 px
        return spaces > 0 ? Component.text(" ".repeat(spaces)).append(parsed) : parsed;
    }

    /** Ширина текста в пикселях стандартного шрифта Minecraft (с промежутком после символа). */
    private static int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += switch (c) {
                case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case 'I', '[', ']', 't', ' ' -> 4;
                case 'f', 'k', '"', '(', ')', '*', '<', '>', '{', '}' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
        }
        return w;
    }
}
