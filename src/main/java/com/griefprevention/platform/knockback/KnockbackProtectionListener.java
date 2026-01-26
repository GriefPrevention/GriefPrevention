package com.griefprevention.platform.knockback;

import com.griefprevention.platform.PlatformDetection;
import com.griefprevention.platform.PlatformListener;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-specific listener for handling player-caused knockback in claims.
 * <p>
 * Uses Paper-specific or Spigot event depending on the detected platform.
 * This prevents players from using melee attacks (spears), projectiles
 * (wind charges), or other mechanisms to knock entities around in protected
 * claims where such interaction is restricted.
 */
public class KnockbackProtectionListener implements PlatformListener
{

    private final DataStore dataStore;
    private final GriefPrevention plugin;

    public KnockbackProtectionListener(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        this.plugin = plugin;
    }

    @Override
    public boolean isSupported()
    {
        // Example check if the required event class exists for the current
        // platform. In this case, both Paper and Spigot support these events,
        // but this pattern is useful for listeners that depend on classes
        // which may not exist on all servers.
        return switch (PlatformDetection.getPlatform())
        {
            case PAPER -> PlatformDetection.classExists("com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent");
            case SPIGOT -> PlatformDetection.classExists("org.bukkit.event.entity.EntityKnockbackByEntityEvent");
        };
    }

    @Override
    public @NotNull Listener create()
    {
        return switch (PlatformDetection.getPlatform())
        {
            case PAPER -> new PaperKnockbackProtectionHandler(dataStore, plugin);
            case SPIGOT -> new SpigotKnockbackProtectionHandler(dataStore, plugin);
        };
    }

}