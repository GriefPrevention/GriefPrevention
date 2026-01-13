package me.ryanhamshire.GriefPrevention.platform.knockback;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

/**
 * Paper implementation of knockback protection handling.
 * Uses Paper's {@link EntityKnockbackByEntityEvent}.
 * <p>
 * Handles all player-caused knockback including melee attacks (spears, swords),
 * projectiles (wind charges), and other mechanisms (shield blocks).
 * <p>
 * Paper resolves projectiles to their shooter, so {@code getHitBy()} returns
 * the player directly for both direct attacks and projectile-caused knockback.
 * <p>
 * This event is preferred over Bukkit's version on Paper servers because it fires
 * first and is not deprecated on Paper.
 */
public class PaperKnockbackProtectionHandler extends KnockbackProtectionHandler
{

    public PaperKnockbackProtectionHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        super(dataStore, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event)
    {
        if (!(event.getHitBy() instanceof Player attacker)) return;

        if (event.getEntity() instanceof Player defender)
        {
            handleKnockbackPlayer(event, attacker, defender);
        }
        else
        {
            handleKnockbackEntity(event, attacker, event.getEntity());
        }
    }

}