package com.griefprevention.platform;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for potion effect types that were renamed across Minecraft versions.
 * <p>
 * Several potion effect constants were renamed in 1.20.5. This enum provides version-safe
 * access to these effects by trying both old and new field names.
 *
 * @see EntityCompat for version-specific entity type checks
 */
public enum PotionEffectAlias
{
    INSTANT_DAMAGE("INSTANT_DAMAGE", "HARM"),
    JUMP_BOOST("JUMP_BOOST", "JUMP");
    // To support more effects, add constants with their new and legacy field names

    private final @NotNull PotionEffectType effectType;

    PotionEffectAlias(@NotNull String newName, @NotNull String legacyName)
    {
        PotionEffectType resolved = FieldResolver.resolve(PotionEffectType.class, newName, legacyName);
        if (resolved == null)
            throw new ExceptionInInitializerError("Failed to resolve PotionEffectType: " + newName + " or " + legacyName);
        this.effectType = resolved;
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