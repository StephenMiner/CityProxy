package me.stephenminer.cityproxy.util;

import me.stephenminer.cityproxy.CityProxy;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class ConfigFile {
    public final ConfigurationProvider provider;
    public Configuration config;
    public final File file;

    public ConfigFile(CityProxy proxy, String name) {
        this(proxy, name, false);
    }

    public ConfigFile(CityProxy proxy, String name, boolean loadDefault) {
        provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        file = new File(proxy.getDataFolder(), name);
        try {
            if (!file.exists()) {
                Files.createDirectories(proxy.getDataFolder().toPath());
                if (loadDefault) {
                    try (InputStream stream = proxy.getResourceAsStream(name)) {
                        Files.copy(stream, file.toPath());
                    }
                }else file.createNewFile();
            }
            config = provider.load(file);

        } catch (IOException e) {
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
