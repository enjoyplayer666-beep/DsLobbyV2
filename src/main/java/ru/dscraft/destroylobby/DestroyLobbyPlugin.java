package ru.dscraft.destroylobby;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroylobby.command.PrefixCommand;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.hook.LuckPermsHook;
import ru.dscraft.destroylobby.hook.PlaceholderHook;
import ru.dscraft.destroylobby.listener.ChatListener;
import ru.dscraft.destroylobby.listener.LobbyJumpListener;
import ru.dscraft.destroylobby.listener.LobbyProtectionListener;
import ru.dscraft.destroylobby.listener.PlayerConnectionListener;
import ru.dscraft.destroylobby.listener.SpawnListener;
import ru.dscraft.destroylobby.scoreboard.ScoreboardManager;
import ru.dscraft.destroylobby.stats.StatsManager;
import ru.dscraft.destroylobby.tab.PlayerBoardService;
import ru.dscraft.destroylobby.tab.TabManager;
import ru.dscraft.destroylobby.visibility.VisibilityManager;

import java.io.File;
import java.util.List;

public final class DestroyLobbyPlugin extends JavaPlugin {

    private static DestroyLobbyPlugin instance;

    /** Версия формата config.yml. Старые конфиги (без этого ключа) заменяются новым. */
    private static final int CONFIG_VERSION = 2;

    /** Что переносим из старого конфига: миры, точка спавна, разрешённые команды, настройки /prefix. */
    private static final List<String> MIGRATED_PATHS = List.of(
            "worlds.lobby",
            "worlds.game-worlds",
            "lobby-protection.spawn",
            "lobby-protection.allowed-commands",
            "customprefix");

    private ConfigManager configManager;
    private LuckPermsHook luckPermsHook;
    private StatsManager statsManager;
    private PlayerBoardService boardService;
    private TabManager tabManager;
    private ScoreboardManager scoreboardManager;
    private VisibilityManager visibilityManager;

    @Override
    public void onEnable() {
        instance = this;

        migrateConfigIfOld();
        saveDefaultConfig();
        // дописываем в существующий config.yml новые настройки (чат и т.п.), не трогая старые значения
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.configManager = new ConfigManager(this);

        // Хук LuckPerms - обязателен для префиксов/суффиксов/веса и для /prefix, но плагин не падает если его нет
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            LuckPerms api = LuckPermsProvider.get();
            this.luckPermsHook = new LuckPermsHook(api, configManager);
            getLogger().info("LuckPerms найден, префиксы/суффиксы в табе и /prefix включены.");
        } else {
            this.luckPermsHook = new LuckPermsHook(null, configManager);
            getLogger().warning("LuckPerms не найден! Префиксы/суффиксы и /prefix работать не будут.");
        }

        logIntegration("Multiverse-Core");
        logIntegration("AdvancedPortals");
        logIntegration("DestroyChat");

        this.statsManager = new StatsManager(this);
        this.boardService = new PlayerBoardService();
        this.tabManager = new TabManager(this, configManager, luckPermsHook, boardService);
        this.scoreboardManager = new ScoreboardManager(this, configManager, statsManager, boardService);
        this.visibilityManager = new VisibilityManager(this, configManager);

        // PlaceholderAPI - регистрируем экспаншен, если плагин установлен
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this, statsManager, luckPermsHook, configManager).register();
            getLogger().info("PlaceholderAPI найден, плейсхолдеры %destroy_...% зарегистрированы.");
        }

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, tabManager, scoreboardManager, statsManager, visibilityManager), this);
        getServer().getPluginManager().registerEvents(
                new LobbyJumpListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                new ChatListener(configManager, visibilityManager), this);
        getServer().getPluginManager().registerEvents(
                new LobbyProtectionListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                new SpawnListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(
                statsManager, this);

        if (getCommand("prefix") != null) {
            PrefixCommand prefixCommand = new PrefixCommand(configManager, luckPermsHook, tabManager);
            getCommand("prefix").setExecutor(prefixCommand);
            getCommand("prefix").setTabCompleter(prefixCommand);
        }

        tabManager.startUpdateTask();
        scoreboardManager.startUpdateTask();

        // подхватываем игроков, которые уже онлайн (например после /reload плагинов)
        for (Player online : getServer().getOnlinePlayers()) {
            tabManager.handleJoin(online);
            scoreboardManager.handleJoin(online);
        }
        visibilityManager.updateAll();

        getLogger().info("DestroyLobby включен.");
    }

    @Override
    public void onDisable() {
        if (visibilityManager != null) visibilityManager.showEveryone();
        if (tabManager != null) tabManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (statsManager != null) {
            statsManager.saveAll();
        }
        getLogger().info("DestroyLobby выключен.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroylobby")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                configManager.reload();
                tabManager.refreshAll();
                scoreboardManager.refreshAll();
                visibilityManager.updateAll();
                sender.sendMessage("§a[DestroyLobby] Конфиг перезагружен.");
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("setspawn")) {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                configManager.setLobbySpawn(player.getLocation());
                sender.sendMessage("§a[DestroyLobby] Точка спавна лобби установлена в текущей позиции. "
                        + "Рекомендуется также выполнить /mvsetspawn в этом же месте.");
                return true;
            }

            sender.sendMessage("§7Использование: /destroylobby reload|setspawn");
            return true;
        }

        if (command.getName().equalsIgnoreCase("coins")) {
            return handleCoinsCommand(sender, args);
        }

        return false;
    }

    /**
     * Старый config.yml (от прошлой версии) содержит старое оформление таба/скорборда,
     * например лишнюю строку в футере лобби. Такой конфиг переименовывается в
     * config-old-*.yml, на его место кладётся новый, а миры/спавн/команды переносятся.
     */
    private void migrateConfigIfOld() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;

        YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
        if (old.getInt("config-version", 0) >= CONFIG_VERSION) return;

        File backup = new File(getDataFolder(), "config-old-" + System.currentTimeMillis() + ".yml");
        if (!file.renameTo(backup)) {
            getLogger().warning("Не удалось переименовать старый config.yml - удали его вручную, "
                    + "иначе останется старое оформление таба.");
            return;
        }

        saveDefaultConfig();
        reloadConfig();
        FileConfiguration cfg = getConfig();
        for (String path : MIGRATED_PATHS) {
            if (!old.contains(path)) continue;
            if (old.isConfigurationSection(path)) {
                cfg.set(path, null); // убираем значения по умолчанию, чтобы не смешались со старыми
                for (String key : old.getConfigurationSection(path).getKeys(true)) {
                    String full = path + "." + key;
                    if (!old.isConfigurationSection(full)) cfg.set(full, old.get(full));
                }
            } else {
                cfg.set(path, old.get(path));
            }
        }
        saveConfig();
        getLogger().info("Старый config.yml сохранён как " + backup.getName()
                + ", создан новый. Миры, точка спавна и разрешённые команды перенесены.");
    }

    private void logIntegration(String name) {
        if (getServer().getPluginManager().getPlugin(name) != null) {
            getLogger().info(name + " найден, работаем в паре.");
        } else {
            getLogger().warning(name + " не найден.");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
        return null;
    }

    private boolean handleCoinsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§7Использование: /coins <give|take|set> <ник> <сумма>");
            return true;
        }
        String action = args[0].toLowerCase();
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден или не в сети.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cСумма должна быть числом.");
            return true;
        }

        switch (action) {
            case "give" -> statsManager.get(target).addCoins(amount);
            case "take" -> statsManager.get(target).addCoins(-amount);
            case "set" -> statsManager.get(target).setCoins(amount);
            default -> {
                sender.sendMessage("§7Использование: /coins <give|take|set> <ник> <сумма>");
                return true;
            }
        }
        sender.sendMessage("§aОК. Коины игрока " + target.getName() + ": " + statsManager.get(target).getCoins());
        return true;
    }

    public static DestroyLobbyPlugin getInstance() {
        return instance;
    }
}
