package com.griefprevention.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformDetectionTest
{

    @Test
    void classExists_returnsTrue_forExistingClass()
    {
        assertTrue(PlatformDetection.classExists("java.lang.String"));
        assertTrue(PlatformDetection.classExists("java.util.List"));
        assertTrue(PlatformDetection.classExists("org.bukkit.Bukkit"));
    }

    @Test
    void classExists_returnsFalse_forNonExistentClass()
    {
        assertFalse(PlatformDetection.classExists("com.example.NonExistentClass"));
        assertFalse(PlatformDetection.classExists("org.bukkit.NotARealClass"));
        assertFalse(PlatformDetection.classExists(""));
    }

    @Test
    void getPlatform_returnsNonNull()
    {
        PlatformDetection.Platform platform = PlatformDetection.getPlatform();
        assertNotNull(platform);
    }

    @Test
    void getPlatform_returnsCachedValue()
    {
        // Call twice to verify caching (same instance should be returned)
        PlatformDetection.Platform first = PlatformDetection.getPlatform();
        PlatformDetection.Platform second = PlatformDetection.getPlatform();
        assertEquals(first, second);
    }

}