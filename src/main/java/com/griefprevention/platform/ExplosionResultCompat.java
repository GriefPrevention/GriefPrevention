package com.griefprevention.platform;

import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Compatibility layer for {@code ExplosionResult} which was added in newer API versions.
 * <p>
 * On older versions where {@code ExplosionResult} doesn't exist, this returns false
 * for all checks, causing explosions to be treated as destructive.
 */
public final class ExplosionResultCompat
{
    private static final Object TRIGGER_BLOCK;
    private static final MethodHandle ENTITY_GET_RESULT;
    private static final MethodHandle BLOCK_GET_RESULT;

    static
    {
        Object triggerBlock = null;
        MethodHandle entityGetResult = null;
        MethodHandle blockGetResult = null;

        try
        {
            Class<?> explosionResultClass = Class.forName("org.bukkit.ExplosionResult");
            triggerBlock = explosionResultClass.getField("TRIGGER_BLOCK").get(null);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            entityGetResult = lookup.findVirtual(
                    EntityExplodeEvent.class,
                    "getExplosionResult",
                    MethodType.methodType(explosionResultClass));
            blockGetResult = lookup.findVirtual(
                    BlockExplodeEvent.class,
                    "getExplosionResult",
                    MethodType.methodType(explosionResultClass));
        }
        catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | IllegalAccessException ignored)
        {
            // ExplosionResult doesn't exist on this version
        }

        TRIGGER_BLOCK = triggerBlock;
        ENTITY_GET_RESULT = entityGetResult;
        BLOCK_GET_RESULT = blockGetResult;
    }

    private ExplosionResultCompat() {}

    /**
     * Checks if the explosion result is {@code TRIGGER_BLOCK} (interaction only, no destruction).
     * <p>
     * Returns false on older versions where {@code ExplosionResult} doesn't exist.
     *
     * @param event the entity explode event
     * @return true if the explosion only triggers blocks without destroying them
     */
    public static boolean isTriggerBlock(@NotNull EntityExplodeEvent event)
    {
        if (ENTITY_GET_RESULT == null || TRIGGER_BLOCK == null)
            return false;

        try
        {
            return TRIGGER_BLOCK.equals(ENTITY_GET_RESULT.invoke(event));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /**
     * Checks if the explosion result is {@code TRIGGER_BLOCK} (interaction only, no destruction).
     * <p>
     * Returns false on older versions where {@code ExplosionResult} doesn't exist.
     *
     * @param event the block explode event
     * @return true if the explosion only triggers blocks without destroying them
     */
    public static boolean isTriggerBlock(@NotNull BlockExplodeEvent event)
    {
        if (BLOCK_GET_RESULT == null || TRIGGER_BLOCK == null)
            return false;

        try
        {
            return TRIGGER_BLOCK.equals(BLOCK_GET_RESULT.invoke(event));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
}