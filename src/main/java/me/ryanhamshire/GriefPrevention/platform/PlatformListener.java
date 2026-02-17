package me.ryanhamshire.GriefPrevention.platform;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for platform-specific event listeners.
 * <p>
 * Implementations provide support detection and factory logic for listeners
 * that behave differently on Paper vs Spigot.
 *
 * <p>To add a new platform-specific listener:
 * <ol>
 *   <li>Create listener implementations for each platform (e.g., PaperXyz, SpigotXyz)</li>
 *   <li>Create a class implementing this interface</li>
 *   <li>Call {@link #register(Plugin)} in {@link me.ryanhamshire.GriefPrevention.GriefPrevention#onEnable()}</li>
 * </ol>
 *
 * @see me.ryanhamshire.GriefPrevention.platform.knockback.WindChargeKnockbackListener
 */
public interface PlatformListener
{

    /**
     * Checks if this listener is supported on the current server.
     *
     * @return true if this listener can be used on the current platform
     */
    boolean isSupported();

    /**
     * Creates the platform-appropriate listener implementation.
     *
     * @return the listener, or null if not supported
     */
    @Nullable Listener create();

    /**
     * Registers this listener with the plugin if supported.
     *
     * @param plugin the plugin to register with
     */
    default void register(Plugin plugin)
    {
        if (isSupported())
        {
            Listener listener = create();
            if (listener != null)
            {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
        }
    }

}