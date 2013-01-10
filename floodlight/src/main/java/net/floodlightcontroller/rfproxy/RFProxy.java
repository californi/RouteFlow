package net.floodlightcontroller.rfproxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionOutput;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageProcessor;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageService;
import net.floodlightcontroller.rfproxy.IPC.MongoIPC.MongoIPCMessageService;
import net.floodlightcontroller.rfproxy.IPC.Tools.AssociationTable;
import net.floodlightcontroller.rfproxy.IPC.Tools.DP;
import net.floodlightcontroller.rfproxy.IPC.Tools.VS;
import net.floodlightcontroller.rfproxy.IPC.Tools.defs;
import net.floodlightcontroller.rfproxy.IPC.Tools.messagesTypes;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.DataPlaneMap;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.DatapathConfig;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.DatapathDown;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.DatapathPortRegister;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.FlowMod;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.RFProtocolFactory;
import net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.VirtualPlaneMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Main Class. */
public class RFProxy implements IOFMessageListener, IFloodlightModule,
		IOFSwitchListener, defs {

	/* Service Provider. */
	protected IFloodlightProviderService floodlightProvider;

	/* Log Service. */
	protected static Logger logger;

	/* Association Table. */
	AssociationTable table = new AssociationTable();

	/* IPC's Definition. */
	private String address = defs.MONGO_ADDRESS;
	private String database_name = defs.MONGO_DB_NAME;
	private String consumer_id = defs.RFPROXY_ID;
	private String listen_queue = defs.RFSERVER_RFPROXY_CHANNEL;

	/* IPC's Creation. */
	private MongoIPCMessageService ipc = new MongoIPCMessageService(
			this.address, this.database_name, this.consumer_id);

	/* Message Factory. */
	private RFProtocolFactory factory = new RFProtocolFactory();

	/* Message In Processor. */
	private RFProtocolProcessor processor = new RFProtocolProcessor(this.ipc);

	/* Flow Config Function. */
	public void flowConfig(Long dp_id, int operation_id) {
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider
				.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		long cookie = AppCookie.makeCookie(123, 0);
		int length = OFFlowMod.MINIMUM_LENGTH;

		List<OFAction> actions = new ArrayList<OFAction>();

		OFMatch match = new OFMatch();
		if (operation_id == defs.DC_RIPV2) {
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_UDP);
			match.setNetworkDestination(IPv4.toIPv4Address("224.0.0.9"));
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_NW_DST_MASK);
		} else if (operation_id == defs.DC_OSPF) {
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(defs.IPPROTO_OSPF);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_NW_PROTO);
		} else if (operation_id == defs.DC_ARP) {
			match.setDataLayerType(defs.ETHERTYPE_ARP);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE);
		} else if (operation_id == defs.DC_ICMP) {
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_ICMP);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_NW_PROTO);
		} else if (operation_id == defs.DC_BGP_INBOUND) {
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
			match.setTransportDestination(defs.IPORT_BGP);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST);
		} else if (operation_id == defs.DC_BGP_OUTBOUND) {
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
			match.setTransportSource(defs.IPORT_BGP);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
					& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_SRC);
		} else if (operation_id == defs.DC_VM_INFO) {
			match.setDataLayerType((short) defs.RF_ETH_PROTO);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE);
		} else if (operation_id == defs.DC_DROP_ALL) {
			flowMod.setPriority((short) 1);
		}

		if (operation_id == defs.DC_CLEAR_FLOW_TABLE) {
			flowMod.setCommand(OFFlowMod.OFPFC_DELETE);
			flowMod.setPriority((short) 0);
		} else if (operation_id == defs.DC_DROP_ALL) {
			// Do nothing: no match, no actions = drop
		} else {
			actions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue(),
					OFPort.OFPP_NONE.getValue()));
			length += OFActionOutput.MINIMUM_LENGTH;
			flowMod.setCommand(OFFlowMod.OFPFC_ADD);
		}

		((OFFlowMod) flowMod.setIdleTimeout((short) 0)
				.setHardTimeout((short) 0).setBufferId(
						OFPacketOut.BUFFER_ID_NONE).setCookie(cookie)
				.setLengthU(length)).setMatch(match).setActions(actions);

		/* Get switch by DPID */
		IOFSwitch sw = floodlightProvider.getSwitches().get(dp_id);
		/* Send the Flow mod */
		if ((sw != null) && sw.isConnected()) {
			try {
				sw.write(flowMod, null);
				sw.flush();
				logger.info(
						"ofp_flow_mod(config) was sent to datapath (dp_id={})",
						dp_id);
			} catch (IOException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(config) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			} catch (IndexOutOfBoundsException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(config) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			}
		}
	}

	/* Flow Delete Function. */
	public void flowDelete(Long dp_id, String address, String net_mask,
			String src_hwaddress) {

		OFFlowMod flowMod = (OFFlowMod) floodlightProvider
				.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch match = new OFMatch();

		match.setDataLayerType(Ethernet.TYPE_IPv4);

		if (defs.MATCH_L2) {
			match.setDataLayerSource(src_hwaddress);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC);
		}

		/* It is not working. */
		/* All rules with IP Destination or IP Source does not work. */
		match.setNetworkDestination(IPv4.toIPv4Address(address));

		match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
				& ~OFMatch.OFPFW_NW_DST_MASK);

		/* Default Priority. */
		flowMod.setPriority(defs.DEFAULT_PRIORITY);

		/* Set command. */
		flowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);

		/* Get switch by DPID */
		IOFSwitch sw = floodlightProvider.getSwitches().get(dp_id);

		flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);

		/* Send the Flow mod */
		if ((sw != null) && sw.isConnected()) {

			flowMod.setMatch(match);

			try {
				sw.write(flowMod, null);
				sw.flush();
				logger.info(
						"ofp_flow_mod(delete) was sent to datapath (dp_id={})",
						dp_id);
			} catch (IOException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(delete) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			} catch (IndexOutOfBoundsException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(config) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			}
		}

	}

	/* Flow Add Function. */
	public void flowAdd(Long dp_id, String address, String netmask,
			String src_hwaddress, String dst_hwaddress, int dst_port) {

		OFFlowMod flowMod = (OFFlowMod) floodlightProvider
				.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		int length = OFFlowMod.MINIMUM_LENGTH;

		OFMatch match = new OFMatch();
		match.setDataLayerType(Ethernet.TYPE_IPv4);

		if (defs.MATCH_L2) {
			match.setDataLayerSource(src_hwaddress);
			match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC);
		}

		/* It is not working. */
		/* All rules with IP Destination or IP Source does not work. */
		match.setNetworkDestination(IPv4.toIPv4Address(address));
		match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_TYPE
				& ~OFMatch.OFPFW_NW_DST_MASK);

		long cookie = AppCookie.makeCookie(123, 0);

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionDataLayerSource(Ethernet
				.toMACAddress(src_hwaddress)));
		length += OFActionDataLayerSource.MINIMUM_LENGTH;
		actions.add(new OFActionDataLayerDestination(Ethernet
				.toMACAddress(dst_hwaddress)));
		length += OFActionDataLayerDestination.MINIMUM_LENGTH;
		actions.add(new OFActionOutput().setPort((short) dst_port));
		length += OFActionOutput.MINIMUM_LENGTH;

		((OFFlowMod) flowMod.setIdleTimeout((short) 0)
				.setHardTimeout((short) 0).setBufferId(
						OFPacketOut.BUFFER_ID_NONE).setCookie(cookie)
				.setCommand(OFFlowMod.OFPFC_ADD).setMatch(match).setActions(
						actions).setLengthU(length))
				.setOutPort((short) dst_port);

		flowMod.setMatch(match);

		/* Get switch by DPID */
		IOFSwitch sw = floodlightProvider.getSwitches().get(dp_id);

		/* Send the Flow mod */
		if ((sw != null) && sw.isConnected()) {
			try {
				sw.write(flowMod, null);
				sw.flush();
				logger.info(
						"ofp_flow_mod(add) was sent to datapath (dp_id={})",
						dp_id);
			} catch (IOException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(add) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			} catch (IndexOutOfBoundsException e) {
				logger
						.info(
								"Error sending ofp_flow_mod(add) to datapath (dp_id={})",
								dp_id);
				e.printStackTrace();
			}
		}

	}

	/* Send Packets to Switches. */
	private void writePacketOutForPacketIn(IOFSwitch sw,
			OFPacketIn packetInMessage, short egressPort, FloodlightContext cntx) {

		OFPacketOut packetOutMessage = (OFPacketOut) floodlightProvider
				.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		short packetOutLength = (short) OFPacketOut.MINIMUM_LENGTH;

		packetOutMessage
				.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		packetOutLength += OFActionOutput.MINIMUM_LENGTH;

		/* Set Actions. */
		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(new OFActionOutput(egressPort, (short) 0));

		packetOutMessage.setActions(actions);

		byte[] packetData = packetInMessage.getPacketData();
		packetOutMessage.setPacketData(packetData);
		packetOutLength += (short) packetData.length;

		packetOutMessage.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		packetOutMessage.setLength(packetOutLength);

		try {
			sw.write(packetOutMessage, null);
		} catch (IOException e) {
			logger.error("Failed to write {} to switch {}: {}", new Object[] {
					packetOutMessage, sw, e });
		} catch (IndexOutOfBoundsException e) {
			logger.info("Error On Packet Send");
			e.printStackTrace();
		}
	}

	/* RFProcessor. */
	public class RFProtocolProcessor extends IPCMessageProcessor implements
			messagesTypes {
		@SuppressWarnings("unused")
		private IPCMessageService ipc;

		public RFProtocolProcessor(IPCMessageService ipc) {
			this.ipc = ipc;
			logger = LoggerFactory.getLogger(RFProtocolProcessor.class);
		}

		@Override
		public boolean process(String from, String to, String channel,
				IPCMessage msg) {

			int type = msg.get_type();

			/* DatapathConfig Message. */
			if (type == messagesTypes.DatapathConfig) {
				DatapathConfig messageDatapathConfig = (net.floodlightcontroller.rfproxy.RFProtocol.java.RFProtocol.DatapathConfig) msg;
				flowConfig(messageDatapathConfig.get_dp_id(),
						messageDatapathConfig.get_operation_id());
			}

			/* FlowMod Message */
			else if (type == messagesTypes.FlowMod) {

				FlowMod messageFlowMod = (FlowMod) msg;

				if (messageFlowMod.get_is_removal()) {
					flowDelete(messageFlowMod.get_dp_id(), messageFlowMod
							.get_address(), messageFlowMod.get_netmask(),
							messageFlowMod.get_src_hwaddress());
				} else {

					flowAdd(messageFlowMod.get_dp_id(), messageFlowMod
							.get_address(), messageFlowMod.get_netmask(),
							messageFlowMod.get_dst_hwaddress(), messageFlowMod
									.get_dst_hwaddress(), messageFlowMod
									.get_dst_port());
				}
			}

			/* DataPlaneMap Messages. */
			else if (type == messagesTypes.DataPlaneMap) {
				logger.info("DataPlaneMap!");
				DataPlaneMap messageDataPlaneMap = (DataPlaneMap) msg;

				/* Table Update. */
				DP dp_in = new DP(messageDataPlaneMap.get_dp_id(),
						messageDataPlaneMap.get_dp_port());
				VS vs_in = new VS(messageDataPlaneMap.get_vs_id(),
						messageDataPlaneMap.get_vs_port());

				table.update_dp_port(dp_in, vs_in);

			}

			return false;
		}

	}

	/*******************************************************************/
	/** Functions needed only for the interface */
	/*******************************************************************/
	@Override
	public String getName() {
		return "RFProxy";
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);

		return l;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	/*******************************************************************/
	/*******************************************************************/

	/* System Initialization. */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(RFProxy.class);

	}

	/* Function to process Packet In Messages. */
	private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
			FloodlightContext cntx) {
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		Short ethernetType = match.getDataLayerType();

		/* Drop all LLDP packets */
		if (ethernetType == Ethernet.TYPE_LLDP) {
			logger.info("Drop LLDP Packet");
			return Command.CONTINUE;
		}

		/* If we have a mapping packet, inform RFServer through a Map message */
		if (ethernetType == defs.RF_ETH_PROTO) {

			/* Byte extraction */
			byte[] data = pi.getPacketData();
			byte[] buf = new byte[8];

			buf[0] = data[14];
			buf[1] = data[15];
			buf[2] = data[16];
			buf[3] = data[17];
			buf[4] = data[18];
			buf[5] = data[19];
			buf[6] = data[20];
			buf[7] = data[21];

			ByteBuffer bb = ByteBuffer.wrap(buf);

			bb.order(ByteOrder.LITTLE_ENDIAN);

			long vm_id = bb.getLong();
			int vm_port = data[22];

			String out = "vm_id=" + Long.toString(vm_id) + " vm_port="
					+ Integer.toString(vm_port) + " vs_id="
					+ Long.toString(sw.getId()) + " vs_port="
					+ Short.toString(pi.getInPort());

			logger.info("Received mapping packet ({})", out);

			VirtualPlaneMap msg = new VirtualPlaneMap(vm_id, vm_port, sw
					.getId(), (int) pi.getInPort());

			this.ipc.send(defs.RFSERVER_RFPROXY_CHANNEL, defs.RFSERVER_ID, msg);

			return Command.CONTINUE;
		}

		/* If the packet came from RFVS, redirect it to the right switch port */
		if (sw.getId() == defs.RFVS_DPID) {

			VS vs_RFSV = new VS(sw.getId(), (int) pi.getInPort());

			DP dp_RFSV = table.vs_port_to_dp_port(vs_RFSV);

			if (dp_RFSV != null) {

				/* Get switch by DPID */
				IOFSwitch sw_out = floodlightProvider.getSwitches().get(
						dp_RFSV.getDp_id());

				if (sw_out != null) {
					this.writePacketOutForPacketIn(sw_out, pi, (short) dp_RFSV
							.getDp_port(), cntx);
				}

				/*
				 * logger.info("Send to (dp_id={}, dp_port={})", dp_RFSV
				 * .getDp_id(), dp_RFSV.getDp_port());
				 */

			} else {

				// logger.info("Unmapped RFVS port (vs_id={}, vs_port={})",
				// vs_RFSV.getVs_id(), vs_RFSV.getVs_port());

			}

		} else {

			DP dp_RFVS = new DP(sw.getId(), pi.getInPort());

			VS vs_RFSV = table.dp_port_to_vs_port(dp_RFVS);

			if (vs_RFSV != null) {

				/* Get switch by DPID */
				IOFSwitch sw_out = floodlightProvider.getSwitches().get(
						vs_RFSV.getVs_id());

				if (sw_out != null) {
					this.writePacketOutForPacketIn(sw_out, pi, (short) vs_RFSV
							.getVs_port(), cntx);
				}

				/*
				 * logger.info("Send to else (dp_id={}, dp_port={})", vs_RFSV
				 * .getVs_id(), vs_RFSV.getVs_port());
				 */
			}

		}

		return Command.CONTINUE;
	}

	/* System Startup. */
	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFSwitchListener(this);

		logger.info("RFProxy Sendo Ativado!");

		/* Listen For Messages. */
		this.ipc.listen(this.listen_queue, this.factory, this.processor, false);

	}

	/* Listener. */
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if (msg.getType() == OFType.PACKET_IN) {
			return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
		}
		return Command.CONTINUE;
	}

	/* Datapath Up. */
	@SuppressWarnings("unchecked")
	@Override
	public void addedSwitch(IOFSwitch sw) {
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		/* Get dp id */
		Long dp_id = sw.getId();
		String dp_id_string = sw.getStringId();

		/* Get switch's ports */
		Collection<OFPhysicalPort> ports = sw.getPorts();

		Iterator iterator = ports.iterator();

		OFPhysicalPort port;

		/* Register the ports */
		while (iterator.hasNext()) {

			port = (OFPhysicalPort) iterator.next();

			if (port.getPortNumber() > 0) {
				int port_id = port.getPortNumber();

				logger.info("Registering datapath port (dp_id={}, dp_port={})",
						dp_id_string, port_id);

				DatapathPortRegister msg = new DatapathPortRegister(dp_id,
						port_id);

				this.ipc.send(defs.RFSERVER_RFPROXY_CHANNEL, defs.RFSERVER_ID,
						msg);
			}
		}
	}

	/* Datapath Down. */
	@Override
	public void removedSwitch(IOFSwitch sw) {
		/* Get dp id */
		Long dp_id = sw.getId();
		String dp_id_string = sw.getStringId();

		logger.info("Datapath is down (dp_id={})", dp_id_string);

		this.table.delete_dp(dp_id);

		DatapathDown msg = new DatapathDown(dp_id);

		this.ipc.send(defs.RFSERVER_RFPROXY_CHANNEL, defs.RFSERVER_ID, msg);

	}

	@Override
	public void switchPortChanged(Long switchId) {
	}

}
