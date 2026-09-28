package ru.dscraft.destroylobby.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.dscraft.destroylobby.config.ConfigManager;
import ru.dscraft.destroylobby.hook.LuckPermsHook;
import ru.dscraft.destroylobby.tab.TabManager;
import ru.dscraft.destroylobby.util.ColorUtil;
import ru.dscraft.destroylobby.util.Perms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * /prefix set &lt;текст&gt;   - личный префикс: таб, над головой и чат (Deluxe+).
 * /prefix reset          - вернуть префикс привилегии.
 * /prefix chat ...       - перенаправляется в /chatprefix плагина DestroyChat (Ultra+).
 * <p>
 * Ник игрока префикс не меняет. Цвета: &-коды, &#RRGGBB, &lt;gradient:#A:#B&gt;текст&lt;/gradient&gt;.
 * Жирный/курсив и спецсимволы/смайлы - только с правом {@link Perms#PREFIX_FORMAT} (Ultra+).
 */
public class PrefixCommand implements CommandExecutor, TabCompleter {

    /** Без права PREFIX_FORMAT в видимом тексте разрешены только буквы, цифры и простая пунктуация. */
    private static final Pattern SIMPLE_TEXT = Pattern.compile("[\\p{L}\\p{N} _.,!?'\"()\\[\\]|:+\\-*#/]+");

    private final ConfigManager configManager;
    private final LuckPermsHook luckPermsHook;
    private final TabManager tabManager;

    public PrefixCommand(ConfigManager configManager, LuckPermsHook luckPermsHook, TabManager tabManager) {
        this.configManager = configManager;
        this.luckPermsHook = luckPermsHook;
        this.tabManager = tabManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
            return true;
        }
        if (!luckPermsHook.isEnabled()) {
            player.sendMessage(ColorUtil.parse("<red>LuckPerms недоступен, префикс сейчас поставить нельзя.</red>"));
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(player, args);
            case "reset", "clear" -> handleReset(player);
            case "chat" -> handleChat(player, args);
            default -> usage(player);
        }
        return true;
    }

    // ---- /prefix set ----

    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission(configManager.customPrefixPermission())) {
            deny(player, "Личный префикс доступен с привилегии Deluxe.");
            return;
        }
        if (args.length < 2) {
            usage(player);
            return;
        }
        String raw = joinFrom(args, 1);
        String error = validate(player, raw, configManager.customPrefixMaxLength());
        if (error != null) {
            deny(player, error);
            return;
        }
        if (!raw.endsWith(" ")) raw = raw + " ";

        if (!luckPermsHook.setCustomPrefix(player, raw)) {
            deny(player, "Не удалось применить префикс, попробуй ещё раз.");
            return;
        }
        tabManager.updatePlayerTeam(player);
        player.sendMessage(Component.text("Готово, теперь ты выглядишь так: ", NamedTextColor.GRAY)
                .append(ColorUtil.rich(raw))
                .append(Component.text(player.getName(), NamedTextColor.GRAY)));
    }

    private void handleReset(Player player) {
        boolean removed = luckPermsHook.clearCustomPrefix(player);
        tabManager.updatePlayerTeam(player);
        if (removed) {
            player.sendMessage(ColorUtil.parse("<green>Личный префикс сброшен, вернулся префикс привилегии.</green>"));
        } else {
            player.sendMessage(ColorUtil.parse("<gray>У тебя и так не было личного префикса.</gray>"));
        }
    }

    // ---- /prefix chat -> DestroyChat ----

    /**
     * Чат-префикс живёт в отдельном плагине DestroyChat. Чтобы игроки писали команду как в меню
     * привилегий (/prefix chat ...), перенаправляем её в /chatprefix. Права проверяет DestroyChat.
     */
    private void handleChat(Player player, String[] args) {
        if (!Bukkit.getPluginManager().isPluginEnabled("DestroyChat")) {
            deny(player, "Чат-плагин DestroyChat не установлен на сервере.");
            return;
        }
        String rest = args.length > 1 ? " " + joinFrom(args, 1) : "";
        player.performCommand("chatprefix" + rest);
    }

    // ---- проверки ----

    /** @return текст ошибки или null, если префикс подходит */
    private String validate(Player player, String raw, int maxVisible) {
        if (raw.isBlank()) return "Пустой префикс.";
        if (raw.contains("\n") || raw.contains("\r") || raw.contains("\t")) return "Префикс должен быть в одну строку.";

        Component component = ColorUtil.rich(raw);
        String visible = ColorUtil.plain(component).trim();
        if (visible.isEmpty()) return "В префиксе нет текста, только цвета.";
        if (visible.codePointCount(0, visible.length()) > maxVisible) {
            return "Слишком длинный префикс (максимум " + maxVisible + " символов без учёта цветов).";
        }

        if (!player.hasPermission(Perms.PREFIX_FORMAT)) {
            if (ColorUtil.hasDecorations(component)) {
                return "Жирный, курсив и другие стили доступны с привилегии Ultra.";
            }
            if (!SIMPLE_TEXT.matcher(visible).matches()) {
                return "Смайлы и спецсимволы в префиксе доступны с привилегии Ultra.";
            }
        }
        return null;
    }

    private void usage(Player player) {
        player.sendMessage(ColorUtil.parse("<gray>Префиксы:</gray>"));
        player.sendMessage(ColorUtil.parse("<gray> <white>/prefix set \\<текст></white> - префикс в табе, над головой и в чате</gray>"));
        player.sendMessage(ColorUtil.parse("<gray> <white>/prefix reset</white> - вернуть префикс привилегии</gray>"));
        player.sendMessage(ColorUtil.parse("<gray> <white>/prefix chat \\<текст></white> - отдельный префикс только для чата</gray>"));
        player.sendMessage(ColorUtil.parse("<gray> <white>/prefix chat reset</white> - убрать чат-префикс</gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Цвета: <white>&c &a &#FF55FF</white>, градиент: <white>\\<gradient:#FF5555:#FFFF55>текст\\</gradient></white></gray>"));
    }

    private void deny(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    private static String joinFrom(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("set", "reset", "chat")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chat")) {
            if ("reset".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("reset");
        }
        return out;
    }
}
