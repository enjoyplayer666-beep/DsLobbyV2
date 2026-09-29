package ru.dscraft.mediatab;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Другие плагины через рефлексию, без зависимости при сборке:
 * коины, убийства и смерти - из DestroyLobby (ru.dscraft.destroylobby.api.LobbyApi),
 * %плейсхолдеры% - из PlaceholderAPI. Без этих плагинов просто 0 и строка как есть.
 */
final class Hooks {

    private static Method coins, kills, deaths, papi;
    private static ClassLoader lobbyLoader;
    private static boolean papiChecked;

    private Hooks() {
    }

    static String coins(Player p) {
        return call(() -> coins, p);
    }

    static String kills(Player p) {
        return call(() -> kills, p);
    }

    static String deaths(Player p) {
        return call(() -> deaths, p);
    }

    private interface Ref {
        Method get();
    }

    private static String call(Ref ref, Player p) {
        if (!resolveLobby()) return "0";
        try {
            return String.valueOf(ref.get().invoke(null, p));
        } catch (Exception e) {
            return "0";
        }
    }

    /** После перезагрузки DestroyLobby (новый загрузчик классов) ищет методы заново. */
    private static synchronized boolean resolveLobby() {
        Plugin lobby = Bukkit.getPluginManager().getPlugin("DestroyLobby");
        if (lobby == null || !lobby.isEnabled()) return false;
        ClassLoader cl = lobby.getClass().getClassLoader();
        if (cl == lobbyLoader && coins != null) return true;
        try {
            Class<?> api = Class.forName("ru.dscraft.destroylobby.api.LobbyApi", true, cl);
            coins = api.getMethod("coins", Player.class);
            kills = api.getMethod("kills", Player.class);
            deaths = api.getMethod("deaths", Player.class);
            lobbyLoader = cl;
            return true;
        } catch (Exception e) {
            coins = null;
            lobbyLoader = null;
            return false;
        }
    }

    /** %плейсхолдеры% PlaceholderAPI, если он установлен. */
    static synchronized String papi(Player p, String text) {
        if (text == null || text.indexOf('%') < 0) return text;
        if (!papiChecked) {
            papiChecked = true;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                            .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                } catch (Exception ignored) {
                }
            }
        }
        if (papi == null) return text;
        try {
            return (String) papi.invoke(null, p, text);
        } catch (Exception e) {
            return text;
        }
    }
}
