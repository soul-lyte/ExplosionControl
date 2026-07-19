package com.zsouul.explosioncontrol.model;

/**
 * Every explosion source ExplosionControl can configure independently.
 * <p>
 * The {@link #key} of each constant is the section name used in {@code config.yml}
 * under the {@code explosions:} root, e.g. {@code CHARGED_CREEPER.key()} is
 * {@code "charged-creeper"}.
 * <p>
 * {@link #OTHER} is a catch-all bucket for any explosion source that does not match
 * a more specific category (for example a future Mojang addition this plugin has not
 * been updated for yet), so no explosion in the game is ever left unconfigurable.
 */
public enum ExplosionCategory {

    TNT("tnt"),
    TNT_MINECART("tnt-minecart"),
    CREEPER("creeper"),
    CHARGED_CREEPER("charged-creeper"),
    WITHER_SPAWN("wither-spawn"),
    WITHER_SKULL("wither-skull"),
    GHAST_FIREBALL("ghast-fireball"),
    FIREBALL("fireball"),
    BLAZE_FIREBALL("blaze-fireball"),
    DRAGON_FIREBALL("dragon-fireball"),
    END_CRYSTAL("end-crystal"),
    RESPAWN_ANCHOR("respawn-anchor"),
    BED("bed"),
    WIND_CHARGE("wind-charge"),
    OTHER("other");

    private final String key;

    ExplosionCategory(String key) {
        this.key = key;
    }

    /**
     * @return the {@code config.yml} section name for this category.
     */
    public String key() {
        return key;
    }

    /**
     * Resolves a category from its {@code config.yml} section name.
     *
     * @param key the section name, e.g. {@code "charged-creeper"}
     * @return the matching category, or {@code null} if none matches
     */
    public static ExplosionCategory fromKey(String key) {
        for (ExplosionCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return null;
    }
}
