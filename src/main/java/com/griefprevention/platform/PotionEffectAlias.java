package com.griefprevention.platform;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for potion effect types that were renamed across Minecraft versions.
 * <p>
 * Several potion effect constants were renamed in 1.20.5. This enum provides version-safe
 * access to these effects by looking up stable minecraft registry keys.
 * <p>
 * Uses {@code Bukkit.getRegistry()} instead of {@code Registry.EFFECT} because the
 * latter was added in 1.20.3 and isn't available in earlier versions.
 *
 * @see EntityCompat for version-specific entity type checks
 */
public enum PotionEffectAlias
{
    INSTANT_DAMAGE("instant_damage"),
    JUMP_BOOST("jump_boost");
    // To support more effects, add constants with their minecraft registry keys

    private final @NotNull PotionEffectType effectType;

    PotionEffectAlias(@NotNull String... keys)
    {
        this.effectType = RegistryResolver.resolve(PotionEffectType.class, keys);
    }

    /**
     * Gets the potion effect type.
     *
     * @return the potion effect type
     */
    public @NotNull PotionEffectType type()
    {
        return effectType;
    }
}