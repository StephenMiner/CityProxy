package me.stephenminer.cityproxy;

import me.stephenminer.cityproxy.commands.ApartmentCmd;
import me.stephenminer.cityproxy.commands.AssignApartment;
import me.stephenminer.cityproxy.commands.BalanceCmd;
import me.stephenminer.cityproxy.commands.RequestCmd;
import me.stephenminer.cityproxy.listener.ApartmentListener;
import me.stephenminer.cityproxy.listener.EconomyListener;
import me.stephenminer.cityproxy.records.BuildingRecord;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.RoomRecord;
import me.stephenminer.cityproxy.util.BuildingLoader;
import me.stephenminer.cityproxy.util.ConfigFile;
import me.stephenminer.cityproxy.util.DayManager;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;

import java.util.*;

public final class CityProxy extends Plugin {
    private static CityProxy instance;

    public DayManager dayCycle;
    public Map<String, BuildingRecord> buildings;
    public Map<String, RoomRecord> rooms;
    public Map<UUID, Double> balances;

    public Map<UUID, PlayerRecord> records;

    public Set<String> buildingServerNames;



    @Override
    public void onEnable() {
        instance = this;
        buildings = new HashMap<>();
        rooms = new HashMap<>();
        buildingServerNames = new HashSet<>();
        balances = new HashMap<>();
        records = loadHasPlayed();
        dayCycle = new DayManager();
        dayCycle.start();
        loadBuildings();
        loadBalances();
        ConfigFile config = new ConfigFile(this,"config.yml", true);
        getProxy().registerChannel("city:info");
        getProxy().registerChannel("city:eco");
        registerCommands();
        registerEvents();

    }

    private void registerEvents(){
        PluginManager pm = getProxy().getPluginManager();
        pm.registerListener(this, new ApartmentListener());
        pm.registerListener(this, new EconomyListener());
    }
    private void registerCommands(){
        PluginManager pm = getProxy().getPluginManager();
        pm.registerCommand(this, new ApartmentCmd());
        pm.registerCommand(this, new AssignApartment());
        pm.registerCommand(this,new BalanceCmd());
        pm.registerCommand(this, new RequestCmd());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        dayCycle.stop();
        getProxy().unregisterChannel("city:info");
        getProxy().unregisterChannel("city:eco");
        Collection<BuildingRecord> records = buildings.values();
        ConfigFile file = new ConfigFile(this,"building-storage.yml");
        for (BuildingRecord record : records){
            writeBuildingToFile(file, record);
        }
        buildings.clear();

        ConfigFile playerFile = new ConfigFile(this,"joined.yml");
        List<String> uuidList = this.records.values().stream().map(PlayerRecord::toString).toList();
        playerFile.getConfig().set("players", uuidList);
        playerFile.saveConfig();


        ConfigFile ecoFile = new ConfigFile(this,"economy.yml");
        Set<UUID> keys = balances.keySet();
        StringBuilder builder = new StringBuilder();
        for (UUID uuid : keys){
            double bal = ((int) (balances.get(uuid) * 100)) / 100d;
            builder.append(uuid.toString()).append(',').append(bal).append('/');
        }
        if (!builder.isEmpty()) builder.deleteCharAt(builder.length()-1);
        ecoFile.getConfig().set("balances",builder.toString());
        ecoFile.saveConfig();
    }



//,

    /**
     * Takes a collection of UUIDs and turns them into a single comma separate String
     * @param collection
     * @return String formatted as "UUID,UUID,UUID,etc"
     */
    private String flattenCollection(Collection<UUID> collection){
        if (collection.isEmpty()) return " ";
        StringBuilder builder = new StringBuilder();
        collection.forEach(uuid->builder.append(uuid.toString()).append(','));
        return builder.deleteCharAt(builder.length()-1).toString();
    }
//%

    /**
     * Generates a String containing the data from the provided RoomRecorded separated by %
     * @param roomRec
     * @return String formatted as "name%owner-uuid%friends-list%roommates-list%size"
     */
    public String fromRoomRec(RoomRecord roomRec){
        String ownerStr = roomRec.owner() == null ? "null" : roomRec.owner().toString();
        return roomRec.name() + "%" + ownerStr + "%" + flattenCollection(roomRec.friends()) + "%" + flattenCollection(roomRec.roomates()) + "%" + roomRec.size();
    }


    /**
     * Saves the provided BuildingRecord to the provided ConfigFile
     * @param file
     * @param record
     */
    public void writeBuildingToFile(ConfigFile file, BuildingRecord record){
        file.getConfig().set("buildings." + record.name(),record.toString());
        file.saveConfig();
    }

    /**
     * Loads all buildings saved inside of the building-storage.yml file
     */
    public void loadBuildings(){
        ConfigFile file = new ConfigFile(this,"building-storage.yml");
        Collection<String> keys = file.getConfig().getSection("buildings").getKeys();
        for (String buildingId : keys){
            BuildingLoader loader = new BuildingLoader(buildingId);
            BuildingRecord record = loader.loadBuilding();
            buildings.put(record.name(),record);
            buildingServerNames.add(record.serverName());
        }
    }

    /**
     * @return The area that makes a room a default room found in config.yml formatted as "#x#" ie. 8x8 -> 64
     */
    public int defaultArea(){
         ConfigFile file = new ConfigFile(this,"config.yml", true);
         if (file.getConfig().contains("default-size")) {
             String content = file.getConfig().getString("default-size");
             return areaFromStr(content);
         }
         return 64;
    }

    public double defaultBal(){
        ConfigFile file = new ConfigFile(this,"config.yml", true);
        if (file.getConfig().contains("starting-bal"))
            return file.getConfig().getDouble("starting-bal");
        else return 400;
    }
    /**
     * get default limits for sending money
     * @param transfers if true, will return limit for transfers, else the limit for requests
     */
    public double moneyLimits(boolean transfers){
        String path = transfers ? "transfer-limit" : "request-limit";
        ConfigFile file = new ConfigFile(this,"config.yml");
        if (file.getConfig().contains(path)) return file.getConfig().getDouble(path);
        else return 4000;
    }

    /**
     * Get daily limits for how many times someone can send money in a day
     * @param transfers
     * @return if transfers, the daily limit for transfers, else the one for requests
     */
    public double sendingLimits(boolean transfers){
        String path = transfers ? "daily-transfers" : "daily-requests";
        ConfigFile file = new ConfigFile(this,"config.yml");
        if (file.getConfig().contains(path)) return file.getConfig().getDouble(path);
        else return 5;
    }
    /**
     * @param str formatted as "numberxnumber" ex. "8x8"
     * @return the area from a size string multiplying the 2 numbers found before and after the x character
     */
    public int areaFromStr(String str){
        String[] unbox = str.split("x");
        return Integer.parseInt(unbox[0]) * Integer.parseInt(unbox[0]);
    }

    /**
     * @return a Set of UUIDs from the joined.yml file
     */
    private Map<UUID,PlayerRecord> loadHasPlayed(){
        ConfigFile file = new ConfigFile(this,"joined.yml");
        if (!file.getConfig().contains("players")) return new HashMap<>();
        List<String> content = file.getConfig().getStringList("players");
       // getProxy().getScheduler().schedule(this,()->System.out.println("A" + content.size()),1, TimeUnit.SECONDS);
        Map<UUID, PlayerRecord> records = new HashMap<>();
        for (String entry : content){
            PlayerRecord record = PlayerRecord.fromString(entry);
            records.put(record.uuid(),record);
        }
        return records;
    }

    private void loadBalances(){
        ConfigFile file = new ConfigFile(this, "economy.yml");
        if (file.config.contains("balances")) {
            String balString = file.getConfig().getString("balances");
            if (balString == null || balString.isEmpty() || balString.isBlank()) return;
            String[] split = balString.split("/");
            for (String entry : split) {
                String[] data = entry.split(",");
                balances.put(UUID.fromString(data[0]), Double.parseDouble(data[1]));
            }
        }
    }



    /**
     * Collects all RoomRecords whose area-size matches the default area-size from CityProxy#defaultArea()
     * Only returns ownerless rooms
     * @return
     */
    public List<RoomRecord> defaultRooms(){
        int match = defaultArea();
        Collection<RoomRecord> records = rooms.values();
        List<RoomRecord> out = records.stream().filter(room-> areaFromStr(room.size()) == match && room.owner()==null).toList();
        return out;
    }

    public double subtract(double d1, double d2){
        int conv1 = (int) (d1*100);
        int conv2 = (int) (d2*100);
        return (conv1 - conv2) / 100d;
    }
    public double add(double d1, double d2){
        int conv1 = (int) (d1 * 100);
        int conv2 = (int) (d2 * 100);
        return (conv1 + conv2) / 100d;
    }


    public static CityProxy getInstance(){
        return instance;
    }
}
