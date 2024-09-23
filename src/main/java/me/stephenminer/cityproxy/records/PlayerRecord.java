package me.stephenminer.cityproxy.records;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PlayerRecord(String name, UUID uuid, List<Receipt> receipts) {
    public PlayerRecord(String name, UUID uuid){
        this(name, uuid, new ArrayList<>());
    }

    public String toString(){
        if (!receipts.isEmpty()) {
            StringBuilder receiptStr = new StringBuilder();
            for (Receipt receipt : receipts) {
                if (receipt == null) continue;
                receiptStr.append(receipt.toString()).append("#");
            }
            if (!receiptStr.isEmpty()) receiptStr.deleteCharAt(receiptStr.length()-1);
            return name + "," + uuid.toString() + "," + receiptStr;
        }else return name + "," + uuid.toString();
    }

    public static PlayerRecord fromString(String str){
        String[] split = str.split(",");
        List<Receipt> receipts = new ArrayList<>();
        if (split.length > 2) {
            String[] receiptStrs = split[2].split("#");
            int bound = Math.min(9, receiptStrs.length);
            for (int i = 0; i < bound; i++){
                receipts.add(Receipt.fromString(receiptStrs[i]));
            }
        }
        return new PlayerRecord(split[0], UUID.fromString(split[1]), receipts);
    }
}
