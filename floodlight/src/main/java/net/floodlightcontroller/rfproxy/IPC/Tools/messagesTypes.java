package net.floodlightcontroller.rfproxy.IPC.Tools;

public interface messagesTypes {
	public static final int PortRegister = 0;
	public static final int PortConfig = 1;
	public static final int DatapathConfig = 2;
	public static final int RouteInfo = 3;
	public static final int FlowMod = 4;	
	public static final int DatapathPortRegister = 5;	
	public static final int DatapathDown = 6;
	public static final int VirtualPlaneMap = 7;
	public static final int DataPlaneMap = 8;
}
