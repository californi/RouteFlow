package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class PortRegister extends IPCMessage implements fields, messagesTypes {
    public PortRegister() {};

    public PortRegister(int vm_id, int vm_port) {
        set_vm_id(vm_id);
        set_vm_port(vm_port);
    };

    public int get_vm_id() {
        return this.vm_id;
    };

    public void set_vm_id(int vm_id) {
        this.vm_id = vm_id;
    };

    public int get_vm_port() {
        return this.vm_port;
    };

    public void set_vm_port(int vm_port) {
        this.vm_port = vm_port;
    };

    public int get_type() {
        return messagesTypes.PortRegister;
    };

    public DBObject to_DBObject() {
        DBObject data = new BasicDBObject();

        data.put("vm_id", String.valueOf(get_vm_id()));
        data.put("vm_port", String.valueOf(get_vm_port()));

        return data;
    };

    public void from_DBObject(DBObject data) {
        DBObject content = (DBObject) data.get(fields.CONTENT_FIELD);

        this.set_vm_id(Integer.parseInt(content.get("vm_id").toString()));
        this.set_vm_port(Integer.parseInt(content.get("vm_port").toString()));
    };

    public String str() {
        String message;

        message = "PortRegister" + "\n vm_id: " + String.valueOf(get_vm_id()) + "\n vm_port: " + String.valueOf(get_vm_port()) + "\n";

        return message;
    };

    private int vm_id;
    private int vm_port;

}