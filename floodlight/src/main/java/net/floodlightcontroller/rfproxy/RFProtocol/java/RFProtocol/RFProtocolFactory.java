package net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageFactory;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;

public class RFProtocolFactory extends IPCMessageFactory implements messagesTypes{

	@Override
	public IPCMessage buildForType(int type) {
		if(type == messagesTypes.DatapathConfig){
			return new DatapathConfig();
		}
		if(type == messagesTypes.DatapathDown){
			return new DatapathDown();
		}
		if(type == messagesTypes.DatapathPortRegister){
			return new DatapathPortRegister();
		}
		if(type == messagesTypes.DataPlaneMap){
			return new DataPlaneMap();
		}
		if(type == messagesTypes.FlowMod){
			return new FlowMod();
		}
		if(type == messagesTypes.PortConfig){
			return new PortConfig();
		}
		if(type == messagesTypes.PortRegister){
			return new PortRegister();
		}
		if(type == messagesTypes.RouteInfo){
			return new RouteInfo();
		}
		if(type == messagesTypes.VirtualPlaneMap){
			return new VirtualPlaneMap();
		}
		return null;
	}

}


