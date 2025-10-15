package me.ryanhamshire.GriefPrevention;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.io.Files;

//loads ignore data from file into a hash map
class IgnoreLoaderThread extends Thread
{

    private final UUID playerToLoad;

    private final ConcurrentHashMap<UUID, Boolean> destinationMap;

    IgnoreLoaderThread(UUID playerToLoad, ConcurrentHashMap<UUID, Boolean> destinationMap)
    {
        this.playerToLoad = playerToLoad;
        this.destinationMap = destinationMap;
        this.setPriority(MIN_PRIORITY);
    }

    @Override
    public void run()
    {
        File ignoreFile = new File(DataStore.playerDataFolderPath + File.separator + this.playerToLoad + ".ignore");

        //if the file doesn't exist, there's nothing to do here
        if (!ignoreFile.exists()) return;

        boolean needRetry = false;
        int retriesRemaining = 5;
        Exception latestException = null;
        do
        {
            try
            {
                needRetry = false;

                //read the file content and immediately close it
                List<String> lines = Files.readLines(ignoreFile, StandardCharsets.UTF_8);

                //each line is one ignore.  asterisks indicate administrative ignores
                for (String line : lines)
                {
                    boolean adminIgnore = false;
                    if (line.startsWith("*"))
                    {
                        adminIgnore = true;
                        line = line.substring(1);
                    }
                    try
                    {
                        UUID ignoredUUID = UUID.fromString(line);
                        this.destinationMap.put(ignoredUUID, adminIgnore);
                    }
                    catch (IllegalArgumentException e) { } //if a bad UUID, ignore the line
                }
            }

            //if there's any problem with the file's content, retry up to 5 times with 5 milliseconds between
            catch (Exception e)
            {
                latestException = e;
                needRetry = true;
                retriesRemaining--;
            }

            try
            {
                if (needRetry) Thread.sleep(5);
            }
            catch (InterruptedException exception) { }

        } while (needRetry && retriesRemaining >= 0);

        //if last attempt failed, log information about the problem
        if (needRetry)
        {
            GriefPrevention.AddLogEntry(
                    "Retry attempts exhausted.  Unable to load ignore data for player \"" + playerToLoad.toString()
                            + "\": " + latestException);
            latestException.printStackTrace();
        }
    }
}
