package me.stephenminer.cityproxy.commands;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.Invite;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.RoomRecord;
import me.stephenminer.cityproxy.util.handlers.ApartmentHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.*;
import java.util.stream.Collectors;

public class ApartmentCmd extends Command implements TabExecutor {
    private final List<Invite> invites;
    private final CityProxy plugin;
    public ApartmentCmd(){
        super("apt");
        this.invites = new ArrayList<>();
        this.plugin = CityProxy.getInstance();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1){
            sender.sendMessage(new ComponentBuilder("You need to input a sub command").color(ChatColor.YELLOW).build());
            return;
        }
        String sub = args[0];
        if (sender instanceof ProxiedPlayer player){
            switch (sub){
                case "invite" -> invitePlayer(player,args);
                case "accept" -> acceptInvite(player,args);
                case "promote" -> promotePlayer(player, args);
                case "demote" -> demotePlayer(player, args);
                case "info" -> handleInfoSending(player,args);
                case "revoke" -> revokeInvite(player,args);
            }
        }else sender.sendMessage(new ComponentBuilder("You need to be a player to use this command!").color(ChatColor.RED).build());
    }


    private void revokeInvite(ProxiedPlayer player, String[] args){
        if (args.length < 2){
            BaseComponent msg = new ComponentBuilder("You need to input the player you wish to revoke your invite from").build();
            player.sendMessage(msg);
            return;
        }
        ProxiedPlayer invitee = plugin.getProxy().getPlayer(args[1]);
        UUID uuid = invitee == null ? uuidFromName(args[1]) : invitee.getUniqueId();
        if (invitee == null && uuid == null){
            BaseComponent msg = new ComponentBuilder("Couldn't find player " + args[1]).color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        for (int i = invites.size()-1; i>= 0; i--){
            Invite invite = invites.get(i);
            if (invite.sender().equals(player.getUniqueId()) && invite.invitee().equals(uuid)){
                invites.remove(i);
                BaseComponent msg = new ComponentBuilder("You've revoked " + args[1] + "'s invite").color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
                ProxiedPlayer target = plugin.getProxy().getPlayer(invite.invitee());
                if (target != null && target.isConnected()){
                    BaseComponent toTarget = new ComponentBuilder("Your invite from " + player.getName() + " was revoked!").color(ChatColor.YELLOW).build();
                    target.sendMessage(toTarget);
                }
                return;
            }
        }
        BaseComponent msg = new ComponentBuilder(args[1] + " doesn't have any invites from you!").color(ChatColor.YELLOW).build();
        player.sendMessage(msg);
        return;

    }

    private void invitePlayer(ProxiedPlayer player, String[] args){
        if (args.length < 3){
            BaseComponent msg = new ComponentBuilder("You need to input the player you wish to invite and whether they should be a friend or roommate")
                    .color(ChatColor.YELLOW)
                    .build();
            player.sendMessage(msg);
            return;
        }
        ProxiedPlayer invitee = plugin.getProxy().getPlayer(args[1]);
        UUID uuid = invitee == null ? uuidFromName(args[1]) : invitee.getUniqueId();
        if (invitee == null && uuid == null){
            BaseComponent msg = new ComponentBuilder("Couldn't find player " + args[1]).color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        String status = args[2];
        int level;
        if (status.equalsIgnoreCase("friend")) level = 0;
        else if (status.equalsIgnoreCase("roommate")) level = 1;
        else {
            BaseComponent msg = new ComponentBuilder("The argument after the player name should either be 'friend' or 'roommate'").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        RoomRecord ownedRoom = findOwnedRoom(player.getUniqueId());
        if (ownedRoom == null){
            BaseComponent msg = new ComponentBuilder("You do not own a room to invite someone to").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        if (uuid.equals(ownedRoom.owner())){
            BaseComponent msg = new ComponentBuilder("You can't invite yourself to your own room!").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        Invite invite = new Invite(player.getUniqueId(),uuid, ownedRoom.name(), level, player.getName());

        BaseComponent toSender = new ComponentBuilder("Sent an invite to " + invitee.getName()).color(ChatColor.GREEN).build();
        player.sendMessage(toSender);
        if(invitee != null && invitee.isConnected()) {
            BaseComponent toInvitee = new ComponentBuilder("You were invited to be " + player.getName() + "'s " + status.toLowerCase()).color(ChatColor.GREEN).build();
            BaseComponent tutorial = new ComponentBuilder("Do /apt accept " + player.getName() + " to accept!").color(ChatColor.GREEN).build();
            // BaseComponent component = new ComponentBuilder().
            invitee.sendMessage(toInvitee);
            invitee.sendMessage(tutorial);
            invites.add(invite);
        }
    }

    /**
     *
     * @param qualifier the UUID to check for
     * @param sender whether you are you looking for a sender or an invitee (false for invitee)
     * @return an Invite matching the provided information or null if none exist
     */
    private Invite findInviteUUID(UUID qualifier, boolean sender){
        for (Invite invite : invites){
            if (sender){
                if (invite.sender().equals(qualifier)) return invite;
            }else if (invite.invitee().equals(qualifier)) return invite;
        }
        return null;
    }

    /**
     * Finds an Invite sent by a player with the provided name and sent to a player with the provided UUID
     * Will remove the invite that it finds from the invite list
     * @param senderName the name of the player who sent the invite
     * @param invitee the UUID of the player who was invited
     * @return an Invite sent by senderName to invitee, null if none exist
     */
    private Invite findInviteName(String senderName, UUID invitee){
        for (int i = invites.size()-1; i >= 0; i--){
            Invite invite = invites.get(i);
            if (senderName.equalsIgnoreCase(invite.senderName()) && invitee.equals(invite.invitee()))
                return invite;
        }
        return null;
    }

    /**
     *
     * @param owner
     * @return The first room found that the provided UUID owns (Should only ever be 1 or 0 anyways) Null if there are none
     */
    private RoomRecord findOwnedRoom(UUID owner){
        return plugin.rooms.values().stream().filter(room->owner.equals(room.owner())).findFirst().orElse(null);
    }

    public void acceptInvite(ProxiedPlayer player, String[] args){
        int size = args.length;
        if (size < 2){
            BaseComponent msg = new ComponentBuilder("You need to specify which player you wish to accept an invite from").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        String senderName = args[1];
        Invite invite = findInviteName(senderName, player.getUniqueId() );
        //invites.remove(invite);
        if (invite == null){
            BaseComponent msg = new ComponentBuilder("You do not have an invite to accept from this player!").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        String roomName = invite.roomName();
        if (!plugin.rooms.containsKey(roomName)){
            BaseComponent msg = new ComponentBuilder("The room you have been invited to no longer exists!").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        RoomRecord room = plugin.rooms.get(roomName);
        if (!invite.sender().equals(room.owner())){
            BaseComponent msg = new ComponentBuilder("This invite is no longer valid since " + invite.senderName() + " no longer owns the room").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        boolean roommate = invite.permLevel() == 1;
        if (roommate) room.addRoomate(invite.invitee());
        else room.addFriend(invite.invitee());
        ApartmentHandler handler = new ApartmentHandler();
        byte[] data = handler.getRoomCollectionData(room);
        player.getServer().sendData("city:info",data);
        ProxiedPlayer sender = plugin.getProxy().getPlayer(invite.sender());
        if (sender != null && sender.isConnected()) {
            BaseComponent senderMsg = new ComponentBuilder(player.getName() + " has accepted your invitation to become a " + (roommate ? "roommate" : "friend")).color(ChatColor.GREEN).build();
            sender.sendMessage(senderMsg);
        }
        BaseComponent inviteeMsg = new ComponentBuilder("You've accepted " + invite.senderName() + "'s invite!").color(ChatColor.GREEN).build();
        player.sendMessage(inviteeMsg);
    }


    public void promotePlayer(ProxiedPlayer player, String[] args){
        // /apt promote [player]
        if (args.length < 2) {
            BaseComponent msg = new ComponentBuilder("You need to specify which player you wish to promote").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        UUID targetId = uuidFromName(args[1]);
        if (targetId == null){
            BaseComponent msg = new ComponentBuilder("Couldn't find player " + args[1]).color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        RoomRecord room = findOwnedRoom(player.getUniqueId());
        if (room == null){
            BaseComponent msg = new ComponentBuilder("You don't own a Room you can promote people for!").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }

        if (!room.friends().contains(targetId)) {
            BaseComponent msg = new ComponentBuilder("The provided player is either already a roommate or isn't a friend yet! Use the invite command to add them as a friend").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        room.removeFriend(targetId);
        room.addRoomate(targetId);
        ApartmentHandler handler = new ApartmentHandler();
        byte[] sendData = handler.getRoomCollectionData(room);
        player.getServer().sendData("city:info",sendData);

        ProxiedPlayer target = plugin.getProxy().getPlayer(args[1]);
        if (target != null && target.isConnected()){
            BaseComponent msg = new ComponentBuilder("You've been promoted to a roommate in " + player.getName() + "'s room").color(ChatColor.AQUA).build();
            target.sendMessage(msg);
        }
        BaseComponent msg = new ComponentBuilder("You've promoted " + args[1] + "!").color(ChatColor.AQUA).build();
        player.sendMessage(msg);
        plugin.getLogger().info("Sending updated collection data");
    }

    public void demotePlayer(ProxiedPlayer player, String[] args){
        // /apt demote [player]
        if (args.length < 2) {
            BaseComponent msg = new ComponentBuilder("You need to specify which player you wish to demote").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        UUID targetId = uuidFromName(args[1]);
        if (targetId == null){
            BaseComponent msg = new ComponentBuilder("Couldn't find player " + args[1]).color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        RoomRecord room = findOwnedRoom(player.getUniqueId());
        if (room == null){
            BaseComponent msg = new ComponentBuilder("You don't own a Room you can demote people for!").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        if (!room.friends().contains(targetId) && !room.roomates().contains(targetId)) {
            BaseComponent msg = new ComponentBuilder("The provided player is neither friend or roommate already ").color(ChatColor.YELLOW).build();
            player.sendMessage(msg);
            return;
        }
        String msg = "";
        if (room.friends().contains(targetId)){
            msg = "You're no longer a friend allowed in " + player.getName() + "'s room";
            room.friends().remove(targetId);
        }else if (room.roomates().contains(targetId)){
            msg = "You're no longer a roommate in " + player.getName() + "'s room, but you're still a friend";
            room.roomates().remove(targetId);
            room.friends().add(targetId);
        }

        BaseComponent toSender = new ComponentBuilder(args[1] + " has been demoted").color(ChatColor.GREEN).build();
        ProxiedPlayer target = plugin.getProxy().getPlayer(targetId);
        if (target != null && target.isConnected()) {
            BaseComponent toTarget = new ComponentBuilder(msg).color(ChatColor.AQUA).build();
            target.sendMessage(toTarget);
        }
        player.sendMessage(toSender);
        ApartmentHandler handler = new ApartmentHandler();
        byte[] sendData = handler.getRoomCollectionData(room);
        player.getServer().sendData("city:info",sendData);
        plugin.getLogger().info("Sending updated collection data");
    }

    public void handleInfoSending(ProxiedPlayer player, String[] args){
        int size = args.length;
        /* /apt info was run
            Displays the sender's room data (friends, roommates)
         */
        if (size < 2){
            RoomRecord room = findOwnedRoom(player.getUniqueId());
            if (room == null){
                BaseComponent msg = new ComponentBuilder("You don't have a room to display info for!").color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
                return;
            }
            displayPlayerSets(player,"Roommate List",room.roomates());
            displayPlayerSets(player,"Friends List", room.friends());
        }else{
            String name = args[1];
            UUID uuid = uuidFromName(name);
            if (uuid == null){
                BaseComponent msg = new ComponentBuilder("Couldn't find any record of a player with name " + name).color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
                return;
            }
            RoomRecord room = findOwnedRoom(uuid);
            if (room == null){
                BaseComponent msg = new ComponentBuilder(name + " doesn't have any room for you to view info on").color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
                return;
            }
            displayPlayerSets(player,name + "'s " + " Roommate List", room.roomates());
            displayPlayerSets(player,name + "'s " + " Friends List", room.friends());

        }

    }

    /**
     * Will display player names from the provided UUIDs to the provided player following a message containing the provided title
     * @param uuids
     */
    private void displayPlayerSets(ProxiedPlayer player, String title, Set<UUID> uuids){
        BaseComponent header = new ComponentBuilder(title).color(ChatColor.AQUA).build();
        player.sendMessage(header);
        for (UUID uuid : uuids){
            String name = nameFromUUID(uuid);
            BaseComponent msg = new ComponentBuilder("- " + name).color(ChatColor.AQUA).build();
            player.sendMessage(msg);
        }

    }

    private String nameFromUUID(UUID uuid){
        PlayerRecord record = plugin.records.getOrDefault(uuid,null);
        if (record == null) return "N/A";
        else return record.name();
    }
    private UUID uuidFromName(String name){
        Collection<PlayerRecord> records = plugin.records.values();
        for (PlayerRecord record : records){
            if (record.name().equalsIgnoreCase(name)) return record.uuid();
        }
        return null;
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return null;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        UUID uuid = player.getUniqueId();
        int size = args.length;
        if (size == 1) return subCmds(args[0]);

        if (size == 2){
            String sub = args[0].toLowerCase();
            return switch (sub){
                case "invite", "info" -> playerNames(args[1]);
                case "promote" -> friendNames(uuid, args[1]);
                case "demote" -> involvedNames(uuid,args[1]);
                case "accept" -> invites(uuid, args[1]);
                default -> playerNames();
            };
        }
        if (size == 3){
            String sub = args[0].toLowerCase();
            if (sub.equals("invite")) return inviteOptions(args[2]);
        }
        return playerNames();
    }



    private List<String> subCmds(String match){
        List<String> subs = new ArrayList<>();
        subs.add("invite");
        subs.add("accept");
        subs.add("promote");
        subs.add("demote");
        subs.add("info");
        subs.add("revoke");
        return filter(subs,match);
    }

    private List<String> inviteOptions(String match){
        List<String> options = new ArrayList<>();
        options.add("friend");
        options.add("roommate");
        return filter(options,match);
    }


    private List<String> playerNames(){
        Collection<ProxiedPlayer> players = plugin.getProxy().getPlayers();
        return players.stream().map(ProxiedPlayer::getName).collect(Collectors.toList());
    }

    private List<String> playerNames(String match){
        return filter(playerNames(), match);
    }

    private List<String> friendNames(UUID owner, String match){
        RoomRecord owned = findOwnedRoom(owner);
        if (owned == null) return playerNames();
        else return filter(owned.friends().stream().map(this::nameFromUUID).toList(),match);
    }

    private List<String> invites(UUID invitee, String match){
        List<String> base = new ArrayList<>();
        for (Invite invite : invites){
            if (invite.invitee().equals(invitee)) base.add(invite.senderName());
        }
        return filter(base, match);
    }

    private List<String> involvedNames(UUID owner, String match){
        RoomRecord owned = findOwnedRoom(owner);
        if (owned == null) return playerNames();
        List<String> base = owned.roomates().stream().map(this::nameFromUUID).collect(Collectors.toList());
        base.addAll(owned.friends().stream().map(this::nameFromUUID).toList());
        return filter(base, match);
    }


    private List<String> filter(Collection<String> base, String match){
        match = match.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String entry : base){
            String temp = ChatColor.stripColor(entry).toLowerCase();
            if (temp.contains(match)) filtered.add(entry);
        }
        return filtered;
    }
}
