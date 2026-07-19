package com.zsouul.explosioncontrol.config;

import com.zsouul.explosioncontrol.model.ExplosionCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads {@code config.yml} into a validated, in-memory {@link ExplosionSettings} lookup for
 * every {@link ExplosionCategory}, and supports being reloaded on demand (see
 * {@code /explosioncontrol reload}) without restarting the server.
 * <p>
 * All validation/clamping happens once here, at load time, so the hot path (event listeners
 * firing on every explosion) only ever does a cheap {@link EnumMap} lookup with no further
 * parsing, string comparisons, or I/O — important for servers with frequent explosions
 * (TNT cannons, PvP, creeper farms, etc).
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<ExplosionCategory, ExplosionSettings> settings = new EnumMap<>(ExplosionCategory.class);

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * (Re)loads {@code config.yml} from disk and rebuilds every {@link ExplosionSettings}
     * entry. Safe to call repeatedly; previous values are discarded atomically per-category.
     */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection explosionsSection = config.getConfigurationSection("explosions");
        Map<ExplosionCategory, ExplosionSettings> loaded = new EnumMap<>(ExplosionCategory.class);

        for (ExplosionCategory category : ExplosionCategory.values()) {
            ConfigurationSection section =
                    explosionsSection != null ? explosionsSection.getConfigurationSection(category.key()) : null;
            loaded.put(category, readSettings(category, section));
        }

        this.settings.clear();
        this.settings.putAll(loaded);
    }

    /**
     * @param category the explosion source
     * @return the resolved settings for that category; never {@code null} — falls back to
     * {@link ExplosionSettings#vanillaDefault()} if {@link #load()} has not run yet or the
     * category was somehow absent.
     */
    public ExplosionSettings getSettings(ExplosionCategory category) {
        return settings.getOrDefault(category, ExplosionSettings.vanillaDefault());
    }

    /**
     * @return an unmodifiable snapshot of every currently-loaded category's settings, mainly
     * for diagnostics (e.g. the reload command reporting how many categories were loaded).
     */
    public Map<ExplosionCategory, ExplosionSettings> getAllSettings() {
        return Collections.unmodifiableMap(settings);
    }

    private ExplosionSettings readSettings(ExplosionCategory category, ConfigurationSection section) {
        if (section == null) {
            logger.warning(() -> "config.yml is missing section 'explosions." + category.key()
                    + "' — falling back to vanilla behaviour for this explosion type. "
                    + "Delete config.yml and let the plugin regenerate it to fix this.");
            return ExplosionSettings.vanillaDefault();
        }

        boolean enabled = section.getBoolean("enabled", true);

        double damageCap = clampRange(
                section.getDouble("damage-cap", ExplosionSettings.MAX_DAMAGE_CAP),
                ExplosionSettings.MIN_DAMAGE_CAP, ExplosionSettings.MAX_DAMAGE_CAP,
                category, "damage-cap");

        double radiusMultiplier = clampMinZero(
                section.getDouble("radius-multiplier", 1.0D), category, "radius-multiplier");

        double knockbackMultiplier = clampMinZero(
                section.getDouble("knockback-multiplier", 1.0D), category, "knockback-multiplier");

        boolean blockDamage = section.getBoolean("block-damage", true);

        return new ExplosionSettings(enabled, damageCap, radiusMultiplier, knockbackMultiplier, blockDamage);
    }

    private double clampRange(double value, double min, double max, ExplosionCategory category, String field) {
        if (value < min || value > max) {
            double clamped = Math.max(min, Math.min(max, value));
            logger.warning(String.format(Locale.ROOT,
                    "explosions.%s.%s = %.2f is outside the valid range [%.1f, %.1f]; clamped to %.2f.",
                    category.key(), field, value, min, max, clamped));
            return clamped;
        }
        return value;
    }

    private double clampMinZero(double value, ExplosionCategory category, String field) {
        if (value < 0.0D) {
            logger.warning(String.format(Locale.ROOT,
                    "explosions.%s.%s = %.2f cannot be negative; clamped to 0.0.",
                    category.key(), field, value));
            return 0.0D;
        }
        return value;
    }
}
