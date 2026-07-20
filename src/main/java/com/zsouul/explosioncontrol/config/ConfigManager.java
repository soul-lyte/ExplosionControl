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
    private volatile boolean debugEnabled;

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

        this.debugEnabled = config.getBoolean("settings.debug", false);

        ConfigurationSection explosionsSection = config.getConfigurationSection("explosions");
        if (explosionsSection == null) {
            logger.warning("config.yml has no 'explosions:' section at all — this usually means "
                    + "the file has a YAML syntax error and failed to parse. Every explosion "
                    + "category will use vanilla defaults until this is fixed. Check the lines "
                    + "above this warning for a YAML parsing error, or delete config.yml and "
                    + "let the plugin regenerate a clean one.");
        }
        Map<ExplosionCategory, ExplosionSettings> loaded = new EnumMap<>(ExplosionCategory.class);

        for (ExplosionCategory category : ExplosionCategory.values()) {
            ConfigurationSection section =
                    explosionsSection != null ? explosionsSection.getConfigurationSection(category.key()) : null;
            loaded.put(category, readSettings(category, section));
        }

        this.settings.clear();
        this.settings.putAll(loaded);

        if (debugEnabled) {
            logger.info(() -> "[debug] loaded settings: " + loaded);
        }
    }

    /**
     * @return whether {@code settings.debug} is enabled in {@code config.yml}; when true,
     * listeners log the category/values they resolve for each explosion to help diagnose
     * configuration or resolution issues.
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
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

        double damageMultiplier = clampMinZero(
                section.getDouble("damage-multiplier", 1.0D), category, "damage-multiplier");

        double radiusMultiplier = clampMinZero(
                section.getDouble("radius-multiplier", 1.0D), category, "radius-multiplier");

        double knockbackMultiplier = clampMinZero(
                section.getDouble("knockback-multiplier", 1.0D), category, "knockback-multiplier");

        boolean blockDamage = section.getBoolean("block-damage", true);

        return new ExplosionSettings(enabled, damageMultiplier, radiusMultiplier, knockbackMultiplier, blockDamage);
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
