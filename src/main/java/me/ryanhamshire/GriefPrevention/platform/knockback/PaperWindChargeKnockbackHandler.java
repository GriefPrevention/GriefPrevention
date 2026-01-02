package me.ryanhamshire.GriefPrevention.platform.knockback;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

/**
 * Paper implementation of wind charge knockback handling.
 * Uses Paper's {@link EntityKnockbackByEntityEvent}.
 * <p>
 * Paper resolves projectiles to their shooter, so this handler cannot check for
 * {@code AbstractWindCharge} directly. Instead, it handles EXPLOSION knockback
 * caused by players, which covers wind charges and other player-caused explosions.
 * <p>
 * This event is preferred over Bukkit's version on Paper servers because it fires
 * first and is not deprecated on Paper.
 */
public class PaperWindChargeKnockbackHandler extends WindChargeKnockbackHandler
{

    public PaperWindChargeKnockbackHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        super(dataStore, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event)
    {
        if (event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION) return;
        if (!(event.getHitBy() instanceof Player attacker)) return;

        if (event.getEntity() instanceof Player defender)
        {
            handleWindChargeKnockbackPlayer(event, attacker, defender);
        }
        else
        {
            handleWindChargeKnockbackEntity(event, attacker, event.getEntity());
        }
    }

}