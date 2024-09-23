package me.stephenminer.cityproxy.records;

import java.util.Set;
import java.util.UUID;

public record RoomRecord(String name, UUID owner, Set<UUID> friends, Set<UUID> roomates, String size) {


    public RoomRecord setOwner(UUID owner){
        return new RoomRecord(name, owner,friends, roomates,size);
    }

    public RoomRecord setName(String name){
        return new RoomRecord(name,owner,friends,roomates,size);
    }

    public void addRoomate(UUID uuid){
        roomates.add(uuid);
    }
    public void removeRoomate(UUID uuid){
        roomates.remove(uuid);
    }
    public void addFriend(UUID uuid) {
        friends.add(uuid);
    }
    public void removeFriend(UUID uuid){
        friends.remove(uuid);
    }


}
