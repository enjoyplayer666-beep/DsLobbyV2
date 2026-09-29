package ru.dscraft.mediatab;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Персональная боковая панель каждому игроку, по умолчанию только вне лобби.
 * Цифры справа скрыты (scoreboard.hide-numbers). Сайдбар живёт на том же персональном board'е,
 * что и команды таба (см. {@link PlayerBoardService}), поэтому сортировку и ники над головой не трогает.
 */
final class ScoreboardManager {

    private static final String OBJECTIVE_ID = "destroy_sb";
    private static final String LINE_TEAM_PREFIX = "dlsb_";

    private final Settings settings;
    private final PlayerBoardService boardService;

    /** Какие entry-строки сейчас используются под сайдбар каждого игрока - чтобы корректно чистить старые строки. */
    private final Map<UUID, List<String>> activeEntries = new ConcurrentHashMap<>();

    ScoreboardManager(Settings settings, PlayerBoardService boardService) {
        this.settings = settings;
        this.boardService = boardService;
    }

    void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    void handleJoin(Player player) {
        update(player);
    }

    void handleQuit(Player player) {
        activeEntries.remove(player.getUniqueId());
        // сам персональный board игрока целиком уничтожается вместе с ним в PlayerBoardService,
        // доп. действий тут не нужно.
    }

    void update(Player player) {
        Scoreboard board = boardService.getOrCreateBoard(player);

        String worldName = player.getWorld().getName();
        boolean isLobby = settings.isLobbyWorld(worldName);
        boolean shouldShow = !isLobby || settings.scoreboardInLobby();

        if (!shouldShow) {
            clearSidebar(player, board);
            return;
        }

        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_ID, "dummy", ColorUtil.parse(buildTitle(player)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            if (settings.hideNumbers()) {
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

        Map<String, String> placeholders = buildPlaceholders(player);

        List<String> lines = settings.scoreboardLines();
        int size = lines.size();
        List<String> usedEntries = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String rawLine = lines.get(i);
            Component lineComponent = ColorUtil.parse(Hooks.papi(player, rawLine), placeholders);
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
        String display = settings.worldDisplayName(worldName);
        return Hooks.papi(player, settings.scoreboardTitle()).replace("{world}", display);
    }

    private Map<String, String> buildPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("player", player.getName());
        double hp = player.getHealth();
        if (settings.healthInHearts()) {
            hp = hp / 2.0; // 20 единиц = 10 сердечек, как "ХП: 10" на скрине
        }
        map.put("health", String.valueOf((int) Math.ceil(hp)));
        map.put("coins", Hooks.coins(player));
        map.put("kills", Hooks.kills(player));
        map.put("deaths", Hooks.deaths(player));
        map.put("world", settings.worldDisplayName(player.getWorld().getName()));
        map.put("date", new SimpleDateFormat(settings.dateFormat()).format(new Date()));
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
}
