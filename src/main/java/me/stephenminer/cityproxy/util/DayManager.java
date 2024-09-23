package me.stephenminer.cityproxy.util;

import me.stephenminer.cityproxy.CityProxy;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DayManager {
    private final CityProxy plugin;
    private boolean run;
    private int dayCounter, dayPeriod;

    public Map<UUID, Integer> requests, transfers;

    public DayManager(){
        this.plugin = CityProxy.getInstance();
        requests = new HashMap<>();
        transfers = new HashMap<>();
    }

    public void start(){
        if (run) return;
        run = true;
        plugin.getProxy().getScheduler().schedule(plugin,()->{
            if (dayCounter >= dayPeriod){
                //decrementMaps();
                requests.clear();
                transfers.clear();
                dayCounter = 0;
                broadcast("A day has passed");
                updatePeriod();
                plugin.getLogger().info("A day has passed");
            }
            dayCounter++;
        }, 0,1, TimeUnit.SECONDS);
    }

    public void decrementMaps(){
        Set<UUID> reqKeys = requests.keySet();
        for (UUID uuid : reqKeys){
            int dec = Math.max(0,requests.get(uuid) - 1);
            requests.put(uuid, dec);
        }
        Set<UUID> tranKeys = transfers.keySet();
        for (UUID uuid : tranKeys){
            int dec = Math.max(0,transfers.get(uuid) - 1);
            transfers.put(uuid, dec);
        }
    }
    public void stop(){
        run = false;
        transfers.clear();
        requests.clear();
    }

    public void updatePeriod(){
        ConfigFile config = new ConfigFile(plugin,"config.yml");
        if (!config.getConfig().contains("day-period")) dayPeriod = 10*60;
        else dayPeriod = config.getConfig().getInt("day-period");
    }

    public void addTransfer(UUID uuid){
        int inc = transfers.getOrDefault(uuid,0) + 1;
        transfers.put(uuid, inc);
    }
    public void addRequest(UUID uuid){
        int inc = requests.getOrDefault(uuid, 0) + 1;
        requests.put(uuid, inc);
    }

    public void broadcast(String msg){
        TextComponent text = new TextComponent(msg);
        text.setColor(ChatColor.AQUA);
        Collection<ProxiedPlayer> players = plugin.getProxy().getPlayers();
        for (ProxiedPlayer player : players){
            player.sendMessage(text);
        }
    }

    public int getRequests(UUID uuid){ return requests.getOrDefault(uuid,0); }
    public int getTransfers(UUID uuid){ return transfers.getOrDefault(uuid,0); }
}
