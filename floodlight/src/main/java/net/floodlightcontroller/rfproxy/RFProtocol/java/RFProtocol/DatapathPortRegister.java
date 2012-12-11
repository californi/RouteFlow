package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class DatapathPortRegister extends IPCMessage implements fields, messagesTypes {
    public DatapathPortRegister() {};

    public DatapathPortRegister(long dp_id, int dp_port) {
        set_dp_id(dp_id);
        set_dp_port(dp_port);
    };

    public long get_dp_id() {
        return this.dp_id;
    };

    public void set_dp_id(long dp_id) {
        this.dp_id = dp_id;
    };

    public int get_dp_port() {
        return this.dp_port;
    };

    public void set_dp_port(int dp_port) {
        this.dp_port = dp_port;
    };

    public int get_type() {
        return messagesTypes.DatapathPortRegister;
    };

    public DBObject to_DBObject() {
        DBObject data = new BasicDBObject();

        data.put("dp_id", String.valueOf(get_dp_id()));
        data.put("dp_port", String.valueOf(get_dp_port()));

        return data;
    };

    public void from_DBObject(DBObject data) {
        DBObject content = (DBObject) data.get(fields.CONTENT_FIELD);

        this.set_dp_id(Long.parseLong(content.get("dp_id").toString()));
        this.set_dp_port(Integer.parseInt(content.get("dp_port").toString()));
    };

    public String str() {
        String message;

        message = "DatapathPortRegister" + "\n dp_id: " + String.valueOf(get_dp_id()) + "\n dp_port: " + String.valueOf(get_dp_port()) + "\n";

        return message;
    };

    private long dp_id;
    private int dp_port;

}