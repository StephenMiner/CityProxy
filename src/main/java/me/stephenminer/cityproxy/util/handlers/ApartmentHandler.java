package me.stephenminer.cityproxy.util;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.BuildingRecord;
import me.stephenminer.cityproxy.records.RoomRecord;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;

/**
 * Handles sending data to and from the proxy and a server
 */
public class DataHandler {
    private final CityProxy plugin;
    public DataHandler(){
        this.plugin = CityProxy.getInstance();
    }


    /**
     * gets room ownership change data as a byte array
     * @param building the name of the building that the room is contained in
     * @param room the name of the room whose owner changed
     * @param newOwner the UUID of the new owner
     * @param defaultOwner whether the room ownership change was due to this room being automatically assigned to a brand new player or not
     * @return byte array containing all the supplied data
     */
    public byte[] getOwnerChange(String building, String room, UUID newOwner, boolean defaultOwner){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("room-owner-change");
        output.writeUTF(building);
        output.writeUTF(room);
        output.writeUTF(newOwner.toString());
        output.writeBoolean(defaultOwner);
        return output.toByteArray();
    }

    /**
     * returns a byte array containing the data of a specific building
     * @param buildingId
     * @return
     */
    public byte[] getBuildingData(String buildingId){
        if (!plugin.buildings.containsKey(buildingId)) return null;
        BuildingRecord building = plugin.buildings.get(buildingId);
        String data = createBuildingString(building);
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(data);
        return output.toByteArray();
    }

    /**
     * returns a byte array containing the data of all buildings contained on the server
     * @param serverName
     * @return
     */
    public byte[] getServerBuildingData(String serverName){
        List<String> buildings = plugin.buildings.values().stream()
                .filter(building->building.serverName().equals(serverName))
                .map(BuildingRecord::name).toList();
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("buildings-info");
        output.writeInt(buildings.size());
        for (String buildingId : buildings){
            //I don't actually think this is a possible case considering these names came from the buildings map. Oh well.
            if (!plugin.buildings.containsKey(buildingId))
                continue;
            BuildingRecord building = plugin.buildings.get(buildingId);
            String data = createBuildingString(building);
            output.writeUTF(data);
        }
        return output.toByteArray();
    }

    public byte[] getRoomCollectionData(RoomRecord room){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("room-set-update");
        output.writeUTF(room.name());
        output.writeUTF(flattenSet(room.friends()));
        output.writeUTF(flattenSet(room.roomates()));
        return output.toByteArray();
    }
    //#
    private String createBuildingString(BuildingRecord building){
        StringBuilder output = new StringBuilder(building.name())
                .append('/').append(building.serverName()).append('/');
        Set<String> roomNames = building.rooms();
        for (String roomName : roomNames){
            if (!plugin.rooms.containsKey(roomName)){
                plugin.getLogger().warning("Couldnt find room with key " + roomName + ". Some data might be lost!");
            }else {
                RoomRecord room = plugin.rooms.get(roomName);
                output.append(plugin.fromRoomRec(room)).append('#');
            }
        }
        return output.deleteCharAt(output.length()-1).toString();
    }

    /**
     * Converts a String gotten through a plugin message into a BuildingRecord (Creating relevant RoomRecords and placing them into the room Map in CityProxy)
     * This does not add the BuildingRecord to the building Map in CityProxy
     * @param str formatted as "name/server-name/RoomString#RoomString#etc..."
     * @return
     */
    public BuildingRecord parseDataMsg(String str){
        String[] split = str.split("/");
        String name = split[0];
        String server = split[1];
        Set<String> roomNames = new HashSet<>();
        if (!(split[2] == null || split[2].isBlank())) {
            String[] roomData = split[2].split("#");
            for (String sRoom : roomData) {
                RoomRecord room = loadRoom(sRoom);
                plugin.rooms.put(room.name(), room);
                roomNames.add(room.name());
            }
        }
        return new BuildingRecord(name, server, roomNames);
    }


    /**
     * Loads a room from a string formatted as "name%owner-uuid%friend-list%roommate-list
     * friend-list and roommatelist are formatted as "uuid,uuid,uuid,etc..."
     * @param roomString The string to read data from
     * @return a RoomRecord contaning the data from the provided String
     */

    public RoomRecord loadRoom(String roomString){
        String[] unbox = roomString.split("%");
        String name = unbox[0];
        String sUUID = unbox[1];
        UUID owner;
        if (sUUID.equals("null")) owner = null;
        else owner = UUID.fromString(unbox[1]);
        Set<UUID> friends = unflattenString(unbox[2]);
        Set<UUID> roomates = unflattenString(unbox[3]);
        String size = unbox[4];
        RoomRecord roomRecord = new RoomRecord(name, owner, friends, roomates, size);
        return roomRecord;
    }

    /**
     * Takes a String and formats a HashSet of UUIDSfrom its contents
     * @param str String formatted as "UUID,UUID,UUID,etc.."
     * @return
     */
    private Set<UUID> unflattenString(String str){
        if (str == null || str.isBlank()) return new HashSet<>();
        String[] unbox = str.split(",");
        Set<UUID> uuids = new HashSet<>();
        for (String entry : unbox)
            uuids.add(UUID.fromString(entry));
        return uuids;
    }

    private String flattenSet(Set<UUID> uuids){
        if (uuids.isEmpty()) return " ";
        StringBuilder builder = new StringBuilder();
        for (UUID uuid : uuids){
            builder.append(uuid.toString()).append(',');
        }
        builder.deleteCharAt(builder.length()-1);
        return builder.toString();
    }


    public void sendBuildNameChangeResult(ProxiedPlayer sender, boolean result, String oldName, String newName){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("building-name-change-result");
        output.writeBoolean(result);
        output.writeUTF(oldName);
        output.writeUTF(newName);
        byte[] send = output.toByteArray();
        sender.getServer().sendData("city:info",send);
    }

    public void sendRoomNameChangeResult(ProxiedPlayer sender, boolean result, String building, String oldName, String newName){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("room-name-change-result");
        output.writeBoolean(result);
        output.writeUTF(building);
        output.writeUTF(oldName);
        output.writeUTF(newName);
        byte[] send = output.toByteArray();
        sender.getServer().sendData("city:info",send);
    }
}
