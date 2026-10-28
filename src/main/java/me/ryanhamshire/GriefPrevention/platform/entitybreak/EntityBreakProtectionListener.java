package me.ryanhamshire.GriefPrevention.platform.entitybreak;

import me.ryanhamshire.GriefPrevention.platform.PlatformDetection;
import me.ryanhamshire.GriefPrevention.platform.PlatformListener;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-specific listener protecting cushions from breakage.
 * <p>
 * Paper models cushions as plain entities rather than {@link org.bukkit.entity.Hanging} and fires
 * its own break event for them instead of {@link org.bukkit.event.hanging.HangingBreakEvent}.
 * Spigot keeps cushions as hangings, so the existing hanging handler covers them there and this
 * listener stays unregistered.
 */
public class EntityBreakProtectionListener implements PlatformListener
{

    @Override
    public boolean isSupported()
    {
        return PlatformDetection.getPlatform() == PlatformDetection.Platform.PAPER
                && PlatformDetection.classExists("io.papermc.paper.event.entity.EntityBreakEvent");
    }

    @Override
    public @NotNull Listener create()
    {
        return new PaperEntityBreakHandler();
    }

}
