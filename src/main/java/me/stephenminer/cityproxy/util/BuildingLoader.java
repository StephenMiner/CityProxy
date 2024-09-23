package me.stephenminer.cityproxy.util;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.BuildingRecord;
import me.stephenminer.cityproxy.records.RoomRecord;

import java.util.*;

public class BuildingLoader {
    private final String id;
    private final ConfigFile file;

    public BuildingLoader(String id){
        file = new ConfigFile(CityProxy.getInstance(), "building-storage.yml");
        this.id = id;
    }


    public BuildingRecord loadBuilding(){
        String data = file.getConfig().getString("buildings." + id);
        String[] unbox = data.split("/");
        String name = unbox[0];
        String server = unbox[1];
        String rooms = unbox[2];
        Set<String> roomNames = new HashSet<>();
        if (!rooms.isEmpty() && !rooms.isBlank()) {
            String[] roomSplit = rooms.split("#");
            for (String sRoom : roomSplit) {
                RoomRecord record = loadRoom(sRoom);
                CityProxy.getInstance().rooms.put(record.name(), record);
                roomNames.add(record.name());
            }
        }
        return new BuildingRecord(id,server, new HashSet<>(roomNames));
    }


    private RoomRecord loadRoom(String roomString){
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
}
