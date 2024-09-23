package me.stephenminer.cityproxy.records;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.util.ConfigFile;

import java.util.Set;

public record BuildingRecord(String name, String serverName, Set<String> rooms) {


    public BuildingRecord setName(String name){
        return new BuildingRecord(name,serverName,rooms);
    }

    public void delete(){
        ConfigFile file = new ConfigFile(CityProxy.getInstance(),"building-storage.yml");
        file.getConfig().set("buildings." + name,null);
        file.saveConfig();
    }

    @Override
    public String toString(){
        CityProxy plugin = CityProxy.getInstance();
        String roomString;
        if (!rooms.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String id : rooms) {
                if (plugin.rooms.containsKey(id)) builder.append(plugin.fromRoomRec(plugin.rooms.get(id))).append('#');
            }
            builder.deleteCharAt(builder.length() - 1);
            roomString=builder.toString();
        }else roomString = " ";
        return name + "/" + serverName + "/" + roomString;
    }
}
