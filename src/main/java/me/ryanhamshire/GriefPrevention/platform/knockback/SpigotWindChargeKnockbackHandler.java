package me.ryanhamshire.GriefPrevention.platform.knockback;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Spigot/Bukkit implementation of wind charge knockback handling.
 * Uses {@link EntityKnockbackByEntityEvent} from the Bukkit API.
 * <p>
 * This handler checks for {@link AbstractWindCharge} directly as the source entity,
 * since Bukkit does not resolve projectiles to their shooter in this event.
 */
public class SpigotWindChargeKnockbackHandler extends WindChargeKnockbackHandler
{

    public SpigotWindChargeKnockbackHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        super(dataStore, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityKnockbackByEntity(@NotNull EntityKnockbackByEntityEvent event)
    {
        Entity sourceEntity = event.getSourceEntity();
        Entity knockedEntity = event.getEntity();

        if (!(sourceEntity instanceof AbstractWindCharge windCharge)) return;

        // Only handle player-caused wind charges.
        if (!(windCharge.getShooter() instanceof Player attacker)) return;

        if (knockedEntity instanceof Player defender)
        {
            handleWindChargeKnockbackPlayer(event, attacker, defender);
        }
        else
        {
            handleWindChargeKnockbackEntity(event, attacker, knockedEntity);
        }
    }

}