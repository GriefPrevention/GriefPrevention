package com.griefprevention;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClaimTest
{
    @Test
    public void testCopyKeepsCorners()
    {
        Claim claim = new Claim(new Location(null, 0, 0, 0), new Location(null, 9, 64, 19), null, List.of(), List.of(), List.of(), List.of(), 1L);
        Claim copy = new Claim(claim);
        assertEquals(claim.getLesserBoundaryCorner(), copy.getLesserBoundaryCorner());
        assertEquals(claim.getGreaterBoundaryCorner(), copy.getGreaterBoundaryCorner());
        assertEquals(claim.getArea(), copy.getArea());
    }
}
