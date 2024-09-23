package me.stephenminer.cityproxy.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.commands.RequestCmd;
import me.stephenminer.cityproxy.records.MoneyRequest;
import me.stephenminer.cityproxy.records.PlayerRecord;
import me.stephenminer.cityproxy.records.Receipt;
import me.stephenminer.cityproxy.util.handlers.EcoHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.*;

/**
 * Handles plugin messages for the economy side of the plugin
 */
public class EconomyListener implements Listener {
    private final CityProxy plugin;
    public EconomyListener(){
        this.plugin = CityProxy.getInstance();
    }


    @EventHandler
    public void onMsg(PluginMessageEvent event){
        if (!event.getTag().equals("city:eco")) return;
        ByteArrayDataInput reader = ByteStreams.newDataInput(event.getData());
        String sub = reader.readUTF();
        switch (sub){
            case "update-bal" -> handleBalUpdate(reader);
            case "request-bal" -> handleBalRequest(reader);
            case "request-receipts" -> handleReceiptRequest(reader);
            case "transfer-request" -> handleTransferRequest(reader);
            case "money-request" -> handleMoneyRequest(reader);
        }
    }

    /**
     * @param num
     * @return num shortened to 2 decimal places
     */
    private double fixDouble(double num){
        int conv =(int) (num * 100);
        return conv / 100d;
    }

    /**
     *
     * @param uuid
     * @return PlayerRecord owned by the supplied UUID, null if none exist;
     */
    private PlayerRecord findRecord(UUID uuid){
        return plugin.records.getOrDefault(uuid,null);
    }


    public void handleBalUpdate(ByteArrayDataInput reader){
        UUID uuid = UUID.fromString(reader.readUTF());
        double bal = reader.readDouble();
        plugin.balances.put(uuid,bal);
    }

    public void handleBalRequest(ByteArrayDataInput reader){
        UUID sender = UUID.fromString(reader.readUTF());
        UUID uuid = UUID.fromString(reader.readUTF());
        EcoHandler handler = new EcoHandler();
        handler.sendBalance(sender, uuid);
    }

    public void handleTransferRequest(ByteArrayDataInput reader){
        UUID sender = UUID.fromString(reader.readUTF());
        if (plugin.dayCycle.getTransfers(sender) >= plugin.sendingLimits(true)){
            ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("You've already made the maximum amount of money transfers today!");
                msg.setColor(ChatColor.YELLOW);
                player.sendMessage(msg);
            }
            return;
        }
        UUID target = UUID.fromString(reader.readUTF());
        String name = nameFromUUID(target);
        if (name.equals("N/A")){
            ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("The inputted player isn't a real player");
                player.sendMessage(msg);
                return;
            }
        }
        double amount = fixDouble(reader.readDouble());
        if (amount > plugin.moneyLimits(true)){
            ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("The maximum amount of money you can transfer is " + plugin.moneyLimits(true) + "!");
                msg.setColor(ChatColor.YELLOW);
                player.sendMessage(msg);
            }
            return;
        }
        double senderBal = fixDouble(plugin.balances.get(sender));
        if (senderBal < amount) {
            ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
            if (player != null && player.isConnected()){
                BaseComponent msg = new ComponentBuilder("You do not have enough money to make this transfer! (Transfer amount: " + amount + ", Balance: " + senderBal).color(ChatColor.YELLOW).build();
                player.sendMessage(msg);
            }
            return;
        }
        //numbers reduced to 2 decimal places and floating point blah blah blahs removed
        double newSenderBal = plugin.subtract(senderBal, amount); //(int) ((senderBal * 100) - (amount * 100))) / 100d;
        double newTargetBal = plugin.add(plugin.balances.get(target), amount); //()int) ((plugin.balances.get(target) *100) + (amount * 100))) / 100d;
        plugin.balances.put(sender,newSenderBal);
        plugin.balances.put(target,newTargetBal);
        plugin.dayCycle.addTransfer(sender);
        ProxiedPlayer targetPlayer = plugin.getProxy().getPlayer(target);
        ProxiedPlayer sendingPlayer = plugin.getProxy().getPlayer(sender);
        //If connected to a server this cannot be null
        PlayerRecord senderRecord = findRecord(sender);
        PlayerRecord targetRecord = findRecord(target);
        if (targetPlayer != null && targetPlayer.isConnected()){
            BaseComponent msg = new ComponentBuilder("+" + amount + " from " + senderRecord.name()).color(ChatColor.AQUA).build();
            targetPlayer.sendMessage(msg);
        }
        if (sendingPlayer != null && sendingPlayer.isConnected()){
            BaseComponent msg = new ComponentBuilder("-" + amount + " (Sent to " + targetRecord.name() + ")").color(ChatColor.AQUA).build();
            sendingPlayer.sendMessage(msg);
        }
        if (senderRecord != null) {
            Receipt receipt = new Receipt(senderRecord.name(), -1 * amount, "Transfer",System.currentTimeMillis());
            addReceiptToRecord(senderRecord,receipt);
        }
        if (targetRecord != null){
            Receipt receipt = new Receipt(senderRecord.name(), amount,"Transfer",System.currentTimeMillis());
            addReceiptToRecord(targetRecord,receipt);
        }
    }

    public void handleMoneyRequest(ByteArrayDataInput reader){
        UUID requester = UUID.fromString(reader.readUTF());
        if (plugin.dayCycle.getRequests(requester) >= plugin.sendingLimits(false)){
            ProxiedPlayer player = plugin.getProxy().getPlayer(requester);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("You've already made the maximum amount of money requests today!");
                msg.setColor(ChatColor.YELLOW);
                player.sendMessage(msg);
            }
            return;
        }
        UUID target = UUID.fromString(reader.readUTF());
        double amount = fixDouble(reader.readDouble());
        if (amount > plugin.moneyLimits(true)){
            ProxiedPlayer player = plugin.getProxy().getPlayer(requester);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("The maximum amount of money you can request is " + plugin.moneyLimits(true) + "!");
                msg.setColor(ChatColor.YELLOW);
                player.sendMessage(msg);
            }
            return;
        }
        String name = nameFromUUID(target);
        if (name.equals("N/A")){
            ProxiedPlayer player = plugin.getProxy().getPlayer(requester);
            if (player != null && player.isConnected()){
                TextComponent msg = new TextComponent("The inputted player isn't a real player");
                msg.setColor(ChatColor.RED);
                player.sendMessage(msg);
                return;
            }
        }
        MoneyRequest request = new MoneyRequest(requester,target,amount);
        RequestCmd.requests.add(request);
        plugin.dayCycle.addRequest(requester);
        ProxiedPlayer targetPlayer = plugin.getProxy().getPlayer(target);
        if (targetPlayer != null && targetPlayer.isConnected()){
            TextComponent msg = new TextComponent(nameFromUUID(requester) + " has sent you a request for $" + amount);
            msg.setColor(ChatColor.AQUA);
            TextComponent accept = new TextComponent("[accept]");
            accept.setColor(ChatColor.GREEN);
            accept.setBold(true);
            TextComponent deny = new TextComponent("[deny]");
            deny.setColor(ChatColor.RED);
            deny.setBold(true);
            accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/request accept " + nameFromUUID(requester) + " " + amount));
            deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/request deny " + nameFromUUID(requester) + " " + amount));
            targetPlayer.sendMessage(msg);
            BaseComponent buttons = new ComponentBuilder(accept).append(deny).build();
            targetPlayer.sendMessage(buttons);
        }
        ProxiedPlayer sender = plugin.getProxy().getPlayer(requester);
        if (sender != null && sender.isConnected()){
            TextComponent msg = new TextComponent("You've sent a request to " + nameFromUUID(target) + " for $" + request.amount());
            msg.setColor(ChatColor.GREEN);
            sender.sendMessage(msg);
        }
    }

    private String nameFromUUID(UUID uuid){
        if (plugin.records.containsKey(uuid)) return plugin.records.get(uuid).name();
        return "N/A";
    }


    private void addReceiptToRecord(PlayerRecord record, Receipt receipt){
        List<Receipt> receipts = record.receipts();
        receipts.sort(Comparator.comparingLong(Receipt::timestamp));
        if (record.receipts().isEmpty()) receipts.add(receipt);
        else if(record.receipts().size() < 9) receipts.add(0,receipt);
        else receipts.set(0,receipt);
        receipts.sort(Comparator.comparingLong(Receipt::timestamp));
    }

    private void handleReceiptRequest(ByteArrayDataInput reader){
        UUID ownerId = UUID.fromString(reader.readUTF());
        PlayerRecord record = findRecord(ownerId);
        EcoHandler handler = new EcoHandler();
        handler.sendReceipts(ownerId, record.receipts().toArray(new Receipt[0]));
    }


    @EventHandler
    public void onJoin(ServerConnectedEvent event){
        //set starting balance
        ProxiedPlayer player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.balances.containsKey(uuid))
            plugin.balances.put(uuid,plugin.defaultBal());
    }
}
