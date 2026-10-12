package com.griefprevention.api.claim;

import me.ryanhamshire.GriefPrevention.Claim;
import org.jetbrains.annotations.NotNull;

/**
 * Customizes a claim before create-time validation and persistence.
 *
 * <p>This allows addons to assign geometry or metadata before parent-bound and overlap checks run.</p>
 */
@FunctionalInterface
public interface ClaimCreationCustomizer
{

    /**
     * Customize the claim before validation.
     *
     * @param claim the mutable claim being created
     */
    void customize(@NotNull Claim claim);

}
