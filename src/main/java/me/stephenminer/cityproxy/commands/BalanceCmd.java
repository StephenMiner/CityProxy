package me.stephenminer.cityproxy.commands;

import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.PlayerRecord;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BalanceCmd extends Command implements TabExecutor {
    private final CityProxy plugin;

    public BalanceCmd(){
        super("bal");
        this.plugin = CityProxy.getInstance();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1){
            BaseComponent msg = new ComponentBuilder("You need to use a subcommand with this command and its subsequent arguments").color(ChatColor.RED).build();
            sender.sendMessage(msg);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub){
            case "set" -> setBalance(sender,args);
            case "give" -> giveBalance(sender, args);
        }
    }

    private void setBalance(CommandSender sender, String[] args){
        if (args.length < 3){
            BaseComponent msg = new ComponentBuilder("You need to input the player you wish to set the balance of, and what you want their new balance to be").color(ChatColor.RED).build();
            sender.sendMessage(msg);
            return;
        }
        PlayerRecord record = findRecord(args[1]);
        if (record == null){
            BaseComponent msg = new ComponentBuilder("Could not find a player with the name " + args[1]).color(ChatColor.RED).build();
            sender.sendMessage(msg);
            return;
        }
        try {
            double amount = Double.parseDouble(args[2]);
            plugin.balances.put(record.uuid(),amount);
            BaseComponent senderMsg = new ComponentBuilder("Set " + record.name() + "'s balance to " + amount).color(ChatColor.GREEN).build();
            sender.sendMessage(senderMsg);
            ProxiedPlayer target = plugin.getProxy().getPlayer(record.uuid());
            if (target != null && target.isConnected()){
                BaseComponent targetMsg = new ComponentBuilder("Your balance was set to " + amount).color(ChatColor.AQUA).build();
                target.sendMessage(targetMsg);
            }
        }catch (Exception e){
            BaseComponent msg = new ComponentBuilder(args[2] + " is not a real number, but needs to be!").color(ChatColor.RED).build();
            sender.sendMessage(msg);
        }

    }

    private void giveBalance(CommandSender sender, String[] args){
        if (args.length < 3){
            BaseComponent msg = new ComponentBuilder("You need to input the player you wish to give money to, and how much you want to give them").color(ChatColor.RED).build();
            sender.sendMessage(msg);
            return;
        }
        PlayerRecord record = findRecord(args[1]);
        if (record == null){
            BaseComponent msg = new ComponentBuilder("Could not find a player with the name " + args[1]).color(ChatColor.RED).build();
            sender.sendMessage(msg);
            return;
        }
        try {
            double amount = Double.parseDouble(args[2]);
            double prev = plugin.balances.getOrDefault(record.uuid(),0d);
            double newBal = ((int) (prev *100) + (amount * 100)) / 100d;
            plugin.balances.put(record.uuid(),newBal);
        }catch (Exception e){
            BaseComponent msg = new ComponentBuilder(args[2] + " is not a real number, but needs to be!").color(ChatColor.RED).build();
            sender.sendMessage(msg);
        }
    }




    private PlayerRecord findRecord(String name){
        Collection<PlayerRecord> records = plugin.records.values();
        for (PlayerRecord record : records){
            if (record.name().equalsIgnoreCase(name)) return record;
        }
        return null;
    }



    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)  return subs(args[0]);
        if (args.length == 2) return players(args[1]);
        return new ArrayList<>();
    }


    private List<String> players(String match){
        Collection<ProxiedPlayer> players = plugin.getProxy().getPlayers();
        return filter(players.stream().map(ProxiedPlayer::getName).toList(), match);
    }

    private List<String> subs(String match){
        List<String> subs = new ArrayList<>();
        subs.add("give");
        subs.add("set");
        return filter(subs, match);
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
