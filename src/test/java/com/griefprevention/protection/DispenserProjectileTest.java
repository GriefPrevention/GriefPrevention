package com.griefprevention.protection;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.BlockEventHandler;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Projectiles fired by a dispenser inside a claim may affect that claim's own blocks.
 */
public class DispenserProjectileTest
{

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        // BlockEventHandler's static material list is built from tags during class initialization.
        doAnswer(invocation ->
        {
            Tag<?> tag = mock();
            doReturn(Set.of()).when(tag).getValues();
            return tag;
        }).when(server).getTag(notNull(), notNull(), notNull());
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        ServerMocks.unsetBukkitServer();
    }

    @AfterEach
    void clearPlugin()
    {
        GriefPrevention.instance = null;
    }

    @Test
    void dispenserInSameClaimMayBreakChorusFlower()
    {
        ProjectileHitEvent event = chorusFlowerHitByDispenser(true);

        new BlockEventHandler(GriefPrevention.instance.dataStore).chorusFlower(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void dispenserOutsideClaimMayNotBreakChorusFlower()
    {
        ProjectileHitEvent event = chorusFlowerHitByDispenser(false);

        new BlockEventHandler(GriefPrevention.instance.dataStore).chorusFlower(event);

        verify(event).setCancelled(true);
    }

    private static ProjectileHitEvent chorusFlowerHitByDispenser(boolean dispenserInClaim)
    {
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;

        World world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);

        Claim claim = mock(Claim.class);
        Location flowerLocation = mock(Location.class);
        Block flower = mock(Block.class);
        when(flower.getType()).thenReturn(Material.CHORUS_FLOWER);
        when(flower.getLocation()).thenReturn(flowerLocation);
        when(dataStore.getClaimAt(flowerLocation, false, null)).thenReturn(claim);

        Location dispenserLocation = mock(Location.class);
        Block dispenserBlock = mock(Block.class);
        when(dispenserBlock.getLocation()).thenReturn(dispenserLocation);
        org.bukkit.projectiles.BlockProjectileSource dispenser = mock(org.bukkit.projectiles.BlockProjectileSource.class);
        when(dispenser.getBlock()).thenReturn(dispenserBlock);
        when(dataStore.getClaimAt(dispenserLocation, false, claim)).thenReturn(dispenserInClaim ? claim : null);

        Arrow arrow = mock(Arrow.class);
        when(arrow.getWorld()).thenReturn(world);
        when(arrow.getShooter()).thenReturn(dispenser);

        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(arrow);
        when(event.getHitBlock()).thenReturn(flower);
        return event;
    }
}
