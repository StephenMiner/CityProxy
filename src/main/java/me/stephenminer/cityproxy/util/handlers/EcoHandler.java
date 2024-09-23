package me.stephenminer.cityproxy.util.handlers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.stephenminer.cityproxy.CityProxy;
import me.stephenminer.cityproxy.records.Receipt;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

public class EcoHandler {
    private final CityProxy plugin;

    public EcoHandler(){
        this.plugin = CityProxy.getInstance();
    }

    public void sendBalance(UUID sender, UUID user){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        double bal = plugin.balances.getOrDefault(user,0d);
        output.writeUTF("send-bal");
        output.writeUTF(user.toString());
        output.writeDouble(bal);
        output.writeUTF(sender.toString());
        ProxiedPlayer player = plugin.getProxy().getPlayer(sender);
        if (player != null) player.getServer().getInfo().sendData("city:eco", output.toByteArray(),true);
    }


    public void sendReceipt(Receipt receipt){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("send-receipt");
        output.writeUTF(receipt.merchant());
        output.writeDouble(receipt.paid());
        output.writeUTF(receipt.reason());
        output.writeLong(receipt.timestamp());
    }

    public void sendReceipts(UUID owner, Receipt[] receipts){
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("send-receipts");
        output.writeUTF(owner.toString());
        output.writeInt(receipts.length - nullCount(receipts));
        for (Receipt receipt : receipts){
            if (receipt == null) continue;
            output.writeUTF(receipt.toString());
        }
        ProxiedPlayer player = plugin.getProxy().getPlayer(owner);
        if (player != null && player.isConnected())
            player.getServer().getInfo().sendData("city:eco",output.toByteArray(),true);
    }

    private int nullCount(Object[] objs){
        int count = 0;
        for (Object obj : objs) if (obj == null) count ++;

        return count;
    }


}
