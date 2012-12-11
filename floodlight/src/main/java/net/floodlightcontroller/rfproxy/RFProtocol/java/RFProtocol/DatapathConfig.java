package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class DatapathConfig extends IPCMessage implements fields, messagesTypes {
    public DatapathConfig() {};

    public DatapathConfig(long dp_id, int operation_id) {
        set_dp_id(dp_id);
        set_operation_id(operation_id);
    };

    public long get_dp_id() {
        return this.dp_id;
    };

    public void set_dp_id(long dp_id) {
        this.dp_id = dp_id;
    };

    public int get_operation_id() {
        return this.operation_id;
    };

    public void set_operation_id(int operation_id) {
        this.operation_id = operation_id;
    };

    public int get_type() {
        return messagesTypes.DatapathConfig;
    };

    public DBObject to_DBObject() {
        DBObject data = new BasicDBObject();

        data.put("dp_id", String.valueOf(get_dp_id()));
        data.put("operation_id", String.valueOf(get_operation_id()));

        return data;
    };

    public void from_DBObject(DBObject data) {
        DBObject content = (DBObject) data.get(fields.CONTENT_FIELD);

        this.set_dp_id(Long.parseLong(content.get("dp_id").toString()));
        this.set_operation_id(Integer.parseInt(content.get("operation_id").toString()));
    };

    public String str() {
        String message;

        message = "DatapathConfig" + "\n dp_id: " + String.valueOf(get_dp_id()) + "\n operation_id: " + String.valueOf(get_operation_id()) + "\n";

        return message;
    };

    private long dp_id;
    private int operation_id;

}