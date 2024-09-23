package me.stephenminer.cityproxy.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.BuildingRecord;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.RoomRecord;
import me.stephenminer.cityproxy.util.ConfigFile;
import me.stephenminer.cityproxy.util.handlers.ApartmentHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Contains the listeners relating to the Apartment functions
 */
public class ApartmentListener implements Listener {
    private final CityProxy plugin;
    private final Set<String> sentServers;



    public ApartmentListener(){
        this.plugin = CityProxy.getInstance();
        sentServers = new HashSet<>();
    }


    @EventHandler
    public void join(ServerConnectedEvent event){
        Server server = event.getServer();
        String name = server.getInfo().getName();
        ProxiedPlayer player = event.getPlayer();
        //send server name
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("server-name");
        out.writeUTF(name);
        server.sendData("city:info",out.toByteArray());

        if (!sentServers.contains(name) && plugin.buildingServerNames.contains(name)) {
            sentServers.add(name);
            ApartmentHandler sender = new ApartmentHandler();
            server.sendData("city:info", sender.getServerBuildingData(name));
            //This else case is needed so that if the owner disables a server, but not this proxy, the building data can still be sent on that server's restart
        }else sendBuildingsInfoPing(server);
        if (!hasRecord(player)){
            PlayerRecord record = new PlayerRecord(player.getName(),player.getUniqueId());
            //log player record
            plugin.records.put(record.uuid(),record);
            //assign starter room
            RoomRecord foundRoom = findAvailableRoom();
            if (foundRoom == null){
                player.sendMessage(new ComponentBuilder().append("Server Network has run out of default rooms! Could not give you one!").color(ChatColor.YELLOW).build());
                plugin.getLogger().warning("There are no more available rooms of the proper size to give to new players");
            }else{
                BuildingRecord host = findHostBuilding(foundRoom);
                if (host == null){
                    player.sendMessage(new ComponentBuilder().append("Server Network could not find the Building your Room is in!").color(ChatColor.YELLOW).build());
                    plugin.getLogger().warning("The building hosting " + foundRoom.name() + " could not be found");
                    return;
                }
                RoomRecord ownedRoom = foundRoom.setOwner(player.getUniqueId());
                plugin.rooms.put(ownedRoom.name(),ownedRoom);
                ServerInfo serverInfo = plugin.getProxy().getServerInfo(host.serverName());
                if (!player.getServer().getInfo().equals(serverInfo))
                    player.connect(serverInfo);
                plugin.getProxy().getScheduler().schedule(plugin,()->{
                    ApartmentHandler handler = new ApartmentHandler();
                    serverInfo.sendData("city:info", handler.getOwnerChange(host.name(),ownedRoom.name(),ownedRoom.owner(), true), true);
                },1, TimeUnit.SECONDS);
                BaseComponent component = new ComponentBuilder("You are being assigned to a room currently! Once assigned, you will be teletported").color(ChatColor.GREEN).build();
                player.sendMessage(component);
            }
        }else{
            //This object is not null
            PlayerRecord record = findRecord(player);
            if (!player.getName().equalsIgnoreCase(record.name())) {
                plugin.records.remove(record.uuid());
                plugin.records.put(player.getUniqueId(),new PlayerRecord(player.getName(), player.getUniqueId()));
            }
        }
    }


    private boolean hasRecord(ProxiedPlayer player){
        return findRecord(player) != null;
    }

    private PlayerRecord findRecord(ProxiedPlayer player){
        return plugin.records.getOrDefault(player.getUniqueId(),null);
    }

    /**
     * Finds an available default RoomRecord (size == default size) that a new player can own (So not owned already)
     * @return A random RoomRecord whose size matches the default room size (see CityProxy#defaultArea())
     */
    private RoomRecord findAvailableRoom(){
        Random random = new Random();
        List<RoomRecord> records = plugin.defaultRooms();
        if (records.isEmpty()) return null;
        return records.get(random.nextInt(records.size()));
    }

    /**
     * Finds the BuildingRecord that hosts the provided RoomRecord
     * @param record The RoomRecord to find the host of
     * @return A building record whose list of rooms contains the provided one's name
     */
    private BuildingRecord findHostBuilding(RoomRecord record){
        Collection<BuildingRecord> buildings = plugin.buildings.values();
        for (BuildingRecord building : buildings){
            if (building.rooms().contains(record.name())) return building;
        }
        return null;
    }



    @EventHandler
    public void onMsg(PluginMessageEvent event) {
        String tag = event.getTag();
        if (!"city:info".equals(tag)) return;
        byte[] data = event.getData();
        ByteArrayDataInput reader = ByteStreams.newDataInput(data);
        String sub = reader.readUTF();
        switch (sub) {
            case "building-update" -> handleBuildingUpdate(reader);
            case "room-update" -> handleRoomUpdate(reader);
            case "building-creation" -> handleBuildingCreation(reader);
            case "room-creation" -> handleRoomCreation(reader);
            case "buildings-info-request" -> handleBuildingsInfoRequest(reader);
            case "building-name-change" -> handleBuildingNameChange(reader);
            case "room-name-change" -> handleRoomNameChange(reader);
            case "building-delete" -> handleBuildingDeletion(reader);
            case "room-delete" -> handleRoomDeletion(reader);
        }
    }

    /**
     * Reads the data from the ByteArrayDataInput to construct a building record to replace an existing one
     * If one doesn't actually exist, a new roomrecord is created anyway
     */
    private void handleBuildingUpdate(ByteArrayDataInput reader){
        String buildingData = reader.readUTF();
        ApartmentHandler handler = new ApartmentHandler();
        BuildingRecord building = handler.parseDataMsg(buildingData);
        plugin.buildings.put(building.name(),building);
        plugin.getLogger().info("Recieved building update change!");
        ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
        plugin.writeBuildingToFile(file, building);
    }

    /**
     * Reads data from ByteArrayDataInput to construct a RoomRecord to replace an existing one
     * If one doesn't actually exist, a new roomrecord is created anyway
     * @param reader
     */
    private void handleRoomUpdate(ByteArrayDataInput reader){
        String roomData = reader.readUTF();
        ApartmentHandler handler = new ApartmentHandler();
        RoomRecord room = handler.loadRoom(roomData);
        plugin.rooms.put(room.name(),room);
        plugin.getLogger().info("Recieved room update change!");
        Collection<BuildingRecord> buildings = plugin.buildings.values();
        for (BuildingRecord building : buildings){
            if (building.rooms().contains(room.name())){
                ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
                plugin.writeBuildingToFile(file,building);
                return;
            }
        }
    }

    /**
     * Creates a BuildingRecord only containing a name and servername and an empty HashSet
     * Will fail if a building with the read name already exists on record in the buildings Map
     * In this case, the player and sending server are attempted to be notified
     * @param reader
     */
    private void handleBuildingCreation(ByteArrayDataInput reader){
        UUID sender = UUID.fromString(reader.readUTF());
        String serverName = reader.readUTF();
        String name = reader.readUTF();
        ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
        if (plugin.buildings.containsKey(name)) {
            if (player != null && player.isConnected()){
                BaseComponent component = new ComponentBuilder().color(ChatColor.YELLOW).append("Failed to finalize building creation, " + name + " exists on another server").build();
                player.sendMessage(component);
            }
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            output.writeUTF("building-creation-failure");
            output.writeUTF(name);
            if (player != null) player.getServer().getInfo().sendData("city:info",output.toByteArray());
            else plugin.getLogger().warning("Failed to send building creation failure response!");
        }else{
            BuildingRecord record = new BuildingRecord(name,serverName,new HashSet<>());
            plugin.buildings.put(record.name(),record);
            if (player != null && player.isConnected()){
                BaseComponent component = new ComponentBuilder().color(ChatColor.GREEN).append("Successfully to finalized building creation for " + name).build();
                player.sendMessage(component);
            }
            ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
            plugin.writeBuildingToFile(file, record);
        }
    }

    private void handleRoomCreation(ByteArrayDataInput reader){
        UUID sender = UUID.fromString(reader.readUTF());
        String building = reader.readUTF();
        String room = reader.readUTF();
        String spawn = reader.readUTF();
        String pos1 = reader.readUTF();
        String pos2 = reader.readUTF();
        ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
        if (plugin.rooms.containsKey(room)){
            if (player != null && player.isConnected()){
                BaseComponent component = new ComponentBuilder().color(ChatColor.YELLOW).append("Failed to finalize room creation, " + room + " exists on another server").build();
                player.sendMessage(component);
            }
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            output.writeUTF("room-creation-failure");
            output.writeUTF(building);
            output.writeUTF(room);
            if (player != null) player.getServer().getInfo().sendData("city:info",output.toByteArray());
            else plugin.getLogger().warning("Failed to send room creation failure response!");
        }else{
            //RoomRecord record = new RoomRecord(room,null,new HashSet<>(), new HashSet<>());
            //plugin.rooms.put(record.name(),record);
            if (player != null && player.isConnected()){
                BaseComponent component = new ComponentBuilder().color(ChatColor.YELLOW).append("Finalizing room creation " + room + "!").build();
                player.sendMessage(component);
            }
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            output.writeUTF("room-creation-success");
            output.writeUTF(building);
            output.writeUTF(room);
            output.writeUTF(spawn);
            output.writeUTF(pos1);
            output.writeUTF(pos2);
            if (player != null) player.getServer().getInfo().sendData("city:info",output.toByteArray());
            else plugin.getLogger().warning("Failed to send room creation success response!");
        }
    }

    private void handleBuildingsInfoRequest(ByteArrayDataInput reader){
        String name = reader.readUTF();
        ServerInfo server = plugin.getProxy().getServerInfo(name);
        if (server != null) {
            ApartmentHandler handler = new ApartmentHandler();
            byte[] buildingData = handler.getServerBuildingData(name);
            server.sendData("city:info",buildingData, true);
            plugin.getLogger().info("Sending server building data for " + name + " upon it's request.");
        }else plugin.getLogger().warning("Attempted to send server building data to " + name + ", but couldn't fint he server");
    }

    private void sendBuildingsInfoPing(Server server){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("buildings-info-ping");
        output.writeUTF(server.getInfo().getName());
        server.sendData("city:info",output.toByteArray());
        plugin.getLogger().info("Sending out offer to send building data to " + server.getInfo().getName());
    }

    private void handleBuildingNameChange(ByteArrayDataInput reader){
        UUID sender = UUID.fromString(reader.readUTF());
        String old = reader.readUTF();
        String name = reader.readUTF();
        ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
        ApartmentHandler handler = new ApartmentHandler();
        if (plugin.buildings.containsKey(name)){
            if (player != null && player.isConnected()){
                BaseComponent msg = new ComponentBuilder(name + " is already taken! Cannot rename building to this").color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
                handler.sendBuildNameChangeResult(player,false,old,name);
                return;
            }
        }
        BuildingRecord building = plugin.buildings.remove(old);
        building.delete();
        plugin.buildings.put(name,building.setName(name));
        ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
        plugin.writeBuildingToFile(file, plugin.buildings.get(name));
        if (player != null && player.isConnected()){
            BaseComponent msg = new ComponentBuilder("Changed building name to " + name).color(ChatColor.GREEN).build();
            player.sendMessage(msg);
            handler.sendBuildNameChangeResult(player,true,old,name);
            return;
        }
    }

    /**
     * Handles Room Name Changes, sending the change confirmation or denial to the server that initially sent the data
     * @param reader
     */
    public void handleRoomNameChange(ByteArrayDataInput reader){
        UUID uuid = UUID.fromString(reader.readUTF());
        String bName = reader.readUTF();
        String old = reader.readUTF();
        String newName = reader.readUTF();
        ProxiedPlayer player = plugin.getProxy().getPlayer(uuid);
        boolean canSend = player != null && player.isConnected();
        ApartmentHandler handler = new ApartmentHandler();
        if (!plugin.buildings.containsKey(bName)){
            if (canSend){
                BaseComponent msg = new ComponentBuilder("Couldn't find building " + bName).color(ChatColor.RED).build();
                player.sendMessage(msg);
                handler.sendRoomNameChangeResult(player,false,bName,old,newName);
                return;
            }
        }else{
            BuildingRecord building = plugin.buildings.get(bName);
            RoomRecord room = plugin.rooms.remove(old);
            if (room == null){
                if (canSend) {
                    BaseComponent msg = new ComponentBuilder("Couldn't find room " + old).color(ChatColor.RED).build();
                    player.sendMessage(msg);
                    handler.sendRoomNameChangeResult(player, false, bName, old, newName);
                }
                return;
            }
            plugin.rooms.put(newName,room.setName(newName));
            ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
            building.rooms().remove(old);
            building.rooms().add(newName);
            plugin.writeBuildingToFile(file,building);
            if (canSend) {
                BaseComponent msg = new ComponentBuilder("Changed room name " + old + " to " + newName).color(ChatColor.GREEN).build();
                player.sendMessage(msg);
                handler.sendRoomNameChangeResult(player, true, bName, old, newName);
            }
        }
    }

    /**
     * Deletes records of the building read from data sent from a bukkit server
     * Updates the storage file
     * @param reader
     */
    public void handleBuildingDeletion(ByteArrayDataInput reader){
        String bName = reader.readUTF();
        BuildingRecord building = plugin.buildings.remove(bName);
        if (building == null) return;
        building.delete();
        Set<String> rooms = building.rooms();
        for (String roomName : rooms)
            plugin.rooms.remove(roomName);
        plugin.getLogger().info("Deleted building " + bName);
    }

    /**
     * Deletes a RoomRecord and removes its association from its BuildingRecord
     * updates the storage file
     * @param reader
     */
    public void handleRoomDeletion(ByteArrayDataInput reader){
        String bName = reader.readUTF();
        String roomName = reader.readUTF();
        BuildingRecord building = plugin.buildings.getOrDefault(bName,null);
        if (building == null) return;
        //Clears data
        plugin.rooms.remove(roomName);
        //Remove room from building
        building.rooms().remove(roomName);
        //save
        ConfigFile file = new ConfigFile(plugin,"building-storage.yml");
        plugin.writeBuildingToFile(file, building);
        plugin.getLogger().info("Deleted room " + roomName);
    }

}
