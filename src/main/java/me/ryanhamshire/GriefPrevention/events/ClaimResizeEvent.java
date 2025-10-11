package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.command.CommandSender;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Claim} is resized.
 */
public class ClaimResizeEvent extends ClaimChangeEvent
{

    private final @Nullable CommandSender modifier;

    /**
     * Construct a new {@code ClaimResizeEvent}.
     *
     * <p>The actor causing modification may not be present if done by plugins.
     *
     * @param from the unmodified {@link Claim}
     * @param to the resulting {@code Claim}
     * @param modifier the {@link CommandSender} causing modification
     */
    public ClaimResizeEvent(@NotNull Claim from, @NotNull Claim to, @Nullable CommandSender modifier)
    {
        super(from, to);
        this.modifier = modifier;
    }

    /**
     * Get the {@link CommandSender} modifying the {@link Claim}. May be {@code null} if caused by a plugin.
     *
     * @return the actor causing creation
     */
    public @Nullable CommandSender getModifier()
    {
        return modifier;
    }

    /**
     * Get the resulting {@link Claim} after modification.
     *
     * @return the resulting {@code Claim}
     * @deprecated Use {@link #getTo()} instead.
     */
    @Deprecated(forRemoval = true, since = "16.18")
    public @NotNull Claim getClaim()
    {
        return getTo();
    }
}
