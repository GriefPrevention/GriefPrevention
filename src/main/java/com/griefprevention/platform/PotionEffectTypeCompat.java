package com.griefprevention.platform;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Compatibility layer for {@code PotionEffectTypeCategory} which was added in 1.21.1.
 * <p>
 * On older versions where {@code PotionEffectTypeCategory} doesn't exist, this returns
 * false for beneficial checks, treating effects as potentially harmful (the safer default).
 */
public final class PotionEffectTypeCompat
{
    private static final Object BENEFICIAL;
    private static final MethodHandle GET_CATEGORY;

    static
    {
        Object beneficial = null;
        MethodHandle getCategory = null;

        try
        {
            Class<?> categoryClass = Class.forName("org.bukkit.potion.PotionEffectTypeCategory");
            beneficial = categoryClass.getField("BENEFICIAL").get(null);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            getCategory = lookup.findVirtual(
                    PotionEffectType.class,
                    "getCategory",
                    MethodType.methodType(categoryClass));
        }
        catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | IllegalAccessException ignored)
        {
            // PotionEffectTypeCategory doesn't exist on this version
        }

        BENEFICIAL = beneficial;
        GET_CATEGORY = getCategory;
    }

    private PotionEffectTypeCompat() {}

    /**
     * Checks if the potion effect type is beneficial (positive effect).
     * <p>
     * Returns false on older versions where {@code PotionEffectTypeCategory} doesn't exist,
     * treating the effect as potentially harmful (the safer default for PvP protection).
     *
     * @param effectType the potion effect type to check
     * @return true if the effect is beneficial
     */
    public static boolean isBeneficial(@NotNull PotionEffectType effectType)
    {
        if (GET_CATEGORY == null || BENEFICIAL == null)
            return false;

        try
        {
            return BENEFICIAL.equals(GET_CATEGORY.invoke(effectType));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
}