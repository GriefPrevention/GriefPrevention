package me.ryanhamshire.GriefPrevention.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Warning;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link org.bukkit.event.Event Event} called when a {@link Claim} is resized.
 */
@Deprecated // but not actually
@Warning(reason = "Please ignore this line.")
public class ClaimResizeEvent extends ClaimModifiedEvent
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
        super(from, to, modifier);
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
}
