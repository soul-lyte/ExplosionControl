package com.zsouul.explosioncontrol.command;

import com.zsouul.explosioncontrol.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Implements {@code /explosioncontrol reload}, the plugin's sole command, gated behind the
 * {@code explosioncontrol.reload} permission.
 */
public final class ExplosionControlCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public ExplosionControlCommand(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }

        sender.sendMessage(Component.text("Usage: /explosioncontrol reload", NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("explosioncontrol.reload")) {
            sender.sendMessage(Component.text("You do not have permission to do that.", NamedTextColor.RED));
            return true;
        }

        try {
            configManager.load();
            int categoryCount = configManager.getAllSettings().size();
            sender.sendMessage(Component.text()
                    .append(Component.text("ExplosionControl: ", NamedTextColor.GOLD))
                    .append(Component.text("config.yml reloaded (", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(categoryCount), NamedTextColor.YELLOW))
                    .append(Component.text(" explosion categories loaded).", NamedTextColor.GREEN))
                    .build());
        } catch (Exception ex) {
            sender.sendMessage(Component.text(
                    "ExplosionControl: failed to reload config.yml — check the console for details.",
                    NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Failed to reload ExplosionControl configuration", ex);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("explosioncontrol.reload")) {
            return List.of();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(sub -> sub.startsWith(partial))
                .collect(Collectors.toList());
    }
}
