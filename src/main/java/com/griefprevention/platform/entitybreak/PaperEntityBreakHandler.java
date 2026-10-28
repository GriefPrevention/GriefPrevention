package com.griefprevention.platform.entitybreak;

import io.papermc.paper.event.entity.EntityBreakByEntityEvent;
import io.papermc.paper.event.entity.EntityBreakEvent;
import me.ryanhamshire.GriefPrevention.EntityEventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Paper implementation of cushion break protection using {@link EntityBreakEvent}.
 * <p>
 * Paper fires this event for every block-attached entity, but hangings already go through
 * {@link HangingBreakEvent}, so only cushions are handled here.
 */
public class PaperEntityBreakHandler implements Listener
{

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityBreak(@NotNull EntityBreakEvent event)
    {
        if (event.getEntityType() != EntityType.CUSHION) return;

        Entity remover = null;
        if (event instanceof EntityBreakByEntityEvent byEntity)
        {
            remover = byEntity.getRemover();
        }
        HangingBreakEvent.RemoveCause cause = switch (event.getCause())
        {
            case ENTITY -> HangingBreakEvent.RemoveCause.ENTITY;
            case EXPLOSION -> HangingBreakEvent.RemoveCause.EXPLOSION;
            case OBSTRUCTION -> HangingBreakEvent.RemoveCause.OBSTRUCTION;
            case PHYSICS -> HangingBreakEvent.RemoveCause.PHYSICS;
            default -> HangingBreakEvent.RemoveCause.DEFAULT;
        };
        EntityEventHandler.protectBreak(event, event.getEntity(), cause, remover);
    }

}
