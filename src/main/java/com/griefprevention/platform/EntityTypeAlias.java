package com.griefprevention.platform;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility layer for entity types that were renamed across Minecraft versions.
 * <p>
 * Several entity type constants were renamed in 1.20.5. This enum provides version-safe
 * access to these types by trying both old and new field names.
 *
 * @see EntityCompat for entity classes that may not exist on all versions
 */
public enum EntityTypeAlias
{
    ITEM("ITEM", "DROPPED_ITEM"),
    END_CRYSTAL("END_CRYSTAL", "ENDER_CRYSTAL"),
    FIREWORK_ROCKET("FIREWORK_ROCKET", "FIREWORK"),
    TNT("TNT", "PRIMED_TNT");
    // To support more types, add constants with their new and legacy field names

    private final @NotNull EntityType entityType;

    EntityTypeAlias(@NotNull String newName, @NotNull String legacyName)
    {
        EntityType resolved = FieldResolver.resolve(EntityType.class, newName, legacyName);
        if (resolved == null)
            throw new ExceptionInInitializerError("Failed to resolve EntityType: " + newName + " or " + legacyName);
        this.entityType = resolved;
    }

    /**
     * Gets the entity type.
     *
     * @return the entity type
     */
    public @NotNull EntityType type()
    {
        return entityType;
    }
}