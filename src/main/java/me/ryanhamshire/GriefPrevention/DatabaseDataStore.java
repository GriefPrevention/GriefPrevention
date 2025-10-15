/*
	GriefPrevention Server Plugin for Minecraft
	Copyright (C) 2012 Ryan Hamshire

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

//manages data stored in the file system
public class DatabaseDataStore extends DataStore
{

    private static final String SQL_UPDATE_NAME = "UPDATE griefprevention_playerdata SET name = ? WHERE name = ?";

    private static final String SQL_INSERT_CLAIM = "INSERT INTO griefprevention_claimdata (id, owner, lessercorner, greatercorner, builders, containers, accessors, managers, inheritnothing, parentid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_DELETE_CLAIM = "DELETE FROM griefprevention_claimdata WHERE id = ?";

    private static final String SQL_SELECT_PLAYER_DATA = "SELECT * FROM griefprevention_playerdata WHERE name = ?";

    private static final String SQL_DELETE_PLAYER_DATA = "DELETE FROM griefprevention_playerdata WHERE name = ?";

    private static final String SQL_INSERT_PLAYER_DATA = "INSERT INTO griefprevention_playerdata (name, lastlogin, accruedblocks, bonusblocks) VALUES (?, ?, ?, ?)";

    private static final String SQL_SET_NEXT_CLAIM_ID = "INSERT INTO griefprevention_nextclaimid VALUES (?)";

    private static final String SQL_DELETE_GROUP_DATA = "DELETE FROM griefprevention_playerdata WHERE name = ?";

    private static final String SQL_INSERT_SCHEMA_VERSION = "INSERT INTO griefprevention_schemaversion VALUES (?)";

    private static final String SQL_DELETE_NEXT_CLAIM_ID = "DELETE FROM griefprevention_nextclaimid";

    private static final String SQL_DELETE_SCHEMA_VERSION = "DELETE FROM griefprevention_schemaversion";

    private static final String SQL_SELECT_SCHEMA_VERSION = "SELECT * FROM griefprevention_schemaversion";

    private Connection databaseConnection = null;

    private final String databaseUrl;

    private final String userName;

    private final String password;

    DatabaseDataStore(String url, String userName, String password) throws Exception
    {
        this.databaseUrl = url;
        this.userName = userName;
        this.password = password;

        this.initialize();
    }

    @Override
    void initialize() throws Exception
    {
        try
        {
            this.refreshDataConnection();
        }
        catch (Exception e2)
        {
            GriefPrevention.AddLogEntry("ERROR: Unable to connect to database.  Check your config file settings.");
            throw e2;
        }

        try (Statement statement = databaseConnection.createStatement())
        {
            //ensure the data tables exist
            statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_nextclaimid (nextid INTEGER)");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS griefprevention_claimdata (id INTEGER, owner VARCHAR(50), lessercorner VARCHAR(100), greatercorner VARCHAR(100), builders TEXT, containers TEXT, accessors TEXT, managers TEXT, inheritnothing BOOLEAN, parentid INTEGER)");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS griefprevention_playerdata (name VARCHAR(50), lastlogin DATETIME, accruedblocks INTEGER, bonusblocks INTEGER)");
            statement.execute("CREATE TABLE IF NOT EXISTS griefprevention_schemaversion (version INTEGER)");

            // By making this run only for MySQL, we technically support SQLite too, as this is the only invalid
            // SQL we use that SQLite does not support. Seeing as its only use is to update VERY old, existing, MySQL
            // databases, this is of no concern.
            if (databaseUrl.startsWith("jdbc:mysql://"))
            {
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY builders TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY containers TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY accessors TEXT");
                statement.execute("ALTER TABLE griefprevention_claimdata MODIFY managers TEXT");
            }

            //if the next claim id table is empty, this is a brand new database which will write using the latest schema
            //otherwise, schema version is determined by schemaversion table (or =0 if table is empty, see getSchemaVersion())
            ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_nextclaimid");
            if (!results.next())
            {
                this.setSchemaVersion(latestSchemaVersion);
            }
        }
        catch (Exception e3)
        {
            GriefPrevention.AddLogEntry("ERROR: Unable to create the necessary database table.  Details:");
            GriefPrevention.AddLogEntry(e3.getMessage());
            e3.printStackTrace();
            throw e3;
        }

        //load group data into memory
        Statement statement = databaseConnection.createStatement();
        ResultSet results = statement.executeQuery("SELECT * FROM griefprevention_playerdata");

        while (results.next())
        {
            String name = results.getString("name");

            //ignore non-groups.  all group names start with a dollar sign.
            if (!name.startsWith("$")) continue;

            String groupName = name.substring(1);
            if (groupName == null || groupName.isEmpty()) continue; //defensive coding, avoid unlikely cases

            int groupBonusBlocks = results.getInt("bonusblocks");

            this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
        }

        //load next claim number into memory
        results = statement.executeQuery("SELECT * FROM griefprevention_nextclaimid");

        //if there's nothing yet, add it
        if (!results.next())
        {
            statement.execute("INSERT INTO griefprevention_nextclaimid VALUES (0)");
            this.nextClaimID = (long) 0;
        }

        //otherwise load it
        else
        {
            this.nextClaimID = results.getLong("nextid");
        }

        if (this.getSchemaVersion() == 0)
        {
            try
            {
                this.refreshDataConnection();

                //pull ALL player data from the database
                statement = this.databaseConnection.createStatement();
                results = statement.executeQuery("SELECT * FROM griefprevention_playerdata");

                //make a list of changes to be made
                HashMap<String, UUID> changes = new HashMap<>();

                ArrayList<String> namesToConvert = new ArrayList<>();
                while (results.next())
                {
                    //get the id
                    String playerName = results.getString("name");

                    //add to list of names to convert to UUID
                    namesToConvert.add(playerName);
                }

                //resolve and cache as many as possible through various means
                try
                {
                    UUIDFetcher fetcher = new UUIDFetcher(namesToConvert);
                    fetcher.call();
                }
                catch (Exception e)
                {
                    GriefPrevention
                            .AddLogEntry("Failed to resolve a batch of names to UUIDs.  Details:" + e.getMessage());
                    e.printStackTrace();
                }

                //reset results cursor
                results.beforeFirst();

                //for each result
                while (results.next())
                {
                    //get the id
                    String playerName = results.getString("name");

                    //try to convert player name to UUID
                    try
                    {
                        UUID playerID = UUIDFetcher.getUUIDOf(playerName);

                        //if successful, update the playerdata row by replacing the player's name with the player's UUID
                        if (playerID != null)
                        {
                            changes.put(playerName, playerID);
                        }
                    }
                    //otherwise leave it as-is. no harm done - it won't be requested by name, and this update only happens once.
                    catch (Exception ex) { }
                }

                //refresh data connection in case data migration took a long time
                this.refreshDataConnection();

                for (String name : changes.keySet())
                {
                    try (PreparedStatement updateStmnt = this.databaseConnection.prepareStatement(SQL_UPDATE_NAME))
                    {
                        updateStmnt.setString(1, changes.get(name).toString());
                        updateStmnt.setString(2, name);
                        updateStmnt.executeUpdate();
                    }
                    catch (SQLException e)
                    {
                        GriefPrevention.AddLogEntry("Unable to convert player data for " + name + ".  Skipping.");
                        GriefPrevention.AddLogEntry(e.getMessage());
                    }
                }
            }
            catch (SQLException e)
            {
                GriefPrevention.AddLogEntry("Unable to convert player data.  Details:");
                GriefPrevention.AddLogEntry(e.getMessage());
                e.printStackTrace();
            }
        }

        if (this.getSchemaVersion() <= 2)
        {
            statement = this.databaseConnection.createStatement();
            statement.execute(
                    "ALTER TABLE griefprevention_claimdata ADD inheritNothing BOOLEAN DEFAULT 0 AFTER managers");
        }

        //load claims data into memory

        results = statement.executeQuery("SELECT * FROM griefprevention_claimdata");

        ArrayList<Claim> claimsToRemove = new ArrayList<>();
        ArrayList<Claim> subdivisionsToLoad = new ArrayList<>();
        List<World> validWorlds = Bukkit.getServer().getWorlds();

        Long claimID = null;
        while (results.next())
        {
            try
            {
                //problematic claims will be removed from secondary storage, and never added to in-memory data store
                boolean removeClaim = false;

                long parentId = results.getLong("parentid");
                claimID = results.getLong("id");
                boolean inheritNothing = results.getBoolean("inheritNothing");
                Location lesserBoundaryCorner = null;
                Location greaterBoundaryCorner = null;
                String lesserCornerString = "(location not available)";
                try
                {
                    lesserCornerString = results.getString("lessercorner");
                    lesserBoundaryCorner = this.locationFromString(lesserCornerString, validWorlds);
                    String greaterCornerString = results.getString("greatercorner");
                    greaterBoundaryCorner = this.locationFromString(greaterCornerString, validWorlds);
                }
                catch (Exception e)
                {
                    if (e.getMessage() != null && e.getMessage().contains("World not found"))
                    {
                        GriefPrevention.AddLogEntry(
                                "Failed to load a claim (ID:" + claimID
                                        + ") because its world isn't loaded (yet?).  Please delete the claim or contact the GriefPrevention developer with information about which plugin(s) you're using to load or create worlds.  "
                                        + lesserCornerString);
                        continue;
                    }
                    else
                    {
                        throw e;
                    }
                }

                String ownerName = results.getString("owner");
                UUID ownerID = null;
                if (ownerName.isEmpty() || ownerName.startsWith("--"))
                {
                    ownerID = null; //administrative land claim or subdivision
                }
                else if (this.getSchemaVersion() < 1)
                {
                    try
                    {
                        ownerID = UUIDFetcher.getUUIDOf(ownerName);
                    }
                    catch (Exception ex)
                    {
                        GriefPrevention.AddLogEntry("This owner name did not convert to a UUID: " + ownerName + ".");
                        GriefPrevention.AddLogEntry(
                                "  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
                    }
                }
                else
                {
                    try
                    {
                        ownerID = UUID.fromString(ownerName);
                    }
                    catch (Exception ex)
                    {
                        GriefPrevention.AddLogEntry("This owner entry is not a UUID: " + ownerName + ".");
                        GriefPrevention.AddLogEntry(
                                "  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
                    }
                }

                String buildersString = results.getString("builders");
                List<String> builderNames = Arrays.asList(buildersString.split(";"));
                builderNames = this.convertNameListToUUIDList(builderNames);

                String containersString = results.getString("containers");
                List<String> containerNames = Arrays.asList(containersString.split(";"));
                containerNames = this.convertNameListToUUIDList(containerNames);

                String accessorsString = results.getString("accessors");
                List<String> accessorNames = Arrays.asList(accessorsString.split(";"));
                accessorNames = this.convertNameListToUUIDList(accessorNames);

                String managersString = results.getString("managers");
                List<String> managerNames = Arrays.asList(managersString.split(";"));
                managerNames = this.convertNameListToUUIDList(managerNames);
                Claim claim = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, ownerID, builderNames,
                        containerNames, accessorNames, managerNames, inheritNothing, claimID);

                if (removeClaim)
                {
                    claimsToRemove.add(claim);
                }
                else if (parentId == -1)
                {
                    //top level claim
                    this.addClaim(claim, false);
                }
                else
                {
                    //subdivision
                    subdivisionsToLoad.add(claim);
                }
            }
            catch (SQLException e)
            {
                GriefPrevention.AddLogEntry("Unable to load a claim.  Details: " + e.getMessage() + " ... " + results);
                e.printStackTrace();
            }
        }

        //add subdivisions to their parent claims
        for (Claim childClaim : subdivisionsToLoad)
        {
            //find top level claim parent
            Claim topLevelClaim = this.getClaimAt(childClaim.getLesserBoundaryCorner(), true, null);

            if (topLevelClaim == null)
            {
                claimsToRemove.add(childClaim);
                GriefPrevention.AddLogEntry(
                        "Removing orphaned claim subdivision: " + childClaim.getLesserBoundaryCorner().toString());
                continue;
            }

            //add this claim to the list of children of the current top level claim
            childClaim.parent = topLevelClaim;
            topLevelClaim.children.add(childClaim);
            childClaim.inDataStore = true;
        }

        for (Claim claim : claimsToRemove)
        {
            this.deleteClaimFromSecondaryStorage(claim);
        }

        if (this.getSchemaVersion() <= 2)
        {
            this.refreshDataConnection();
            statement = this.databaseConnection.createStatement();
            statement.execute("DELETE FROM griefprevention_claimdata WHERE id = '-1'");
        }

        super.initialize();
    }

    @Override
    synchronized void writeClaimToStorage(Claim claim) //see datastore.cs.  this will ALWAYS be a top level claim
    {
        try
        {
            this.refreshDataConnection();

            //wipe out any existing data about this claim
            this.deleteClaimFromSecondaryStorage(claim);

            //write claim data to the database
            this.writeClaimData(claim);
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry(
                    "Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner)
                            + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
        }
    }

    //actually writes claim data to the database
    synchronized private void writeClaimData(Claim claim) throws SQLException
    {
        String lesserCornerString = this.locationToString(claim.getLesserBoundaryCorner());
        String greaterCornerString = this.locationToString(claim.getGreaterBoundaryCorner());
        String owner = "";
        if (claim.ownerID != null) owner = claim.ownerID.toString();

        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();

        claim.getPermissions(builders, containers, accessors, managers);

        String buildersString = this.storageStringBuilder(builders);
        String containersString = this.storageStringBuilder(containers);
        String accessorsString = this.storageStringBuilder(accessors);
        String managersString = this.storageStringBuilder(managers);
        boolean inheritNothing = claim.getSubclaimRestrictions();
        long parentId = claim.parent == null ? -1 : claim.parent.id;

        try (PreparedStatement insertStmt = this.databaseConnection.prepareStatement(SQL_INSERT_CLAIM))
        {

            insertStmt.setLong(1, claim.id);
            insertStmt.setString(2, owner);
            insertStmt.setString(3, lesserCornerString);
            insertStmt.setString(4, greaterCornerString);
            insertStmt.setString(5, buildersString);
            insertStmt.setString(6, containersString);
            insertStmt.setString(7, accessorsString);
            insertStmt.setString(8, managersString);
            insertStmt.setBoolean(9, inheritNothing);
            insertStmt.setLong(10, parentId);
            insertStmt.executeUpdate();
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry(
                    "Unable to save data for claim at " + this.locationToString(claim.lesserBoundaryCorner)
                            + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
        }
    }

    //deletes a claim from the database
    @Override
    synchronized void deleteClaimFromSecondaryStorage(Claim claim)
    {
        try (PreparedStatement deleteStmnt = this.databaseConnection.prepareStatement(SQL_DELETE_CLAIM))
        {
            deleteStmnt.setLong(1, claim.id);
            deleteStmnt.executeUpdate();
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry("Unable to delete data for claim " + claim.id + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    PlayerData getPlayerDataFromStorage(UUID playerID)
    {
        PlayerData playerData = new PlayerData();
        playerData.playerID = playerID;

        try (PreparedStatement selectStmnt = this.databaseConnection.prepareStatement(SQL_SELECT_PLAYER_DATA))
        {
            selectStmnt.setString(1, playerID.toString());
            ResultSet results = selectStmnt.executeQuery();

            //if data for this player exists, use it
            if (results.next())
            {
                playerData.setAccruedClaimBlocks(results.getInt("accruedblocks"));
                playerData.setBonusClaimBlocks(results.getInt("bonusblocks"));
            }
        }
        catch (SQLException e)
        {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.AddLogEntry(playerID + " " + errors, CustomLogEntryTypes.Exception);
        }

        return playerData;
    }

    //saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
    @Override
    public void overrideSavePlayerData(UUID playerID, PlayerData playerData)
    {
        //never save data for the "administrative" account.  an empty string for player name indicates administrative account
        if (playerID == null) return;

        this.savePlayerData(playerID.toString(), playerData);
    }

    private void savePlayerData(String playerID, PlayerData playerData)
    {
        try (PreparedStatement deleteStmnt = this.databaseConnection.prepareStatement(SQL_DELETE_PLAYER_DATA);
                PreparedStatement insertStmnt = this.databaseConnection.prepareStatement(SQL_INSERT_PLAYER_DATA))
        {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(playerID));

            SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = sqlFormat.format(new Date(player.getLastPlayed()));
            deleteStmnt.setString(1, playerID);
            deleteStmnt.executeUpdate();

            insertStmnt.setString(1, playerID);
            insertStmnt.setString(2, dateString);
            insertStmnt.setInt(3, playerData.getAccruedClaimBlocks());
            insertStmnt.setInt(4, playerData.getBonusClaimBlocks());
            insertStmnt.executeUpdate();
        }
        catch (SQLException e)
        {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.AddLogEntry(playerID + " " + errors, CustomLogEntryTypes.Exception);
        }
    }

    @Override
    synchronized void incrementNextClaimID()
    {
        this.setNextClaimID(this.nextClaimID + 1);
    }

    //sets the next claim ID.  used by incrementNextClaimID() above, and also while migrating data from a flat file data store
    synchronized void setNextClaimID(long nextID)
    {
        this.nextClaimID = nextID;

        try (PreparedStatement deleteStmnt = this.databaseConnection.prepareStatement(SQL_DELETE_NEXT_CLAIM_ID);
                PreparedStatement insertStmnt = this.databaseConnection.prepareStatement(SQL_SET_NEXT_CLAIM_ID))
        {
            deleteStmnt.execute();
            insertStmnt.setLong(1, nextID);
            insertStmnt.executeUpdate();
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry("Unable to set next claim ID to " + nextID + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
        }
    }

    //updates the database with a group's bonus blocks
    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue)
    {
        //group bonus blocks are stored in the player data table, with player name = $groupName
        try (PreparedStatement deleteStmnt = this.databaseConnection.prepareStatement(SQL_DELETE_GROUP_DATA);
                PreparedStatement insertStmnt = this.databaseConnection.prepareStatement(SQL_INSERT_PLAYER_DATA))
        {
            SimpleDateFormat sqlFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = sqlFormat.format(new Date());
            deleteStmnt.setString(1, '$' + groupName);
            deleteStmnt.executeUpdate();

            insertStmnt.setString(1, '$' + groupName);
            insertStmnt.setString(2, dateString);
            insertStmnt.setInt(3, 0);
            insertStmnt.setInt(4, currentValue);
            insertStmnt.executeUpdate();
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry("Unable to save data for group " + groupName + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
        }
    }

    @Override
    synchronized void close()
    {
        if (this.databaseConnection != null)
        {
            try
            {
                if (!this.databaseConnection.isClosed())
                {
                    this.databaseConnection.close();
                }
            }
            catch (SQLException e) { }
        }

        this.databaseConnection = null;
    }

    private synchronized void refreshDataConnection() throws SQLException
    {
        if (this.databaseConnection == null || !this.databaseConnection.isValid(3))
        {
            if (this.databaseConnection != null && !this.databaseConnection.isClosed())
            {
                this.databaseConnection.close();
            }

            //set username/pass properties
            Properties connectionProps = new Properties();
            connectionProps.put("user", this.userName);
            connectionProps.put("password", this.password);
            connectionProps.put("autoReconnect", "true");
            connectionProps.put("maxReconnects", String.valueOf(Integer.MAX_VALUE));

            //establish connection
            this.databaseConnection = DriverManager.getConnection(this.databaseUrl, connectionProps);
        }
    }

    @Override
    protected int getSchemaVersionFromStorage()
    {
        try (PreparedStatement selectStmnt = this.databaseConnection.prepareStatement(SQL_SELECT_SCHEMA_VERSION))
        {
            ResultSet results = selectStmnt.executeQuery();

            //if there's nothing yet, assume 0 and add it
            if (!results.next())
            {
                this.setSchemaVersion(0);
                return 0;
            }
            //otherwise return the value that's in the table
            else
            {
                return results.getInt("version");
            }
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry("Unable to retrieve schema version from database.  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    protected void updateSchemaVersionInStorage(int versionToSet)
    {
        try (PreparedStatement deleteStmnt = this.databaseConnection.prepareStatement(SQL_DELETE_SCHEMA_VERSION);
                PreparedStatement insertStmnt = this.databaseConnection.prepareStatement(SQL_INSERT_SCHEMA_VERSION))
        {
            deleteStmnt.execute();

            insertStmnt.setInt(1, versionToSet);
            insertStmnt.executeUpdate();
        }
        catch (SQLException e)
        {
            GriefPrevention.AddLogEntry("Unable to set next schema version to " + versionToSet + ".  Details:");
            GriefPrevention.AddLogEntry(e.getMessage());
        }
    }

    /**
     * Concats an array to a string divided with the ; sign
     *
     * @param input Arraylist with strings to concat
     * @return String with all values from input array
     */
    private String storageStringBuilder(ArrayList<String> input)
    {
        String output = "";
        for (String string : input)
        {
            output += string + ";";
        }
        return output;
    }

}
