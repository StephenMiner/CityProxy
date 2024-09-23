package me.stephenminer.cityproxy.util;

import me.stephenminer.cityproxy.CityProxy;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class StorageFile {
    public final ConfigurationProvider provider;
    public Configuration config;
    public final File file;

    public StorageFile(CityProxy proxy, String name){
        provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        file = new File(proxy.getDataFolder(), name);
        if (!file.exists())
            file.mkdirs();
        try {
            config = provider.load(file);
        }catch (IOException e){
            e.printStackTrace();
        }
    }



    public Configuration getConfig(){ return config; }

    public void saveConfig(){
        try {
            provider.save(config, file);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
