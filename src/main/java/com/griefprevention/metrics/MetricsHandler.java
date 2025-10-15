package com.griefprevention.metrics;

import java.util.concurrent.Callable;

import me.ryanhamshire.GriefPrevention.GriefPrevention;

/**
 * Created on 3/2/2024.
 *
 * @author RoboMWM
 */
public class MetricsHandler
{

    private final Metrics metrics;

    public MetricsHandler(GriefPrevention plugin)
    {
        metrics = new Metrics(plugin, 3294);

        try
        {
            addSimplePie("bukkit_implementation", plugin.getServer().getVersion().split("-")[1]);
        }
        catch (Throwable ignored) { }
    }

    private void addSimplePie(String id, boolean value)
    {
        addSimplePie(id, Boolean.toString(value));
    }

    private void addSimplePie(String id, String value)
    {
        metrics.addCustomChart(new Metrics.SimplePie(id, new Callable<String>() {

            @Override
            public String call() throws Exception
            {
                return value;
            }
        }));
    }
}
