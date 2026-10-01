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
import ru.dscraft.destroylobby.listener.GameWelcomeListener;
import ru.dscraft.destroylobby.listener.TeleportCommandListener;
import ru.dscraft.destroylobby.listener.UnknownCommandListener;
import ru.dscraft.destroylobby.listener.PlayerTimeMenuListener;
import ru.dscraft.destroylobby.listener.PlayerConnectionListener;
import ru.dscraft.destroylobby.listener.SpawnListener;
import ru.dscraft.destroylobby.stats.StatsManager;
import ru.dscraft.destroylobby.api.LobbyApi;
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
    private VisibilityManager visibilityManager;

    @Override
    public void onEnable() {
        instance = this;

        migrateConfigIfOld();
        saveDefaultConfig();
        // дописываем в существующий config.yml новые настройки (чат и т.п.), не трогая старые значения
        getConfig().options().copyDefaults(true);
        migrateJumpSettings();
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
        LobbyApi.init(statsManager); // таб и скорборд - в плагине MediaTab, он берёт отсюда коины/убийства/смерти
        this.visibilityManager = new VisibilityManager(this, configManager);

        // PlaceholderAPI - регистрируем экспаншен, если плагин установлен
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this, statsManager, luckPermsHook, configManager).register();
            getLogger().info("PlaceholderAPI найден, плейсхолдеры %destroy_...% зарегистрированы.");
        }

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, statsManager, visibilityManager), this);
        getServer().getPluginManager().registerEvents(new GameWelcomeListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ru.dscraft.destroylobby.listener.LobbyWorldListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(new ru.dscraft.destroylobby.listener.LobbyPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new UnknownCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerTimeMenuListener(this), this);
        TeleportCommandListener teleports = new TeleportCommandListener(this, configManager);
        getServer().getPluginManager().registerEvents(teleports, this);
        if (getCommand("hub") != null) getCommand("hub").setExecutor(teleports);
        if (getCommand("spawn") != null) {
            getCommand("spawn").setExecutor(teleports);
            // /spawn есть и у Essentials - забираем себе, чтобы в чате он был известной командой (не красным)
            getServer().getScheduler().runTask(this, () -> {
                org.bukkit.command.PluginCommand ours = getCommand("spawn");
                if (ours != null) getServer().getCommandMap().getKnownCommands().put("spawn", ours);
            });
        }
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
            PrefixCommand prefixCommand = new PrefixCommand(configManager, luckPermsHook);
            getCommand("prefix").setExecutor(prefixCommand);
            getCommand("prefix").setTabCompleter(prefixCommand);
        }

        // подхватываем игроков, которые уже онлайн (например после /reload плагинов)
        visibilityManager.updateAll();

        getLogger().info("DestroyLobby включен.");
    }

    @Override
    public void onDisable() {
        if (visibilityManager != null) visibilityManager.showEveryone();
        if (statsManager != null) {
            statsManager.saveAll();
        }
        LobbyApi.init(null);
        getLogger().info("DestroyLobby выключен.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroylobby")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                configManager.reload();
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

            if (args.length >= 2 && args[0].equalsIgnoreCase("portal")) {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                org.bukkit.Location l = player.getLocation();
                var cfg = getConfig();
                switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                    case "pos1", "pos2" -> {
                        String k = "lobby-portal." + args[1].toLowerCase(java.util.Locale.ROOT);
                        cfg.set("lobby-portal.world", l.getWorld().getName());
                        cfg.set(k + ".x", l.getBlockX());
                        cfg.set(k + ".y", l.getBlockY());
                        cfg.set(k + ".z", l.getBlockZ());
                        sender.sendMessage("§a[DestroyLobby] Угол портала " + args[1] + " поставлен: " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                    }
                    case "target" -> {
                        cfg.set("lobby-portal.target.world", l.getWorld().getName());
                        cfg.set("lobby-portal.target.x", l.getBlockX() + 0.5);
                        cfg.set("lobby-portal.target.y", l.getY());
                        cfg.set("lobby-portal.target.z", l.getBlockZ() + 0.5);
                        cfg.set("lobby-portal.target.yaw", Math.round(l.getYaw() / 90f) * 90f);
                        cfg.set("lobby-portal.target.pitch", 0f);
                        sender.sendMessage("§a[DestroyLobby] Точка, куда ведёт портал, поставлена здесь.");
                    }
                    case "on", "off" -> {
                        cfg.set("lobby-portal.enabled", args[1].equalsIgnoreCase("on"));
                        sender.sendMessage("§a[DestroyLobby] Портал " + (args[1].equalsIgnoreCase("on") ? "включён" : "выключен") + ".");
                    }
                    default -> sender.sendMessage("§7/destroylobby portal pos1|pos2|target|on|off");
                }
                saveConfig();
                return true;
            }

            sender.sendMessage("§7Использование: /destroylobby reload|setspawn|portal <pos1|pos2|target|on|off>");
            return true;
        }

        if (command.getName().equalsIgnoreCase("coins")) {
            return handleCoinsCommand(sender, args);
        }

        return false;
    }

    /**
     * copyDefaults не меняет уже записанные значения, поэтому новые паузу прыжка (1 секунда)
     * и меньшее количество синего огня переносим в старый config.yml один раз - по lobby-jump.settings-version.
     */
    private void migrateJumpSettings() {
        FileConfiguration cfg = getConfig();
        Object current = cfg.get("lobby-jump.settings-version", null); // без значений по умолчанию
        int latest = cfg.getDefaults() == null ? 1 : cfg.getDefaults().getInt("lobby-jump.settings-version", 1);
        if (current instanceof Number n && n.intValue() >= latest) return;
        for (String path : new String[]{"lobby-jump.cooldown-ticks", "lobby-jump.particles.burst-count",
                "lobby-jump.particles.ring-points", "lobby-jump.particles.trail-count",
                "lobby-jump.particles.landing-count", "lobby-jump.vertical.min", "lobby-jump.vertical.max",
                "lobby-jump.forward.min", "lobby-jump.forward.max"}) {
            cfg.set(path, cfg.getDefaults().get(path));
        }
        cfg.set("lobby-jump.settings-version", latest);
        getLogger().info("Прыжок в лобби: новые настройки силы прыжка.");
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
