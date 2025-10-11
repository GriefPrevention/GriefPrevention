package me.ryanhamshire.GriefPrevention.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.BlockFace;

import org.junit.jupiter.api.Test;

public class BoundingBoxTest
{

    @Test
    public void testVerify()
    {
        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 10, 10);
        BoundingBox boxB = new BoundingBox(10, 0, 10, 0, 10, 0);
        assertEquals(boxA, boxB);
    }

    @Test
    public void testMeasurements()
    {
        BoundingBox boxA = new BoundingBox(-1, 0, 1, 5, 4, 3);
        assertEquals(7, boxA.getLength());
        assertEquals(5, boxA.getHeight());
        assertEquals(3, boxA.getWidth());
        assertEquals(7 * 3, boxA.getArea());
        assertEquals(7 * 3 * 5, boxA.getVolume());
        assertEquals(2.5, boxA.getCenterX());
        assertEquals(2.5, boxA.getCenterY());
        assertEquals(2.5, boxA.getCenterZ());
    }

    @Test
    public void testCopy()
    {
        BoundingBox boxA = new BoundingBox(1, 2, 3, 4, 5, 6);
        BoundingBox boxB = new BoundingBox(7, 8, 9, 10, 11, 12);
        boxB.copy(boxA);
        assertEquals(boxA.getMinX(), boxB.getMinX());
        assertEquals(boxA.getMinY(), boxB.getMinY());
        assertEquals(boxA.getMinZ(), boxB.getMinZ());
        assertEquals(boxA.getMaxX(), boxB.getMaxX());
        assertEquals(boxA.getMaxY(), boxB.getMaxY());
        assertEquals(boxA.getMaxZ(), boxB.getMaxZ());
    }

    @Test
    public void testResize()
    {
        testBlockfaceFunction(BoundingBox::resize, new BoundingBox(0, 0, -10, 10, 10, 10));
    }

    private void testBlockfaceFunction(TriConsumer<BoundingBox, BlockFace, Integer> function, BoundingBox boxB)
    {
        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 10, 10);
        function.apply(boxA, BlockFace.NORTH, 10);
        assertEquals(boxB, boxA);

        for (BlockFace face : BlockFace.values())
        {
            if (face == BlockFace.SELF)
            {
                function.apply(boxA, face, 15);
                assertEquals(boxB, boxA);
                continue;
            }
            function.apply(boxA, face, 15);
            assertNotEquals(boxB, boxA);
            function.apply(boxA, face, -15);
            assertEquals(boxB, boxA);
        }
    }

    private interface TriConsumer<T, U, V>
    {

        void apply(T t, U u, V v);
    }

    @Test
    public void testMove()
    {
        testBlockfaceFunction(BoundingBox::move, new BoundingBox(0, 0, -10, 10, 10, 0));

        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 10, 10);
        BoundingBox boxB = boxA.clone();
        boxA.move(BlockFace.EAST, 15);
        assertNotEquals(boxB, boxA);
        BoundingBox boxC = boxA.clone();
        boxA.move(BlockFace.EAST, -15);
        assertEquals(boxB, boxA);
        boxC.move(BlockFace.EAST.getOppositeFace(), 15);
        assertEquals(boxB, boxC);
    }

    @Test
    public void testUnion()
    {
        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 10, 10);
        BoundingBox boxB = new BoundingBox(0, 0, 0, 10, 15, 20);
        BoundingBox boxC = new BoundingBox(-10, 0, 0, 10, 15, 20);
        boxA.union(0, 15, 20);

        assertEquals(boxB, boxA);
        boxA.union(-10, 7, 10);
        assertEquals(boxC, boxA);
    }

    @Test
    public void testIntersectCorner()
    {
        // One corner inside
        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 0, 10);
        BoundingBox boxB = new BoundingBox(5, 0, 5, 15, 0, 15);
        BoundingBox boxC = new BoundingBox(-5, 0, -5, 4, 0, 4);

        assertTrue(boxA.intersects(boxB));
        assertTrue(boxB.intersects(boxA));
        assertTrue(boxA.intersects(boxC));
        assertTrue(boxC.intersects(boxA));
        assertFalse(boxB.intersects(boxC));
        assertFalse(boxC.intersects(boxB));
    }

    @Test
    public void testIntersectCenter()
    {
        // Central intersection
        BoundingBox boxA = new BoundingBox(0, 0, 5, 10, 0, 15);
        BoundingBox boxB = new BoundingBox(5, 0, 0, 15, 0, 10);

        assertTrue(boxA.intersects(boxB));
        assertTrue(boxB.intersects(boxA));
    }

    @Test
    public void testIntersectLinearAdjacent()
    {
        // Linear North-South
        BoundingBox boxA = new BoundingBox(0, 0, 0, 10, 0, 10);
        BoundingBox boxB = new BoundingBox(0, 0, 11, 10, 0, 21);
        BoundingBox boxC = new BoundingBox(0, 0, 10, 10, 0, 20);

        // Adjacent
        assertFalse(boxA.intersects(boxB));
        assertFalse(boxB.intersects(boxA));
        // Overlapping on edge
        assertTrue(boxA.intersects(boxC));
        assertTrue(boxC.intersects(boxA));

        // Linear East-West
        boxA = new BoundingBox(0, 0, 0, 10, 0, 10);
        boxB = new BoundingBox(11, 0, 0, 21, 0, 10);
        boxC = new BoundingBox(10, 0, 0, 20, 0, 10);

        // Adjacent
        assertFalse(boxA.intersects(boxB));
        assertFalse(boxB.intersects(boxA));
        // Overlapping on edge
        assertTrue(boxA.intersects(boxC));
        assertTrue(boxC.intersects(boxA));
    }

    @Test
    void testContainment2d()
    {
        // Vertical
        BoundingBox boxA = new BoundingBox(0, 0, 0, 20, 10, 20);
        BoundingBox boxB = boxA.clone();
        boxB.move(BlockFace.UP, 20);

        assertFalse(boxA.contains(boxB));
        assertTrue(boxA.contains2d(boxB));
    }

    @Test
    public void testContainment()
    {
        // Complete containment
        BoundingBox boxA = new BoundingBox(0, 0, 0, 20, 0, 20);
        BoundingBox boxB = new BoundingBox(5, 0, 5, 15, 0, 15);
        BoundingBox boxC = new BoundingBox(-5, 0, -5, 4, 0, 4);
        BoundingBox boxD = boxA.clone();

        assertTrue(boxA.contains(boxB));
        assertTrue(boxB.intersects(boxA));
        assertFalse(boxB.contains(boxA));
        assertFalse(boxA.contains(boxC));
        assertTrue(boxA.contains(boxD));
        assertTrue(boxD.contains(boxA));
    }
}
