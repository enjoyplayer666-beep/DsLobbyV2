package ru.dscraft.mediatab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Шапка/низ таба и строка каждого игрока.
 * <p>
 * Строка в табе собирается целиком как {@code playerListName} (префикс + ник + суффикс,
 * любой hex и градиенты), а команда отвечает только за сортировку по весу LuckPerms и за ник над головой.
 * <p>
 * Лобби: у всех одинаковый префикс/цвет ({@code tab.lobby.*}).
 * Игровой мир: префикс/суффикс из LuckPerms (без префикса - {@code tab.game.default-prefix}),
 * цвет ника - хвост префикса, иначе белый у команды проекта и серый у остальных.
 */
final class TabManager {

    private static final String TEAM_PREFIX = "dlb_";

    private final Settings settings;
    private final LuckPermsHook luckPerms; // null - LuckPerms не установлен
    private final PlayerBoardService boards;

    /** Последний выставленный playerListName - чтобы не слать одинаковые пакеты каждую секунду. */
    private final Map<UUID, Component> lastListName = new ConcurrentHashMap<>();

    TabManager(Settings settings, LuckPermsHook luckPerms, PlayerBoardService boards) {
        this.settings = settings;
        this.luckPerms = luckPerms;
        this.boards = boards;
    }

    void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHeaderFooter(player);
            updatePlayer(player, false);
        }
    }

    /** Полный пересчёт с принудительной отправкой (вход игрока, /mediatab reload). */
    void refreshAll() {
        lastListName.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHeaderFooter(player);
            updatePlayer(player, true);
        }
    }

    void handleJoin(Player player) {
        boards.createBoard(player);
        refreshAll();
    }

    void handleQuit(Player player) {
        boards.removeEntryEverywhere(player);
        boards.removeBoard(player);
        lastListName.remove(player.getUniqueId());
    }

    void updateHeaderFooter(Player player) {
        String world = player.getWorld().getName();
        boolean lobby = settings.isLobbyWorld(world);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", settings.worldDisplayName(world));
        placeholders.put("player", player.getName());
        placeholders.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.put("max", String.valueOf(Bukkit.getMaxPlayers()));

        player.sendPlayerListHeaderAndFooter(
                lines(player, settings.header(lobby), placeholders),
                lines(player, settings.footer(lobby), placeholders));
    }

    private static Component lines(Player player, List<String> lines, Map<String, String> placeholders) {
        return ColorUtil.parseLines(lines.stream().map(l -> Hooks.papi(player, l)).toList(), placeholders);
    }

    void updatePlayer(Player player, boolean force) {
        boolean lobby = settings.isLobbyWorld(player.getWorld().getName());

        Component prefix;
        Component suffix;
        Component name;
        TextColor nameColor;

        if (lobby) {
            prefix = ColorUtil.parse(settings.lobbyPrefix());
            suffix = Component.empty();
            nameColor = ColorUtil.parseColor(settings.lobbyNameColor(), NamedTextColor.GRAY);
            name = Component.text(player.getName(), nameColor);
        } else {
            String rawPrefix = "";
            String rawSuffix = "";
            if (settings.showPrefixes() && luckPerms != null) {
                rawPrefix = luckPerms.prefix(player);
                rawSuffix = luckPerms.suffix(player);
            }
            // хвост префикса из /prefix set - цвет ника
            NameStyle.Split split = NameStyle.split(rawPrefix);
            String nickStyle = split.nickStyle();
            rawPrefix = split.prefix() == null ? "" : split.prefix();
            // оформление группы из tab.game.group-formats (Elite и т.п.), если нет своего /prefix set
            // команда проекта - всегда со своим префиксом, даже если её группа наследует elitesp
            Settings.GroupFormat format = isStaff(player) || (luckPerms != null && luckPerms.hasOwnPrefix(player))
                    ? null : settings.groupFormat(g -> player.hasPermission("group." + g));
            if (format != null) {
                if (format.prefix() != null) rawPrefix = format.prefix();
                if (format.nameStyle() != null && !format.nameStyle().isBlank()) nickStyle = format.nameStyle();
                if (format.suffix() != null) rawSuffix = format.suffix();
            }
            if (rawPrefix.isEmpty()) {
                rawPrefix = settings.defaultPrefix();
                // обычный игрок: "⚔ ник" - ник своим цветом
                if (nickStyle == null && !isStaff(player)) nickStyle = settings.defaultNameColor();
            }
            if (!rawSuffix.isEmpty() && settings.suffixAutoSpace() && !rawSuffix.startsWith(" ")) {
                rawSuffix = " " + rawSuffix;
            }
            if (nickStyle == null) nickStyle = isStaff(player) ? settings.staffNameColor() : settings.nameColor();

            prefix = ColorUtil.rich(rawPrefix);
            suffix = ColorUtil.rich(rawSuffix);
            // титул из MediaItems - между ником и суффиксом: "Ник титул ✔"
            Component title = Hooks.title(player);
            if (!Component.empty().equals(title)) {
                suffix = Component.text(" ").append(title).append(suffix);
            }
            nameColor = ColorUtil.parseColor(nickStyle, NamedTextColor.GRAY);
            name = Component.empty().append(ColorUtil.rich(nickStyle + player.getName()));
        }

        // 1) строка в табе - полноценный компонент, поддерживает любой hex и градиент
        Component listName = Component.empty().append(prefix).append(name).append(suffix);
        if (force || !Objects.equals(lastListName.get(player.getUniqueId()), listName)) {
            player.playerListName(listName);
            lastListName.put(player.getUniqueId(), listName);
        }

        // 2) команда - сортировка по весу LuckPerms + ник над головой
        int weight = luckPerms == null ? 0 : luckPerms.weight(player);
        int sortKey = Math.max(0, 99999 - weight); // больший weight -> выше в списке
        String teamName = TEAM_PREFIX + String.format("%05d", sortKey) + "_"
                + player.getUniqueId().toString().replace("-", "").substring(0, 8);
        boards.applyEntryToAll(player, teamName, prefix, suffix, ColorUtil.toNamed(nameColor), force);
    }

    private boolean isStaff(Player player) {
        for (String group : settings.staffGroups()) {
            if (player.hasPermission("group." + group)) return true;
        }
        return false;
    }
}
