package me.stephenminer.cityproxy.commands;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.BuildingRecord;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.RoomRecord;
import me.stephenminer.cityproxy.util.handlers.ApartmentHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AssignApartment extends Command {
    private final CityProxy plugin;
    public AssignApartment() {
        super("setowner");
        this.plugin = CityProxy.getInstance();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int size = args.length;

        if (size < 1){
            BaseComponent msg = new ComponentBuilder("You need to specify who you want to assign an apartment to").color(ChatColor.YELLOW).build();
            sender.sendMessage(msg);
            return;
        }
        String name = args[0];
        ProxiedPlayer target = plugin.getProxy().getPlayer(name);
        if (target.getUniqueId() == null){
            BaseComponent msg = new ComponentBuilder("Couldn't find player named " + args[0]).color(ChatColor.YELLOW).build();
            BaseComponent info = new ComponentBuilder("The target needs to be online").color(ChatColor.YELLOW).build();
            sender.sendMessage(msg);
            sender.sendMessage(info);
            return;
        }
        RoomRecord owned = findOwnedRoom(target.getUniqueId());
        if (owned != null){
            BaseComponent msg = new ComponentBuilder(args[0] + " already owns a room").color(ChatColor.YELLOW).build();
            sender.sendMessage(msg);
            return;
        }

        RoomRecord room = findAvailableRoom();
        if (room == null){
            sender.sendMessage(new ComponentBuilder().append("Server Network has run out of default rooms! Could not give you one!").color(ChatColor.YELLOW).build());
            plugin.getLogger().warning("There are no more available rooms of the proper size to give to new players");
            return;
        }
        BuildingRecord host = findHostBuilding(room);
        if (host == null){
            sender.sendMessage(new ComponentBuilder().append("Server Network could not find the Building your Room is in!").color(ChatColor.YELLOW).build());
            plugin.getLogger().warning("The building hosting " + room.name() + " could not be found");
            return;
        }
        final RoomRecord newRoom = room.setOwner(target.getUniqueId());
        plugin.rooms.put(newRoom.name(),newRoom);
        ServerInfo server = plugin.getProxy().getServerInfo(host.serverName());
        target.connect(server);
        if (!target.getServer().getInfo().equals(server))
            target.connect(server);

        plugin.getProxy().getScheduler().schedule(plugin,()->{
            ApartmentHandler handler = new ApartmentHandler();
            server.sendData("city:info", handler.getOwnerChange(host.name(),newRoom.name(),newRoom.owner(), true), true);
        },1, TimeUnit.SECONDS);
        BaseComponent component = new ComponentBuilder("You are being assigned to a room currently! Once assigned, you will be teletported").color(ChatColor.GREEN).build();
        target.sendMessage(component);



    }

    /**
     *
     * @param owner
     * @return The first room found that the provided UUID owns (Should only ever be 1 or 0 anyways) Null if there are none
     */
    private RoomRecord findOwnedRoom(UUID owner){
        return plugin.rooms.values().stream().filter(room->owner.equals(room.owner())).findFirst().orElse(null);
    }

    private UUID uuidFromName(String name){
        Collection<PlayerRecord> records = plugin.records.values();
        for (PlayerRecord record : records){
            if (record.name().equalsIgnoreCase(name)) return record.uuid();
        }
        return null;
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
}
