package ru.dscraft.destroylobby.tab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.dscraft.destroylobby.DestroyLobbyPlugin;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.hook.LuckPermsHook;
import ru.dscraft.destroylobby.util.ColorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хедер/футер таба + как выглядит строка каждого игрока.
 * <p>
 * Почему раньше цвета "не совпадали": цвет ника задавался через цвет команды (Team#color),
 * а он умеет только 16 стандартных цветов - любой hex (#CDCDFF и т.п.) округлялся.
 * Теперь строка в табе собирается целиком как {@code playerListName} (префикс + ник + суффикс,
 * любой hex и градиенты), а команда отвечает только за сортировку и за ник над головой.
 * <p>
 * Лобби: у всех одинаковый префикс/цвет ({@code tab.lobby.*}), LuckPerms игнорируется.
 * Игровой мир: префикс/суффикс из LuckPerms, если префикса нет - {@code tab.game.default-prefix}.
 */
public class TabManager {

    private static final String TEAM_PREFIX = "dlb_";

    private final DestroyLobbyPlugin plugin;
    private final ConfigManager configManager;
    private final LuckPermsHook luckPermsHook;
    private final PlayerBoardService boardService;

    /** Последний выставленный playerListName - чтобы не слать одинаковые пакеты каждую секунду. */
    private final Map<UUID, Component> lastListName = new ConcurrentHashMap<>();

    private BukkitTask task;

    public TabManager(DestroyLobbyPlugin plugin, ConfigManager configManager,
                      LuckPermsHook luckPermsHook, PlayerBoardService boardService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.luckPermsHook = luckPermsHook;
        this.boardService = boardService;
    }

    public void startUpdateTask() {
        long interval = configManager.getTabUpdateInterval();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateHeaderFooter(player);
                updatePlayerTeam(player, false);
            }
        }, 20L, interval);
    }

    /** Полный пересчёт с принудительной отправкой (вход игрока, /destroylobby reload). */
    public void refreshAll() {
        lastListName.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHeaderFooter(player);
            updatePlayerTeam(player, true);
        }
    }

    public void handleJoin(Player player) {
        boardService.createBoard(player);
        refreshAll();
    }

    public void handleQuit(Player player) {
        boardService.removeEntryEverywhere(player);
        boardService.removeBoard(player);
        lastListName.remove(player.getUniqueId());
    }

    public void updateHeaderFooter(Player player) {
        String worldName = player.getWorld().getName();
        boolean lobby = configManager.isLobbyWorld(worldName);

        List<String> header = lobby ? configManager.getTabHeaderLobby() : configManager.getTabHeaderGame();
        List<String> footer = lobby ? configManager.getTabFooterLobby() : configManager.getTabFooterGame();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", configManager.getDisplayNameForWorld(worldName));
        placeholders.put("player", player.getName());
        placeholders.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.put("max", String.valueOf(Bukkit.getMaxPlayers()));

        player.sendPlayerListHeaderAndFooter(
                ColorUtil.parseLines(header, placeholders),
                ColorUtil.parseLines(footer, placeholders));
    }

    /** Вызывается извне (например после /prefix) - применить сразу. */
    public void updatePlayerTeam(Player player) {
        updatePlayerTeam(player, false);
    }

    public void updatePlayerTeam(Player player, boolean force) {
        boolean lobby = configManager.isLobbyWorld(player.getWorld().getName());

        Component prefix;
        Component suffix;
        TextColor nameColor;

        if (lobby) {
            prefix = ColorUtil.parse(configManager.getLobbySharedPrefix());
            suffix = Component.empty();
            nameColor = ColorUtil.parseColor(configManager.getLobbySharedNameColor(), NamedTextColor.GRAY);
        } else {
            String rawPrefix = "";
            String rawSuffix = "";
            if (configManager.tabShowPrefixesGame()) {
                rawPrefix = luckPermsHook.getPrefix(player);
                rawSuffix = luckPermsHook.getSuffix(player);
            }
            if (rawPrefix.isEmpty()) {
                rawPrefix = configManager.getGameDefaultPrefix();
            }
            if (!rawSuffix.isEmpty() && configManager.tabSuffixAutoSpace() && !rawSuffix.startsWith(" ")) {
                rawSuffix = " " + rawSuffix;
            }
            prefix = ColorUtil.legacy(rawPrefix);
            suffix = ColorUtil.legacy(rawSuffix);
            nameColor = ColorUtil.parseColor(
                    configManager.getNickColorForGroup(luckPermsHook.getPrimaryGroupName(player)),
                    NamedTextColor.WHITE);
        }

        // 1) строка в табе - полноценный компонент, поддерживает любой hex
        Component listName = Component.empty()
                .append(prefix)
                .append(Component.text(player.getName(), nameColor))
                .append(suffix);
        Component previous = lastListName.get(player.getUniqueId());
        if (force || !Objects.equals(previous, listName)) {
            player.playerListName(listName);
            lastListName.put(player.getUniqueId(), listName);
        }

        // 2) команда - сортировка по весу LuckPerms + ник над головой
        int weight = luckPermsHook.getWeight(player);
        int sortKey = Math.max(0, 99999 - weight); // больший weight -> выше в списке
        String teamName = TEAM_PREFIX + String.format("%05d", sortKey) + "_" + shortUuid(player.getUniqueId());

        boardService.applyEntryToAll(player, teamName, prefix, suffix, ColorUtil.toNamed(nameColor), force);
    }

    private String shortUuid(UUID uuid) {
        return uuid.toString().replace("-", "").substring(0, 8);
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
