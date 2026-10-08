package ru.dscraft.mediatab;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/** Настройки из config.yml. */
final class Settings {

    private final MediaTabPlugin plugin;

    Settings(MediaTabPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---- миры ----

    boolean isLobbyWorld(String world) {
        return cfg().getString("worlds.lobby", "world").equalsIgnoreCase(world);
    }

    String worldDisplayName(String world) {
        if (isLobbyWorld(world)) return cfg().getString("worlds.lobby-display-name", "lobby");
        ConfigurationSection games = cfg().getConfigurationSection("worlds.game-worlds");
        return games != null && games.contains(world) ? games.getString(world, world) : world;
    }

    // ---- таб ----

    long tabInterval() {
        return Math.max(1, cfg().getLong("tab.update-interval-ticks", 20));
    }

    /** Цвет перед эмодзи у ника (&f - родные цвета значков из ресурс-пака). */
    String emojiColor() {
        return cfg().getString("tab.emoji-color", "&f");
    }

    List<String> header(boolean lobby) {
        return lines(lobby ? "tab.lobby.header" : "tab.game.header");
    }

    List<String> footer(boolean lobby) {
        return lines(lobby ? "tab.lobby.footer" : "tab.game.footer");
    }

    /** Строка ИЛИ список строк из конфига -> список строк. */
    private List<String> lines(String path) {
        if (cfg().isList(path)) return cfg().getStringList(path);
        String single = cfg().getString(path, "");
        return single == null || single.isEmpty() ? List.of() : List.of(single);
    }

    String lobbyPrefix() {
        return cfg().getString("tab.lobby.shared-prefix", "");
    }

    String lobbyNameColor() {
        return cfg().getString("tab.lobby.shared-name-color", "&7");
    }

    boolean showPrefixes() {
        return cfg().getBoolean("tab.game.show-prefixes", true);
    }

    String defaultPrefix() {
        return cfg().getString("tab.game.default-prefix", "");
    }

    boolean suffixAutoSpace() {
        return cfg().getBoolean("tab.game.suffix-auto-space", true);
    }

    /** Префикс группы персонала в табе (значок из ресурс-пака), "" - взять из LuckPerms. */
    String staffPrefix(String group) {
        return cfg().getString("tab.game.staff-prefixes." + group, "");
    }

    /** Суффикс группы персонала в табе: staff-suffixes.<группа>, иначе общий staff-suffix ("" - из LuckPerms). */
    String staffSuffix(String group) {
        String own = cfg().getString("tab.game.staff-suffixes." + group, null);
        if (own != null) return own;
        return cfg().getString("tab.game.staff-suffix", "&a&l✔");
    }

    /** Цвет ника обычного игрока (без префикса, группа default). */
    String defaultNameColor() {
        return cfg().getString("tab.game.default-name-color", "&#CDCDFF");
    }

    /** Оформление группы в табе: префикс, стиль ника, суффикс (null - оставить из LuckPerms). */
    record GroupFormat(String prefix, String nameStyle, String suffix) {
    }

    /** Первая группа сверху вниз из tab.game.group-formats, которая есть у игрока; null - нет. */
    GroupFormat groupFormat(java.util.function.Predicate<String> hasGroup) {
        ConfigurationSection s = cfg().getConfigurationSection("tab.game.group-formats");
        if (s == null) return null;
        for (String group : s.getKeys(false)) {
            if (hasGroup.test(group)) {
                return new GroupFormat(s.getString(group + ".prefix"), s.getString(group + ".name-style"),
                        s.getString(group + ".suffix"));
            }
        }
        return null;
    }

    String nameColor() {
        return cfg().getString("tab.game.name-color", "&7");
    }

    String staffNameColor() {
        return cfg().getString("tab.game.staff-name-color", "&f");
    }

    List<String> staffGroups() {
        return cfg().getStringList("tab.game.staff-groups").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    // ---- скорборд ----

    boolean scoreboardInLobby() {
        return cfg().getBoolean("scoreboard.show-in-lobby", false);
    }

    long scoreboardInterval() {
        return Math.max(1, cfg().getLong("scoreboard.update-interval-ticks", 20));
    }

    boolean hideNumbers() {
        return cfg().getBoolean("scoreboard.hide-numbers", true);
    }

    boolean healthInHearts() {
        return "hearts".equalsIgnoreCase(cfg().getString("scoreboard.health-mode", "hearts"));
    }

    String dateFormat() {
        return cfg().getString("scoreboard.date-format", "dd.MM.yyyy");
    }

    String scoreboardTitle() {
        return cfg().getString("scoreboard.game.title", "<white>{world}</white>");
    }

    List<String> scoreboardLines() {
        return cfg().getStringList("scoreboard.game.lines");
    }
}
