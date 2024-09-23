package me.stephenminer.cityproxy.commands;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.MoneyRequest;
import me.stephenminer.cityproxy.records.PlayerRecord;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.protocol.packet.Chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AcceptRequestCmd extends Command {
    public static final List<MoneyRequest> requests = new ArrayList<>();
    private final CityProxy plugin;

    public AcceptRequestCmd(){
        super("request-accept");
        this.plugin = CityProxy.getInstance();
    }
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        try{
            UUID requester = uuidFromName(args[0]);
         //   UUID requester = UUID.fromString(args[0]);
            double amount = Double.parseDouble(args[1]);
            MoneyRequest request = findRequest(player.getUniqueId(),requester,amount);
            if (request == null) return;
            fulfillRequest(player, request);
        }catch (Exception e){ e.printStackTrace(); }
    }

    private UUID uuidFromName(String name){
        Collection<PlayerRecord> records = plugin.records.values();
        for (PlayerRecord record : records){
            if (record.name().equalsIgnoreCase(name)) return record.uuid();
        }
        return null;
    }
    private String nameFromUUID(UUID uuid){
        if (plugin.records.containsKey(uuid)) return plugin.records.get(uuid).name();
        return "N/A";
    }

    private MoneyRequest findRequest(UUID target, UUID requester, double amount){
        for (MoneyRequest request : AcceptRequestCmd.requests){
            if (request.amount() == amount && request.requester().equals(requester) && request.target().equals(target)) return request;
        }
        return null;
    }


    private void fulfillRequest(ProxiedPlayer target, MoneyRequest request){
        double bal = plugin.balances.get(target.getUniqueId());
        if (bal < request.amount()){
            BaseComponent msg = new ComponentBuilder("You do not have enough money to fulfill this request").color(ChatColor.RED).build();
            target.sendMessage(msg);
            return;
        }
        double newBal = plugin.subtract(bal,request.amount());
        plugin.balances.put(target.getUniqueId(),newBal);
        double requesterBal = plugin.add(plugin.balances.get(request.requester()),request.amount());
        plugin.balances.put(request.requester(),requesterBal);
        ProxiedPlayer requester = plugin.getProxy().getPlayer(request.requester());
        if (requester != null && requester.isConnected()){
            BaseComponent msg = new ComponentBuilder(target.getName() + " has accepted your request for $" + request.amount()).color(ChatColor.GREEN).build();
            requester.sendMessage(msg);
        }
        BaseComponent msg = new ComponentBuilder("You've accepted " + nameFromUUID(request.requester()) + "'s request for $"  + request.amount()).color(ChatColor.GREEN).build();
        target.sendMessage(msg);
        AcceptRequestCmd.requests.remove(request);
    }


}
