package com.griefprevention.api.claim;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for claim geometries.
 */
public final class ClaimGeometryRegistry
{

    private final Map<NamespacedKey, ClaimGeometry> geometries = new LinkedHashMap<>();
    private final ClaimGeometry defaultGeometry = new RectangularClaimGeometry();

    public ClaimGeometryRegistry()
    {
        register(defaultGeometry);
    }

    public void register(@NotNull ClaimGeometry geometry)
    {
        geometries.put(geometry.getKey(), geometry);
    }

    public void unregister(@NotNull NamespacedKey key)
    {
        if (defaultGeometry.getKey().equals(key))
        {
            geometries.put(key, defaultGeometry);
            return;
        }

        geometries.remove(key);
    }

    public @Nullable ClaimGeometry get(@NotNull NamespacedKey key)
    {
        return geometries.get(key);
    }

    public @NotNull ClaimGeometry getDefaultGeometry()
    {
        return defaultGeometry;
    }

    public @NotNull ClaimGeometry resolve(@Nullable NamespacedKey key)
    {
        if (key == null)
        {
            return defaultGeometry;
        }

        ClaimGeometry geometry = geometries.get(key);
        return geometry != null ? geometry : defaultGeometry;
    }

    public @NotNull List<ClaimGeometry> getGeometries()
    {
        return new ArrayList<>(geometries.values());
    }

}
