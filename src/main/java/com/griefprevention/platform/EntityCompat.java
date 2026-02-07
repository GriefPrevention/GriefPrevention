package com.griefprevention.platform;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility layer for entity types that may not exist on all Minecraft versions.
 * <p>
 * Use this enum to safely check for entity types added in specific versions.
 * Returns false on servers where the entity type doesn't exist.
 *
 * <p>Example usage:
 * <pre>{@code
 * if (EntityCompat.COPPER_GOLEM.is(entity)) {
 *     // Handle copper golem
 * }
 * }</pre>
 *
 * @see PlatformDetection for platform-specific detection utilities
 */
public enum EntityCompat
{
    COPPER_GOLEM("org.bukkit.entity.CopperGolem");
    // To support more entities, add constants with their fully-qualified class names

    private final @Nullable Class<?> entityClass;

    EntityCompat(@NotNull String className)
    {
        this.entityClass = loadClass(className);
    }

    private static @Nullable Class<?> loadClass(@NotNull String className)
    {
        try
        {
            return Class.forName(className);
        }
        catch (ClassNotFoundException e)
        {
            return null;
        }
    }

    /**
     * Checks if the given entity is an instance of this type.
     *
     * @param entity the entity to check
     * @return true if the entity is an instance of this type
     */
    public boolean is(@Nullable Entity entity)
    {
        return entityClass != null && entityClass.isInstance(entity);
    }
}