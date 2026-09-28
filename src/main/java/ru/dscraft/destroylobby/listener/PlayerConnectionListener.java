package ru.dscraft.destroylobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.scoreboard.ScoreboardManager;
import ru.dscraft.destroylobby.stats.StatsManager;
import ru.dscraft.destroylobby.tab.TabManager;
import ru.dscraft.destroylobby.visibility.VisibilityManager;

public class PlayerConnectionListener implements Listener {

    private final DestroyLobbyPlugin plugin;
    private final TabManager tabManager;
    private final ScoreboardManager scoreboardManager;
    private final StatsManager statsManager;
    private final VisibilityManager visibilityManager;

    public PlayerConnectionListener(DestroyLobbyPlugin plugin, TabManager tabManager,
                                    ScoreboardManager scoreboardManager, StatsManager statsManager,
                                    VisibilityManager visibilityManager) {
        this.plugin = plugin;
        this.tabManager = tabManager;
        this.scoreboardManager = scoreboardManager;
        this.statsManager = statsManager;
        this.visibilityManager = visibilityManager;
    }

    // MONITOR - после SpawnListener (HIGHEST), игрок уже стоит в лобби
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        statsManager.get(player); // прогреваем кэш статистики
        tabManager.handleJoin(player);
        scoreboardManager.handleJoin(player);
        visibilityManager.update(player);

        // страховка: SpawnListener может повторно перенести игрока через 3 тика
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            visibilityManager.update(player);
            tabManager.updateHeaderFooter(player);
            tabManager.updatePlayerTeam(player, true);
            scoreboardManager.update(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tabManager.handleQuit(event.getPlayer());
        scoreboardManager.handleQuit(event.getPlayer());
        statsManager.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // портал лобби -> SkyPvP: сразу меняем таб, видимость и скорборд, не ждём таймера
        visibilityManager.update(player);
        tabManager.updateHeaderFooter(player);
        tabManager.updatePlayerTeam(player, false);
        scoreboardManager.update(player);
    }
}
