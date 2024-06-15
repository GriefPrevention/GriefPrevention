package me.ryanhamshire.GriefPrevention;

import org.khelekore.prtree.MBRConverter;

/**
 * A converter locating {@link Claim} minimum and maximum coordinates.
 */
class ClaimToMinRegion implements MBRConverter<Claim>
{
    @Override
    public int getDimensions()
    {
        return 2;
    }

    @Override
    public double getMin(int axis, Claim claim)
    {
        return axis == 0 ? claim.getLesserBoundaryCorner().getBlockX() : claim.getLesserBoundaryCorner().getBlockZ();
    }

    @Override
    public double getMax(int axis, Claim claim)
    {
        return axis == 0 ? claim.getGreaterBoundaryCorner().getBlockX() : claim.getGreaterBoundaryCorner().getBlockZ();
    }

    /**
     * Branch factor for a Priority R-Tree. Formula selected based on
     * <a href="https://gist.github.com/Jikoo/276b092f597b2818ef20fe45476e4794#file-data-txt">benchmarking results</a>.
     *
     * @param claims the number of claims loaded
     * @return the branch factor for a new Priority R-Tree
     */
    static int getBranchFactor(int claims)
    {
        return 30 + claims / 1000;
    }

}
