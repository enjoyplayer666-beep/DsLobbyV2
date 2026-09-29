package ru.dscraft.mediatab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * У каждого игрока ОДИН персональный Scoreboard на всю сессию: в нём живут и команды
 * (сортировка таба + префикс/суффикс над головой), и сайдбар. Состояние каждого игрока
 * зеркалится во все персональные board'ы.
 * <p>
 * Обновление идёт только при реальном изменении (иначе каждую секунду летели бы
 * N x N пакетов команд), либо принудительно - при входе нового игрока.
 */
public class PlayerBoardService {

    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** target uuid -> последнее применённое состояние (имя команды + оформление). */
    private final Map<UUID, EntryState> applied = new ConcurrentHashMap<>();

    private record EntryState(String teamName, Component prefix, Component suffix, NamedTextColor color) {
    }

    public Scoreboard createBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        return board;
    }

    public Scoreboard getOrCreateBoard(Player player) {
        Scoreboard existing = boards.get(player.getUniqueId());
        if (existing != null) return existing;
        return createBoard(player);
    }

    /**
     * Обновляет команду игрока {@code target} во всех board'ах.
     *
     * @param force true - применить даже если ничего не поменялось (новый board, reload)
     */
    public void applyEntryToAll(Player target, String teamName, Component prefix, Component suffix,
                                NamedTextColor color, boolean force) {
        EntryState next = new EntryState(teamName, prefix, suffix, color);
        EntryState previous = applied.get(target.getUniqueId());
        if (!force && Objects.equals(previous, next)) return;

        String entryName = target.getName();
        String previousTeam = previous != null ? previous.teamName() : null;

        for (Scoreboard board : boards.values()) {
            if (previousTeam != null && !previousTeam.equals(teamName)) {
                Team oldTeam = board.getTeam(previousTeam);
                if (oldTeam != null) {
                    oldTeam.removeEntry(entryName);
                    if (oldTeam.getEntries().isEmpty()) oldTeam.unregister();
                }
            }

            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            if (!team.hasEntry(entryName)) team.addEntry(entryName);
            team.prefix(prefix);
            team.suffix(suffix);
            if (color != null) team.color(color);
        }

        applied.put(target.getUniqueId(), next);
    }

    /** Убирает игрока из всех команд во всех board'ах (вызывать на выходе, ПЕРЕД removeBoard). */
    public void removeEntryEverywhere(Player target) {
        EntryState state = applied.remove(target.getUniqueId());
        if (state == null) return;
        String entryName = target.getName();
        for (Scoreboard board : boards.values()) {
            Team team = board.getTeam(state.teamName());
            if (team != null) {
                team.removeEntry(entryName);
                if (team.getEntries().isEmpty()) team.unregister();
            }
        }
    }

    public void removeBoard(Player player) {
        boards.remove(player.getUniqueId());
    }
}
