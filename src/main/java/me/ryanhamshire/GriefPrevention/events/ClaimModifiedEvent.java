package me.ryanhamshire.GriefPrevention.events;

import org.bukkit.Warning;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.Claim;

/**
 * @deprecated replaced by more descriptive {@link ClaimResizeEvent} and generic {@link ClaimChangeEvent}
 *
 * An {@link Event} for when a {@link Claim} is changed.
 *
 * <p>If cancelled, the resulting changes will not be made.
 *
 * @author Narimm on 5/08/2018.
 */
@Deprecated(forRemoval = true, since = "16.18")
@Warning(value = true, reason = "ClaimModifiedEvent will be removed in favor of ClaimResizeEvent")
public class ClaimModifiedEvent extends ClaimResizeEvent
{

    /**
     * Construct a new {@code ClaimModifiedEvent}.
     *
     * <p>Note that the actor causing modification may not be present if done by plugins.
     *
     * @param from the unmodified {@link Claim}
     * @param to the resulting {@code Claim}
     * @param modifier the {@link CommandSender} causing modification
     */
    public ClaimModifiedEvent(@NotNull Claim from, @NotNull Claim to, @Nullable CommandSender modifier)
    {
        super(from, to, modifier);
    }
}
