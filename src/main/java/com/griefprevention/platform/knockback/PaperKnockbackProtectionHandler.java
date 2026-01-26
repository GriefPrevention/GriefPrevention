package com.griefprevention.platform.knockback;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

/**
 * Paper implementation of knockback protection handling.
 * Uses Paper's {@link EntityKnockbackByEntityEvent} and {@link EntityPushedByEntityAttackEvent}.
 * <p>
 * Handles all player-caused knockback including melee attacks (spears),
 * projectiles (wind charges), mace smash AoE, and other mechanisms (shield blocks).
 * <p>
 * Paper resolves projectiles to their shooter, so {@code getHitBy()} returns
 * the player directly for both direct attacks and projectile-caused knockback.
 * <p>
 * This event is preferred over Bukkit's version on Paper servers because it fires
 * first and is not deprecated on Paper.
 * <p>
 * <b>Known limitation:</b> Wind Burst enchantment knockback cannot be blocked due to
 * a Paper bug where cancelling {@link EntityPushedByEntityAttackEvent} does not prevent
 * the knockback. See <a href="https://github.com/PaperMC/Paper/issues/13079">Paper #13079</a>.
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

    /**
     * Handle push events from AoE attacks like mace smash.
     * This is Paper-specific and handles knockback that doesn't go through
     * the normal {@link EntityKnockbackByEntityEvent}.
     * <p>
     * Note: Wind Burst enchantment knockback bypasses this due to Paper bug #13079.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityPushedByEntityAttack(@NotNull EntityPushedByEntityAttackEvent event)
    {
        Entity pusher = event.getPushedBy();

        Player attacker;
        if (pusher instanceof Player player)
        {
            attacker = player;
        }
        else if (pusher instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
        {
            attacker = shooter;
        }
        else
        {
            return;
        }

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