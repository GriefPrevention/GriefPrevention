package com.griefprevention.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Utility for resolving static fields by name with fallback support.
 * <p>
 * Used by alias enums to handle renamed constants across Minecraft versions.
 */
final class FieldResolver
{
    private FieldResolver() {}

    /**
     * Resolves a static field value by trying the new name first, then falling back to the legacy name.
     *
     * @param clazz the class containing the static field
     * @param newName the new field name (1.20.5+)
     * @param legacyName the legacy field name (pre-1.20.5)
     * @param <T> the field type
     * @return the resolved value, or null if neither name exists
     */
    static <T> @Nullable T resolve(@NotNull Class<T> clazz, @NotNull String newName, @NotNull String legacyName)
    {
        // Try new name first (1.20.5+)
        T value = getByFieldName(clazz, newName);
        if (value != null) return value;

        // Fall back to legacy name
        return getByFieldName(clazz, legacyName);
    }

    private static <T> @Nullable T getByFieldName(@NotNull Class<T> clazz, @NotNull String fieldName)
    {
        try
        {
            Field field = clazz.getDeclaredField(fieldName);
            return clazz.cast(field.get(null));
        }
        catch (NoSuchFieldException | IllegalAccessException e)
        {
            return null;
        }
    }
}