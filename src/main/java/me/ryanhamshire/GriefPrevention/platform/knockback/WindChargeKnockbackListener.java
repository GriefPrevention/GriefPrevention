package me.ryanhamshire.GriefPrevention.platform.knockback;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.platform.PlatformDetection;
import me.ryanhamshire.GriefPrevention.platform.PlatformListener;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-specific listener for handling wind charge knockback in claims.
 * <p>
 * Uses Paper-specific or Spigot event depending on the detected platform.
 * This prevents players from using wind charges to knock others around
 * in protected claims where PvP or entity interaction is restricted.
 */
public class WindChargeKnockbackListener implements PlatformListener
{

    private final DataStore dataStore;
    private final GriefPrevention plugin;

    public WindChargeKnockbackListener(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        this.plugin = plugin;
    }

    @Override
    public boolean isSupported()
    {
        // Example: Check if the required event class exists for the current platform.
        // Both Paper and Spigot support these events, but this pattern is useful
        // for listeners that depend on classes which may not exist on all servers.
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
            case PAPER -> new PaperWindChargeKnockbackHandler(dataStore, plugin);
            case SPIGOT -> new SpigotWindChargeKnockbackHandler(dataStore, plugin);
        };
    }

}