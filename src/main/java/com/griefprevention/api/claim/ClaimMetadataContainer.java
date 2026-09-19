package com.griefprevention.api.claim;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Namespaced persistent metadata attached to a claim.
 *
 * <p>Values should be YAML-serializable so they can be persisted by built-in storage implementations.</p>
 */
public final class ClaimMetadataContainer
{

    private final Map<NamespacedKey, Object> values;

    public ClaimMetadataContainer()
    {
        this.values = new HashMap<>();
    }

    public ClaimMetadataContainer(@NotNull ClaimMetadataContainer other)
    {
        this.values = new HashMap<>(other.values);
    }

    public @Nullable Object get(@NotNull NamespacedKey key)
    {
        return values.get(key);
    }

    public @Nullable Object set(@NotNull NamespacedKey key, @Nullable Object value)
    {
        if (value == null)
        {
            return values.remove(key);
        }

        return values.put(key, value);
    }

    public @Nullable Object remove(@NotNull NamespacedKey key)
    {
        return values.remove(key);
    }

    public boolean contains(@NotNull NamespacedKey key)
    {
        return values.containsKey(key);
    }

    public @NotNull Map<NamespacedKey, Object> asMap()
    {
        return Collections.unmodifiableMap(values);
    }

    public void setAll(@NotNull ClaimMetadataContainer other)
    {
        values.clear();
        values.putAll(other.values);
    }

}
