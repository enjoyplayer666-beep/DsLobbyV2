package ru.dscraft.destroylobby.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.stats.PlayerStats;
import ru.dscraft.destroylobby.stats.StatsManager;
import ru.dscraft.destroylobby.tab.PlayerBoardService;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Персональная боковая панель каждому игроку, показывается только вне лобби -
 * как на скрине "SkyPvP". Цифры справа скрыты (scoreboard.hide-numbers).
 * <p>
 * ВАЖНО: раньше этот класс подменял игроку scoreboard целиком новым объектом,
 * что стирало все Team-команды таба (см. javadoc {@link PlayerBoardService}).
 * Теперь сайдбар живёт на том же персональном board'е, что и команды таба -
 * здесь только objective/scores/строчные "dlsb_" команды, сортировку и
 * префиксы игроков это больше не трогает.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "destroy_sb";
    private static final String LINE_TEAM_PREFIX = "dlsb_";

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final PlayerBoardService boardService;

    /** Какие entry-строки сейчас используются под сайдбар каждого игрока - чтобы корректно чистить старые строки. */
    private final Map<UUID, List<String>> activeEntries = new ConcurrentHashMap<>();

    private BukkitTask task;

    public ScoreboardManager(DestroyLobbyPlugin plugin, ConfigManager configManager,
                              StatsManager statsManager, PlayerBoardService boardService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.boardService = boardService;
    }

    public void startUpdateTask() {
        long interval = configManager.getScoreboardUpdateInterval();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                update(player);
            }
        }, 20L, interval);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void handleJoin(Player player) {
        update(player);
    }

    public void handleQuit(Player player) {
        activeEntries.remove(player.getUniqueId());
        // сам персональный board игрока целиком уничтожается вместе с ним в PlayerBoardService,
        // доп. действий тут не нужно.
    }

    public void update(Player player) {
        Scoreboard board = boardService.getOrCreateBoard(player);

        String worldName = player.getWorld().getName();
        boolean isLobby = configManager.isLobbyWorld(worldName);
        boolean shouldShow = !isLobby || configManager.scoreboardShowInLobby();

        if (!shouldShow) {
            clearSidebar(player, board);
            return;
        }

        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_ID, "dummy", ColorUtil.parse(buildTitle(player)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            if (configManager.scoreboardHideNumbers()) {
                // убирает красные цифры справа (как на сервере-образце), Paper 1.20.4+
                objective.numberFormat(NumberFormat.blank());
            }
        } else {
            objective.displayName(ColorUtil.parse(buildTitle(player)));
        }

        // чистим строки от прошлого обновления (длина списка в конфиге могла измениться после reload)
        List<String> previousEntries = activeEntries.get(player.getUniqueId());
        if (previousEntries != null) {
            for (String entry : previousEntries) {
                board.resetScores(entry);
            }
        }

        PlayerStats stats = statsManager.get(player);
        Map<String, String> placeholders = buildPlaceholders(player, stats);

        List<String> lines = configManager.getScoreboardLines();
        int size = lines.size();
        List<String> usedEntries = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String rawLine = lines.get(i);
            Component lineComponent = ColorUtil.parse(rawLine, placeholders);
            int score = size - i;

            String teamName = LINE_TEAM_PREFIX + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            String entry = generateUniqueEntry(i, usedEntries);
            usedEntries.add(entry);
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.prefix(lineComponent);

            objective.getScore(entry).setScore(score);
        }

        activeEntries.put(player.getUniqueId(), usedEntries);
    }

    private void clearSidebar(Player player, Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective != null) {
            objective.unregister();
        }
        List<String> previousEntries = activeEntries.remove(player.getUniqueId());
        if (previousEntries != null) {
            for (String entry : previousEntries) {
                board.resetScores(entry);
                Team team = board.getEntryTeam(entry);
                if (team != null && team.getName().startsWith(LINE_TEAM_PREFIX)) {
                    team.removeEntry(entry);
                    if (team.getEntries().isEmpty()) {
                        team.unregister();
                    }
                }
            }
        }
    }

    private String buildTitle(Player player) {
        String worldName = player.getWorld().getName();
        String display = configManager.getDisplayNameForWorld(worldName);
        return configManager.getScoreboardTitle().replace("{world}", display);
    }

    private Map<String, String> buildPlaceholders(Player player, PlayerStats stats) {
        Map<String, String> map = new HashMap<>();
        map.put("player", player.getName());
        double hp = player.getHealth();
        if ("hearts".equalsIgnoreCase(configManager.scoreboardHealthMode())) {
            hp = hp / 2.0; // 20 единиц = 10 сердечек, как "ХП: 10" на скрине
        }
        map.put("health", String.valueOf((int) Math.ceil(hp)));
        map.put("coins", String.valueOf(stats.getCoins()));
        map.put("kills", String.valueOf(stats.getKills()));
        map.put("deaths", String.valueOf(stats.getDeaths()));
        map.put("date", new SimpleDateFormat(configManager.getScoreboardDateFormat()).format(new Date()));
        return map;
    }

    /**
     * Использует символы форматирования §0..§f как "невидимые" уникальные записи (entries),
     * чтобы можно было выводить несколько одинаковых/пустых строк подряд без конфликтов.
     */
    private String generateUniqueEntry(int index, List<String> used) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int digit = n % 16;
            sb.append("§").append(Integer.toHexString(digit));
            n /= 16;
        }
        String candidate = sb.toString();
        while (candidate.isEmpty() || used.contains(candidate)) {
            candidate = candidate + "§r";
        }
        return candidate;
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
