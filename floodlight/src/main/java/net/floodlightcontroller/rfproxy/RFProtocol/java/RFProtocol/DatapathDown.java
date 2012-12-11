package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class DatapathDown extends IPCMessage implements fields, messagesTypes {
    public DatapathDown() {};

    public DatapathDown(long dp_id) {
        set_dp_id(dp_id);
    };

    public long get_dp_id() {
        return this.dp_id;
    };

    public void set_dp_id(long dp_id) {
        this.dp_id = dp_id;
    };

    public int get_type() {
        return messagesTypes.DatapathDown;
    };

    public DBObject to_DBObject() {
        DBObject data = new BasicDBObject();

        data.put("dp_id", String.valueOf(get_dp_id()));

        return data;
    };

    public void from_DBObject(DBObject data) {
        DBObject content = (DBObject) data.get(fields.CONTENT_FIELD);

        this.set_dp_id(Long.parseLong(content.get("dp_id").toString()));
    };

    public String str() {
        String message;

        message = "DatapathDown" + "\n dp_id: " + String.valueOf(get_dp_id()) + "\n";

        return message;
    };

    private long dp_id;

}