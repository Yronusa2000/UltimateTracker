package fr.yronusa.ultimatetracker.Database;

import fr.yronusa.ultimatetracker.InventoryLocation;
import fr.yronusa.ultimatetracker.TrackedItem;
import fr.yronusa.ultimatetracker.UltimateTracker;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.List;
import java.util.UUID;

import static fr.yronusa.ultimatetracker.Config.Config.*;


public class Database {

    public static Connection connection;

    public enum TYPE {
        mysql,
        mariadb,
        postgresql,
        oracle,
        sqlserver,
        sqlite
    }


    public static Connection getConnection() throws SQLException {
        if(!connection.isValid(2)){
            UltimateTracker.yro.sendMessage("§C NEW CONNEXION (TO DATABASE)");
            connect();
        }
        return connection;
    }

    public static void connect() throws SQLException {
        Database.connection = DriverManager.getConnection(Database.getJDBC(), databaseUser, databasePassword);
    }


    public static String getJDBC(){
         return switch (databaseType) {
            case oracle -> "jdbc:oracle:thin:@" + databaseHost + ":" + databasePort + ":orcl";
            case sqlserver ->
                    "jdbc:sqlserver://" + databaseHost + ":" + databasePort + ";databaseName=" + databaseName;
            case sqlite -> {
                String path = UltimateTracker.getInstance().getDataFolder().getAbsolutePath();
                yield "jdbc:sqlite:" + path + databaseName + ".db";
            }
            default -> "jdbc:" + databaseType + "://"
                    + databaseHost + ":" + databasePort + "/" + databaseName + "?useSSL=false";
        };
    }


    public static void add(TrackedItem trackedItem) {
        String itemBase64 = trackedItem.getBase64();

        String statement = "INSERT INTO TRACKED_ITEMS (UUID, ITEMBASE64, LAST_UPDATE, LAST_INVENTORIES, IS_BLACKLISTED) VALUES (?, ?, ?, ?, ?)";
        Bukkit.getScheduler().runTaskAsynchronously(UltimateTracker.getInstance(), new Runnable() {
            @Override
            public void run() {

                try {
                    Connection conn = getConnection();
                    PreparedStatement preparedStatement = conn.prepareStatement(statement);
                    preparedStatement.setString(1, trackedItem.getOriginalID().toString());
                    preparedStatement.setString(2, itemBase64);
                    preparedStatement.setTimestamp(3, trackedItem.getLastUpdateItem());
                    preparedStatement.setString(4, "TODO");
                    preparedStatement.setInt(5, 0);

                    int i = preparedStatement.executeUpdate();
                    if (i > 0) {
                        System.out.println("ROW INSERTED");
                    } else {
                        System.out.println("ROW NOT INSERTED");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }


            }
        });


    }

    public static Timestamp getLastUpdate(UUID uuid){

        String statement = "SELECT LAST_UPDATE FROM TRACKED_ITEMS WHERE UUID = ?";
        Timestamp lastUpdateTimestamp = null;

        try {
            Connection conn = getConnection();
            PreparedStatement preparedStatement = conn.prepareStatement(statement);
            preparedStatement.setString(1, uuid.toString());
            System.out.println(preparedStatement);

            ResultSet resultSet = preparedStatement.executeQuery();

            // Check if the result set has data
            if (resultSet.next()) {
                // Retrieve the last update timestamp from the result set
                lastUpdateTimestamp = resultSet.getTimestamp("LAST_UPDATE");
                // Print or use the timestamp as needed
                System.out.println("Last Update Timestamp for UUID " + uuid.toString() + ": " + lastUpdateTimestamp);
            } else {
                System.out.println("No data found for UUID: " + uuid);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Handle the exception appropriately
        }

        return lastUpdateTimestamp;
    }
    public static List<InventoryLocation> getLastInventories(UUID uuid){
        String sqlSelectTrackedItem= "SELECT * FROM SAVED_ITEMS WHERE UUID = " + uuid.toString();

        final String[] res = new String[0];
        Bukkit.getScheduler().runTaskAsynchronously(UltimateTracker.getInstance(), new Runnable() {
            @Override
            public void run() {
                try {
                    Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(sqlSelectTrackedItem);
                    ResultSet rs = ps.executeQuery(); {
                        while (rs.next()) {
                            res[0] = rs.getString("LAST_UPDATE");
                        }


                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    // handle the exception
                }
            }


        });
        return null;
    }
    public static void update(TrackedItem item, Timestamp newDate) {

        String verif = "SELECT 1 FROM TRACKED_ITEMS WHERE UUID = ?";
        String statement = "UPDATE TRACKED_ITEMS SET LAST_UPDATE = ? WHERE UUID = ?";
        Bukkit.getScheduler().runTaskAsynchronously(UltimateTracker.getInstance(), new Runnable() {
            @Override
            public void run() {

                try {
                    Connection conn = getConnection();
                    String uuid = item.getOriginalID().toString();
                    PreparedStatement verifPresence = conn.prepareStatement(verif);
                    verifPresence.setString(1, uuid);

                    PreparedStatement preparedStatement = conn.prepareStatement(statement);
                    preparedStatement.setTimestamp(1, newDate);
                    preparedStatement.setString(2, uuid);

                    ResultSet resultSet = verifPresence.executeQuery();
                    if(!resultSet.next()){
                        Database.add(item);
                        return;
                    }


                    int i = preparedStatement.executeUpdate();
                    if (i > 0) {
                        System.out.println("ROW UPDATED");
                    } else {
                        System.out.println("ROW NOT UPDATED");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }


            }
        });


    }

    public static boolean isDuplicated(TrackedItem item){
        Timestamp databaseTimestamp = Database.getLastUpdate(item.getOriginalID());
        Timestamp itemTimestamp = item.getLastUpdateItem();
        System.out.println("dtb ts : " + databaseTimestamp);
        System.out.println("item ts : " + itemTimestamp);
        return itemTimestamp.before(databaseTimestamp);
    }

    public static void blacklist(UUID oldID) {

        String statement = "UPDATE TRACKED_ITEMS SET BLACKLIST = ? WHERE UUID = ?";
        Bukkit.getScheduler().runTaskAsynchronously(UltimateTracker.getInstance(), new Runnable() {
            @Override
            public void run() {

                try {
                    Connection conn = getConnection();
                    PreparedStatement preparedStatement = conn.prepareStatement(statement);
                    preparedStatement.setInt(1, 1);
                    preparedStatement.setString(2, oldID.toString());

                    int i = preparedStatement.executeUpdate();
                    if (i > 0) {
                        System.out.println("ITEM SUCCESSFULLY BLACKLISTED");
                    } else {
                        System.out.println("ITEM NOT BLACKLISTED");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }


            }
        });


    }

}

