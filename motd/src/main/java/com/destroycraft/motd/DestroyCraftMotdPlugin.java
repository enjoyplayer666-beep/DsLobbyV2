package com.destroycraft.motd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class DestroyCraftMotdPlugin extends ru.dscraft.destroylobby.module.Module {

    private CachedServerIcon serverIcon;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        extractDefaultIcon();
        reloadServerIcon();

        Bukkit.getPluginManager().registerEvents(new MotdListener(this), this);

        getLogger().info("DestroyCraftMOTD включён. MOTD и иконка сервера настроены.");
    }

    /**
     * Копирует иконку по умолчанию (icon.png) из ресурсов плагина в его папку
     * данных при первом запуске, чтобы владельцу было что редактировать.
     */
    private void extractDefaultIcon() {
        File target = new File(getDataFolder(), "icon.png");
        if (target.exists()) {
            return;
        }
        try (InputStream in = getResource("icon.png")) {
            if (in == null) {
                return;
            }
            getDataFolder().mkdirs();
            Files.copy(in, target.toPath());
        } catch (IOException e) {
            getLogger().warning("Не удалось скопировать иконку по умолчанию: " + e.getMessage());
        }
    }

    /**
     * Загружает (или перезагружает) favicon, который будет показан в списке серверов.
     */
    public void reloadServerIcon() {
        this.serverIcon = null;

        if (!getConfig().getBoolean("icon.enabled", true)) {
            return;
        }

        String path = getConfig().getString("icon.icon-path", "icon.png");
        File iconFile = new File(getDataFolder(), path);

        // Фолбэк на server-icon.png в корне сервера, если своей иконки нет.
        if (!iconFile.exists()) {
            iconFile = new File("server-icon.png");
        }

        if (!iconFile.exists()) {
            getLogger().warning("Файл иконки не найден: " + path + " (и server-icon.png тоже нет).");
            return;
        }

        try {
            this.serverIcon = Bukkit.loadServerIcon(iconFile);
        } catch (Exception e) {
            getLogger().warning("Не удалось загрузить иконку сервера: " + e.getMessage());
        }
    }

    public CachedServerIcon getServerIcon() {
        return serverIcon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroymotd")) {
            reloadConfig();
            reloadServerIcon();
            sender.sendMessage("§d[DestroyCraftMOTD] §fКонфигурация перезагружена.");
            return true;
        }
        return false;
    }
}
