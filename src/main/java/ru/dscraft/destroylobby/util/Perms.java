package ru.dscraft.destroylobby.util;

/**
 * Права DestroyLobby. Права чата (/prefix chat, /color, цветные сообщения)
 * объявлены в плагине DestroyChat.
 */
public final class Perms {

    /** Жирные/курсивные префиксы и любые символы в /prefix set (Ultra+). */
    public static final String PREFIX_FORMAT = "destroylobby.prefix.format";
    /** /prefix reset ник - сбросить префикс другому игроку (команда проекта). */
    public static final String PREFIX_RESET_OTHERS = "destroylobby.prefix.reset.others";

    private Perms() {
    }
}
