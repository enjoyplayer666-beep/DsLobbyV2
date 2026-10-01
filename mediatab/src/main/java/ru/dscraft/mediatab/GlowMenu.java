package ru.dscraft.mediatab;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /glow - меню свечения: 16 цветов, свечение вокруг игрока цветом команды табло.
 * Legend - 5 цветов, Elite SP, команда проекта и опы - все (glow.access в config.yml).
 */
final class GlowMenu implements CommandExecutor, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Цвет свечения: ключ, название, цвет, слот в меню, запасной блок (если нет текстуры головы). */
    record Glow(String key, String name, NamedTextColor color, int slot, Material fallback) {
    }

    static final List<Glow> GLOWS = List.of(
            new Glow("yellow", "Жёлтый", NamedTextColor.YELLOW, 19, Material.YELLOW_CONCRETE),
            new Glow("blue", "Синий", NamedTextColor.BLUE, 20, Material.BLUE_CONCRETE),
            new Glow("gold", "Оранжевый", NamedTextColor.GOLD, 21, Material.ORANGE_CONCRETE),
            new Glow("gray", "Серый", NamedTextColor.GRAY, 22, Material.LIGHT_GRAY_CONCRETE),
            new Glow("green", "Лаймовый", NamedTextColor.GREEN, 23, Material.LIME_CONCRETE),
            new Glow("red", "Красный", NamedTextColor.RED, 24, Material.RED_CONCRETE),
            new Glow("pink", "Розовый", NamedTextColor.LIGHT_PURPLE, 25, Material.PINK_CONCRETE),
            new Glow("aqua", "Голубой", NamedTextColor.AQUA, 28, Material.LIGHT_BLUE_CONCRETE),
            new Glow("black", "Чёрный", NamedTextColor.BLACK, 29, Material.BLACK_CONCRETE),
            new Glow("dark_blue", "Тёмно-синий", NamedTextColor.DARK_BLUE, 30, Material.BLUE_CONCRETE),
            new Glow("dark_aqua", "Бирюзовый", NamedTextColor.DARK_AQUA, 31, Material.CYAN_CONCRETE),
            new Glow("dark_red", "Тёмно-красный", NamedTextColor.DARK_RED, 32, Material.RED_CONCRETE),
            new Glow("dark_gray", "Тёмно-серый", NamedTextColor.DARK_GRAY, 33, Material.GRAY_CONCRETE),
            new Glow("dark_green", "Зелёный", NamedTextColor.DARK_GREEN, 34, Material.GREEN_CONCRETE),
            new Glow("white", "Белый", NamedTextColor.WHITE, 39, Material.WHITE_CONCRETE),
            new Glow("purple", "Фиолетовый", NamedTextColor.DARK_PURPLE, 41, Material.PURPLE_CONCRETE));

    private static final int OFF_SLOT = 49;

    private final MediaTabPlugin plugin;
    private final Settings settings;
    private final NamespacedKey key;

    GlowMenu(MediaTabPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "glow");
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ---------------- доступ ----------------

    private boolean staff(Player p) {
        if (p.isOp()) return true;
        for (String g : settings.staffGroups()) {
            if (p.hasPermission("group." + g)) return true;
        }
        return false;
    }

    /** Какие цвета доступны: null - все, пусто - никаких. */
    private Set<String> allowed(Player p) {
        if (staff(p)) return null;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("glow.access");
        if (s == null) return Set.of();
        for (String group : s.getKeys(false)) {
            if (!p.hasPermission("group." + group.toLowerCase(Locale.ROOT))) continue;
            List<String> list = s.getStringList(group);
            if (list.isEmpty() && "all".equalsIgnoreCase(s.getString(group))) return null;
            if (list.stream().anyMatch(x -> x.equalsIgnoreCase("all"))) return null;
            return Set.copyOf(list.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList());
        }
        return Set.of();
    }

    private static boolean can(Set<String> allowed, Glow g) {
        return allowed == null || allowed.contains(g.key());
    }

    /** Текущее свечение игрока или null. */
    Glow current(Player p) {
        String k = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (k == null) return null;
        Set<String> allowed = allowed(p);
        for (Glow g : GLOWS) {
            if (g.key().equals(k)) return can(allowed, g) ? g : null; // привилегию сняли - свечения нет
        }
        return null;
    }

    // ---------------- меню ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        Set<String> allowed = allowed(p);
        if (allowed != null && allowed.isEmpty()) {
            p.sendMessage(MM.deserialize("<#C9C9FB>Нет такой команды :/</#C9C9FB>"));
            return true;
        }
        open(p, allowed);
        return true;
    }

    private void open(Player p, Set<String> allowed) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, MM.deserialize(plugin.getConfig().getString("glow.title", "<gray>| Свечение |</gray>")));
        ItemStack pane = named(new ItemStack(Material.PURPLE_STAINED_GLASS_PANE), Component.text(" "), List.of());
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) h.inv.setItem(i, pane);
        }
        for (Glow g : GLOWS) {
            boolean ok = can(allowed, g);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (ok) {
                lore.add(MM.deserialize("<white>▸</white> <#55FF55>Доступен</#55FF55>"));
                lore.add(MM.deserialize("  <white>Нажмите, чтобы включить</white>"));
            } else {
                lore.add(MM.deserialize("<white>▸</white> <#FF5555>Недоступен</#FF5555>"));
                lore.add(MM.deserialize("  <white>Доступно с привилегии</white> <#8C8CFF>Elite SP</#8C8CFF>"));
            }
            h.inv.setItem(g.slot(), named(icon(g), Component.text(g.name(), g.color()), lore));
        }
        h.inv.setItem(OFF_SLOT, named(new ItemStack(Material.BARRIER), MM.deserialize("<#FF5555>Выключить свечение</#FF5555>"), List.of(
                Component.empty(),
                MM.deserialize("<white>▸</white> <#FF5555>Нажмите, чтобы убрать</#FF5555>"),
                MM.deserialize("  <white>любое текущее свечение.</white>"))));
        p.openInventory(h.inv);
    }

    /** Голова цвета (текстура из glow.heads.<цвет>) или блок того же цвета. */
    private ItemStack icon(Glow g) {
        String texture = plugin.getConfig().getString("glow.heads." + g.key(), "");
        if (texture == null || texture.isBlank()) return new ItemStack(g.fallback());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            String hash = texture.trim();
            if (hash.startsWith("http")) hash = hash.substring(hash.lastIndexOf('/') + 1);
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("glow" + hash).getBytes()), "glow");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL("http://textures.minecraft.net/texture/" + hash));
            profile.setTextures(textures);
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
            return head;
        } catch (Exception e) {
            return new ItemStack(g.fallback());
        }
    }

    private static ItemStack named(ItemStack it, Component name, List<Component> lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<Component> l = new ArrayList<>();
        for (Component c : lore) l.add(c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getSlot();
        if (slot == OFF_SLOT) {
            p.getPersistentDataContainer().remove(key);
            p.closeInventory();
            p.sendMessage(MM.deserialize(plugin.getConfig().getString("glow.off-message", "<white>Свечение выключено.</white>")));
            plugin.refresh(p);
            return;
        }
        for (Glow g : GLOWS) {
            if (g.slot() != slot) continue;
            if (!can(allowed(p), g)) return;
            p.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.key());
            p.closeInventory();
            String name = "<" + g.color().asHexString() + ">" + g.name() + "</" + g.color().asHexString() + ">";
            p.sendMessage(MM.deserialize(plugin.getConfig().getString("glow.message",
                    "<white>Свечение успешно активировано! [</white><color><white>]</white>").replace("<color>", name)));
            plugin.refresh(p);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
