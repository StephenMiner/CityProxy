package me.stephenminer.cityproxy.commands;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.MoneyRequest;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.Receipt;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.*;

public class RequestCmd extends Command {
    public static final List<MoneyRequest> requests = new ArrayList<>();
    private final CityProxy plugin;

    public RequestCmd(){
        super("request");
        this.plugin = CityProxy.getInstance();
    }
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        if (args.length < 3) {
            sendRequestMsgs(player);
            return;
        }
        String option = args[0].toLowerCase();
        try{
            UUID requester = uuidFromName(args[1]);
         //   UUID requester = UUID.fromString(args[0]);
            double amount = Double.parseDouble(args[2]);
            MoneyRequest request = findRequest(player.getUniqueId(),requester,amount);
            if (request == null) return;
            if (option.equals("accept"))
                fulfillRequest(player, request);
            else if (option.equals("deny"))
                denyRequest(player,request);
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
        for (MoneyRequest request : RequestCmd.requests){
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
        RequestCmd.requests.remove(request);
        PlayerRecord targetRecord = findRecord(target.getUniqueId());
        if (targetRecord != null){
            Receipt targetReceipt = new Receipt(nameFromUUID(request.requester()),-1 * request.amount(),"Request", System.currentTimeMillis());
            addReceiptToRecord(targetRecord,targetReceipt);
        }
        PlayerRecord requesterRecord = findRecord(request.requester());
        if (requesterRecord != null){
            Receipt requesterReceipt =new Receipt(target.getName(),request.amount(),"Request",System.currentTimeMillis());
            addReceiptToRecord(requesterRecord,requesterReceipt);
        }



    }

    private void denyRequest(ProxiedPlayer target, MoneyRequest request){
        RequestCmd.requests.remove(request);
        ProxiedPlayer requester = plugin.getProxy().getPlayer(request.requester());
        if (requester != null && requester.isConnected()){
            BaseComponent msg = new ComponentBuilder("You're request to " + target.getName() + " has bene denied").color(ChatColor.AQUA).build();
            target.sendMessage(msg);
            return;
        }
    }

    private void sendRequestMsgs(ProxiedPlayer target){
        TextComponent start = new TextComponent("Money Requests:");
        target.sendMessage(start);
        for (MoneyRequest request : RequestCmd.requests){
            if (!request.target().equals(target.getUniqueId())) continue;
            TextComponent msg = new TextComponent("Request from " + nameFromUUID(request.requester()) + ", requesting $" + request.amount());
            target.sendMessage(msg);
            target.sendMessage(constructMsg(request));
        }
    }

    private BaseComponent constructMsg(MoneyRequest request){
        TextComponent accept = new TextComponent("[accept]");
        accept.setBold(true);
        accept.setColor(ChatColor.GREEN);
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/request accept " + nameFromUUID(request.requester()) + " " + request.amount()));
        TextComponent deny = new TextComponent("[deny]");
        deny.setBold(true);
        deny.setColor(ChatColor.RED);
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/request deny " + nameFromUUID(request.requester()) + " " + request.amount()));
        return new ComponentBuilder().append(accept).append(deny).build();
    }

    private PlayerRecord findRecord(UUID uuid){
        return plugin.records.getOrDefault(uuid,null);
    }

    private void addReceiptToRecord(PlayerRecord record, Receipt receipt){
        List<Receipt> receipts = record.receipts();
        receipts.sort(Comparator.comparingLong(Receipt::timestamp));
        if (record.receipts().isEmpty()) receipts.add(receipt);
        else if(record.receipts().size() < 9) receipts.add(0,receipt);
        else receipts.set(0,receipt);
        receipts.sort(Comparator.comparingLong(Receipt::timestamp));
    }


}
