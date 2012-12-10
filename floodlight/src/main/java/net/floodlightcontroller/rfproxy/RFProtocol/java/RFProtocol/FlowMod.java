package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class FlowMod extends IPCMessage implements fields, messagesTypes {
    public FlowMod() {};

    public FlowMod(long dp_id, String address, String netmask, int dst_port, String src_hwaddress, String dst_hwaddress, boolean is_removal) {
        set_dp_id(dp_id);
        set_address(address);
        set_netmask(netmask);
        set_dst_port(dst_port);
        set_src_hwaddress(src_hwaddress);
        set_dst_hwaddress(dst_hwaddress);
        set_is_removal(is_removal);
    };

    public long get_dp_id() {
        return this.dp_id;
    };

    public void set_dp_id(long dp_id) {
        this.dp_id = dp_id;
    };

    public String get_address() {
        return this.address;
    };

    public void set_address(String address) {
        this.address = address;
    };

    public String get_netmask() {
        return this.netmask;
    };

    public void set_netmask(String netmask) {
        this.netmask = netmask;
    };

    public int get_dst_port() {
        return this.dst_port;
    };

    public void set_dst_port(int dst_port) {
        this.dst_port = dst_port;
    };

    public String get_src_hwaddress() {
        return this.src_hwaddress;
    };

    public void set_src_hwaddress(String src_hwaddress) {
        this.src_hwaddress = src_hwaddress;
    };

    public String get_dst_hwaddress() {
        return this.dst_hwaddress;
    };

    public void set_dst_hwaddress(String dst_hwaddress) {
        this.dst_hwaddress = dst_hwaddress;
    };

    public boolean get_is_removal() {
        return this.is_removal;
    };

    public void set_is_removal(boolean is_removal) {
        this.is_removal = is_removal;
    };

    public int get_type() {
        return messagesTypes.FlowMod;
    };

    public DBObject to_DBObject() {
        DBObject data = new BasicDBObject();

        data.put("dp_id", String.valueOf(get_dp_id()));
        data.put("address", String.valueOf(get_address()));
        data.put("netmask", String.valueOf(get_netmask()));
        data.put("dst_port", String.valueOf(get_dst_port()));
        data.put("src_hwaddress", String.valueOf(get_src_hwaddress()));
        data.put("dst_hwaddress", String.valueOf(get_dst_hwaddress()));
        data.put("is_removal", get_is_removal());

        return data;
    };

    public void from_DBObject(DBObject data) {
        DBObject content = (DBObject) data.get(fields.CONTENT_FIELD);

        this.set_dp_id(Long.parseLong(content.get("dp_id").toString()));
        this.set_address(content.get("address").toString());
        this.set_netmask(content.get("netmask").toString());
        this.set_dst_port(Integer.parseInt(content.get("dst_port").toString()));
        this.set_src_hwaddress(content.get("src_hwaddress").toString());
        this.set_dst_hwaddress(content.get("dst_hwaddress").toString());
        this.set_is_removal(Boolean.parseBoolean(content.get("is_removal").toString()));
    };

    public String str() {
        String message;

        message = "FlowMod" + "\n dp_id: " + String.valueOf(get_dp_id()) + "\n address: " + String.valueOf(get_address()) + "\n netmask: " + String.valueOf(get_netmask()) + "\n dst_port: " + String.valueOf(get_dst_port()) + "\n src_hwaddress: " + String.valueOf(get_src_hwaddress()) + "\n dst_hwaddress: " + String.valueOf(get_dst_hwaddress()) + "\n is_removal: " + String.valueOf(get_is_removal()) + "\n";

        return message;
    };

    private long dp_id;
    private String address;
    private String netmask;
    private int dst_port;
    private String src_hwaddress;
    private String dst_hwaddress;
    private boolean is_removal;

}