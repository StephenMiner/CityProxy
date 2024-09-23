package me.stephenminer.cityproxy.records;


public record Receipt(String merchant, double paid, String reason, long timestamp) {


    public String toString(){
        return merchant + "/" + paid + "/" + reason + "/" + timestamp;
    }
    public static Receipt fromString(String str){
        String[] data = str.split("/");
        String merchant = data[0];
        double paid = Double.parseDouble(data[1]);
        String reason = data[2];
        long timeStamp = Long.parseLong(data[3]);
        return new Receipt(merchant, paid, reason, timeStamp);
    }
}
