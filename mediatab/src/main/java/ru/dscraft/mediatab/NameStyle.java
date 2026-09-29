package ru.dscraft.mediatab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цвет ника задаётся хвостом префикса: коды после последнего видимого символа.
 * {@code /prefix set &6&l&oКОРОЛЬ &2&l&o} - префикс "КОРОЛЬ", ник - &amp;2&amp;l&amp;o.
 * Так же, как в чате DestroyChat.
 */
final class NameStyle {

    /** Хвост из одних цветовых кодов/тегов (с пробелами между ними) в конце строки. */
    private static final Pattern TRAILING_STYLE = Pattern.compile(
            "((?:\\s*(?:[&§]x(?:[&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]|<[^/<>][^<>]*>))+)\\s*$");

    /** Префикс без хвоста и стиль ника из хвоста (null - хвоста нет). */
    record Split(String prefix, String nickStyle) {
    }

    private NameStyle() {
    }

    static Split split(String raw) {
        if (raw == null || raw.isEmpty()) return new Split(raw, null);
        Matcher m = TRAILING_STYLE.matcher(raw);
        if (!m.find()) return new Split(raw, null);
        String prefix = raw.substring(0, m.start());
        // без видимого текста это не префикс с цветом ника, а просто цвет
        if (ColorUtil.plain(ColorUtil.rich(prefix)).isBlank()) return new Split(raw, null);
        // &r в хвосте - сброс после префикса, а не цвет ника
        String style = m.group(1).replaceAll("\\s+", "").replaceAll("[&§][rR]", "");
        if (style.isEmpty()) return new Split(raw, null);
        if (!prefix.endsWith(" ")) prefix = prefix + " ";
        return new Split(prefix, style);
    }
}
