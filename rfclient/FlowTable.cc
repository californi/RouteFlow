#include <stdio.h>
#include <arpa/inet.h>
#include <netinet/ether.h>
#include <netdb.h>
#include <sys/socket.h>
#include <time.h>

#include <string>
#include <vector>
#include <cstring>
#include <iostream>

#include "ipc/RFProtocol.h"
#include "converter.h"
#include "FlowTable.h"

using namespace std;

#define FULL_IPV4_MASK ((in_addr){ 0xffffffff })
#define FULL_CIDR_MASK 32
#define FULL_IPV6_MASK ((in6_addr){{{ 0xff, 0xff, 0xff, 0xff, \
                                      0xff, 0xff, 0xff, 0xff, \
                                      0xff, 0xff, 0xff, 0xff, \
                                      0xff, 0xff, 0xff, 0xff }}})
#define EMPTY_MAC_ADDRESS "00:00:00:00:00:00"

const MACAddress FlowTable::MAC_ADDR_NONE(EMPTY_MAC_ADDRESS);

int FlowTable::family = AF_UNSPEC;
unsigned FlowTable::groups = ~0U;
int FlowTable::llink = 0;
int FlowTable::laddr = 0;
int FlowTable::lroute = 0;

struct rtnl_handle FlowTable::rthNeigh;
boost::thread FlowTable::HTPolling;
struct rtnl_handle FlowTable::rth;
boost::thread FlowTable::RTPolling;

map<string, Interface> FlowTable::interfaces;
vector<uint32_t>* FlowTable::down_ports;
IPCMessageService* FlowTable::ipc;
uint64_t FlowTable::vm_id;

list<RouteEntry> FlowTable::routeTable;
list<HostEntry> FlowTable::hostTable;

// TODO: implement a way to pause the flow table updates when the VM is not
//       associated with a valid datapath

void FlowTable::HTPollingCb() {
    rtnl_listen(&rthNeigh, FlowTable::updateHostTable, NULL);
}

void FlowTable::RTPollingCb() {
    rtnl_listen(&rth, FlowTable::updateRouteTable, NULL);
}

void FlowTable::clear() {
    FlowTable::routeTable.clear();
    FlowTable::hostTable.clear();
}

void FlowTable::start(uint64_t vm_id, map<string, Interface> interfaces,
                      IPCMessageService* ipc, vector<uint32_t>* down_ports) {
    FlowTable::vm_id = vm_id;
    FlowTable::interfaces = interfaces;
    FlowTable::ipc = ipc;
    FlowTable::down_ports = down_ports;

    rtnl_open(&rth, RTMGRP_IPV4_MROUTE | RTMGRP_IPV4_ROUTE
                  | RTMGRP_IPV6_MROUTE | RTMGRP_IPV6_ROUTE);
    rtnl_open(&rthNeigh, RTMGRP_NEIGH);

    HTPolling = boost::thread(&FlowTable::HTPollingCb);
    RTPolling = boost::thread(&FlowTable::RTPollingCb);
    HTPolling.detach();
    RTPolling.detach();
}

int FlowTable::updateHostTable(const struct sockaddr_nl *, struct nlmsghdr *n, void *) {
	struct ndmsg *ndmsg_ptr = (struct ndmsg *) NLMSG_DATA(n);
	struct rtattr *rtattr_ptr;

	char intf[IF_NAMESIZE + 1];
	memset(intf, 0, IF_NAMESIZE + 1);

	if (if_indextoname((unsigned int) ndmsg_ptr->ndm_ifindex, (char *) intf) == NULL) {
		perror("HostTable");
		return 0;
	}

    /*
	if (ndmsg_ptr->ndm_state != NUD_REACHABLE) {
	    cout << "ndm_state: " << (uint16_t) ndmsg_ptr->ndm_state << endl;
		return 0;
	}
	*/

	char ip[INET_ADDRSTRLEN];
	char mac[2 * IFHWADDRLEN + 5 + 1];

	memset(ip, 0, INET_ADDRSTRLEN);
	memset(mac, 0, 2 * IFHWADDRLEN + 5 + 1);

	rtattr_ptr = (struct rtattr *) RTM_RTA(ndmsg_ptr);
	int rtmsg_len = RTM_PAYLOAD(n);

	for (; RTA_OK(rtattr_ptr, rtmsg_len); rtattr_ptr = RTA_NEXT(rtattr_ptr, rtmsg_len)) {
		switch (rtattr_ptr->rta_type) {
		case RTA_DST:
			if (inet_ntop(AF_INET, RTA_DATA(rtattr_ptr), ip, 128) == NULL) {
				perror("HostTable");
				return 0;
			}
			break;
		case NDA_LLADDR:
			if (strncpy(mac, ether_ntoa(((ether_addr *) RTA_DATA(rtattr_ptr))), sizeof(mac)) == NULL) {
				perror("HostTable");
				return 0;
			}
			break;
		default:
			break;
		}
	}

	HostEntry hentry;
	map<string, Interface>::iterator it;

	hentry.address = IPAddress(IPV4, ip);
	hentry.hwaddress = MACAddress(mac);

	it = interfaces.find(intf);
	if (it != interfaces.end())
		hentry.interface = it->second;
	if (not hentry.interface.active) {
		fprintf(stderr, "Interface inactive. Dropping Host Entry\n");
		return 0;
	}

	switch (n->nlmsg_type) {
	    case RTM_NEWNEIGH:
		    std::cout << "netlink->RTM_NEWNEIGH: ip=" << ip << ", mac=" << mac << std::endl;
#ifdef ROUTEMOD_ENABLED
		    FlowTable::sendToHw(RMT_ADD, hentry);
#else
		    FlowTable::addFlowToHw(hentry);
#endif
		    // TODO: Shouldn't we check for a duplicate?
		    FlowTable::hostTable.push_back(hentry);
		    break;
	    /* TODO: enable this? It is causing serious problems. Why?
	    case RTM_DELNEIGH:
		    std::cout << "netlink->RTM_DELNEIGH: ip=" << ip << ", mac=" << mac << std::endl;
#ifdef ROUTEMOD_ENABLED
		    FlowTable::sendToHw(RMT_DELETE, hentry);
#else
		    FlowTable::delFlowFromHw(hentry);
#endif
		    // TODO: delete from hostTable
		    break;
	    */
	}

	return 0;
}

int FlowTable::updateRouteTable(const struct sockaddr_nl *, struct nlmsghdr *n, void *) {
	struct rtmsg *rtmsg_ptr = (struct rtmsg *) NLMSG_DATA(n);

	if (!((n->nlmsg_type == RTM_NEWROUTE || n->nlmsg_type == RTM_DELROUTE) && rtmsg_ptr->rtm_table == RT_TABLE_MAIN)) {
		return 0;
	}

	char net[INET_ADDRSTRLEN];
	char gw[INET_ADDRSTRLEN];
	char intf[IF_NAMESIZE + 1];

	memset(net, 0, INET_ADDRSTRLEN);
	memset(gw, 0, INET_ADDRSTRLEN);
	memset(intf, 0, IF_NAMESIZE + 1);

	struct rtattr *rtattr_ptr;
	rtattr_ptr = (struct rtattr *) RTM_RTA(rtmsg_ptr);
	int rtmsg_len = RTM_PAYLOAD(n);

	for (; RTA_OK(rtattr_ptr, rtmsg_len); rtattr_ptr = RTA_NEXT(rtattr_ptr, rtmsg_len)) {
		switch (rtattr_ptr->rta_type) {
		case RTA_DST:
			inet_ntop(AF_INET, RTA_DATA(rtattr_ptr), net, 128);
			break;
		case RTA_GATEWAY:
			inet_ntop(AF_INET, RTA_DATA(rtattr_ptr), gw, 128);
			break;
		case RTA_OIF:
			if_indextoname(*((int *) RTA_DATA(rtattr_ptr)), (char *) intf);
			break;
		case RTA_MULTIPATH: {
			struct rtnexthop *rtnhp_ptr = (struct rtnexthop *) RTA_DATA(
					rtattr_ptr);
			int rtnhp_len = RTA_PAYLOAD(rtattr_ptr);

			if (rtnhp_len < (int) sizeof(*rtnhp_ptr)) {
				break;
			}

			if (rtnhp_ptr->rtnh_len > rtnhp_len) {
				break;
			}

			if_indextoname(rtnhp_ptr->rtnh_ifindex, (char *) intf);

			int attrlen = rtnhp_len - sizeof(struct rtnexthop);

			if (attrlen) {
				struct rtattr *attr = RTNH_DATA(rtnhp_ptr);

				for (; RTA_OK(attr, attrlen); attr = RTA_NEXT(attr, attrlen))
					if ((attr->rta_type == RTA_GATEWAY)) {
						inet_ntop(AF_INET, RTA_DATA(attr), gw, 128);
						break;
					}
			}
		}
			break;
		default:
			break;
		}
	}

	/* Skipping routes to directly attached networks (next-hop field is blank) */
	{
		struct in_addr gwAddr;
		if (inet_aton(gw, &gwAddr) == 0) {
			fprintf(stderr, "Blank next-hop field. Dropping Route\n");
			return 0;
		}
	}

	struct in_addr convmask;
	convmask.s_addr = htonl(~((1 << (32 - rtmsg_ptr->rtm_dst_len)) - 1));
	char mask[INET_ADDRSTRLEN];
	snprintf(mask, sizeof(mask), "%s", inet_ntoa(convmask));

	RouteEntry rentry;
	map<string, Interface>::iterator it;
	list<RouteEntry>::iterator itRoutes;

	switch (n->nlmsg_type) {
	case RTM_NEWROUTE:
		std::cout << "netlink->RTM_NEWROUTE: net=" << net << ", mask=" << mask << ", gw=" << gw << std::endl;

		// Discard if there's no gateway
		if (inet_addr(gw) == INADDR_NONE) {
			fprintf(stderr, "No gateway specified. Dropping Route\n");
			return 0;
		}

		rentry.address = IPAddress(IPV4, net);
		rentry.gateway = IPAddress(IPV4, gw);
		rentry.netmask = IPAddress(IPV4, mask);

		it = interfaces.find(intf);
		if (it != interfaces.end())
			rentry.interface = it->second;

		if (not rentry.interface.active) {
			fprintf(stderr, "Interface inactive. Dropping NEWROUTE\n");
			return 0;
		}

		for (itRoutes = FlowTable::routeTable.begin(); itRoutes != FlowTable::routeTable.end(); itRoutes++) {
			if (rentry == (*itRoutes)) {
				std::cout << "Duplicate route add request.\n" << "\n";
				return 0;
			}
		}

#ifdef ROUTEMOD_ENABLED
		FlowTable::sendToHw(RMT_ADD, rentry);
#else
		FlowTable::addFlowToHw(rentry);
#endif
		FlowTable::routeTable.push_back(rentry);
		break;
	case RTM_DELROUTE:
		std::cout << "netlink->RTM_DELROUTE: net=" << net << ", mask=" << mask << ", gw=" << gw << std::endl;

		rentry.address = IPAddress(IPV4, net);
		rentry.gateway = IPAddress(IPV4, gw);
		rentry.netmask = IPAddress(IPV4, mask);

		it = interfaces.find(intf);
		if (it != interfaces.end())
			rentry.interface = it->second;

		if (not rentry.interface.active) {
			fprintf(stderr, "Interface inactive. Dropping DELROUTE\n");
			return 0;
		}

		for (itRoutes = FlowTable::routeTable.begin(); itRoutes != FlowTable::routeTable.end(); itRoutes++) {
			if (rentry == (*itRoutes)) {
#ifdef ROUTEMOD_ENABLED
				FlowTable::sendToHw(RMT_DELETE, rentry);
#else
				FlowTable::delFlowFromHw(rentry);
#endif
				FlowTable::routeTable.remove(*itRoutes);
				return 0;
			}
		}
		break;
	}

	return 0;
}

void FlowTable::fakeReq(const char *hostAddr, const char *intf) {
	int s;
	struct arpreq req;
	struct hostent *hp;
	struct sockaddr_in *sin;

	memset(&req, 0, sizeof(req));

	sin = (struct sockaddr_in *) &req.arp_pa;
	sin->sin_family = AF_INET;
	sin->sin_addr.s_addr = inet_addr(hostAddr);

    // Cast to eliminate warning. in_addr.s_addr is uint32_t (netinet/in.h:141)
	if (sin->sin_addr.s_addr == (uint32_t) -1) {
		if (!(hp = gethostbyname(hostAddr))) {
			fprintf(stderr, "ARP: %s ", hostAddr);
			perror(NULL);
			return;
		}
		memcpy(&sin->sin_addr, hp->h_addr, sizeof(sin->sin_addr));
	}

	if ((s = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("socket() failed");
		return;
	}

	connect(s, (struct sockaddr *) sin, sizeof(struct sockaddr));
	close(s);
}

const MACAddress& FlowTable::getGateway(const IPAddress& gateway,
                                        const Interface& iface) {
    if (is_port_down(iface.port)) {
        return FlowTable::MAC_ADDR_NONE;
    }

    // We need to resolve the gateway's IP in order to install a route flow.
    // The MAC address of the next-hop is required as it is used to re-write
    // the layer 2 header before forwarding the packet.
    for (int tries = 0; tries < 50; tries++) {
        list<HostEntry>::iterator iter;
        for (iter = FlowTable::hostTable.begin();
             iter != FlowTable::hostTable.end(); ++iter) {
            if (iter->address == gateway) {
                return (iter->hwaddress);
            }
        }

        FlowTable::fakeReq(gateway.toString().c_str(), iface.name.c_str());
        struct timespec sleep = {0, 20000000}; // 20ms
        nanosleep(&sleep, NULL);
    }

    return FlowTable::MAC_ADDR_NONE;
}

bool FlowTable::is_port_down(uint32_t port) {
    vector<uint32_t>::iterator it;
    for (it=down_ports->begin() ; it < down_ports->end(); it++)
        if (*it == port)
            return true;
    return false;
}

#ifdef ROUTEMOD_ENABLED
int FlowTable::setEthernet(RouteMod& rm, const Interface& local_iface,
                           const MACAddress& gateway) {
    rm.add_match(Match(RFMT_ETHERNET, local_iface.hwaddress));

    if (rm.get_mod() != RMT_DELETE) {
        rm.add_action(Action(RFAT_SET_ETH_SRC, local_iface.hwaddress));
        rm.add_action(Action(RFAT_SET_ETH_DST, gateway));
    }

    return 0;
}

int FlowTable::setIP(RouteMod& rm, const IPAddress& addr,
                     const IPAddress& mask) {
     if (addr.getVersion() == IPV4) {
        rm.add_match(Match(RFMT_IPV4, addr, mask));
    } else if (addr.getVersion() == IPV6) {
        rm.add_match(Match(RFMT_IPV6, addr, mask));
    } else {
        fprintf(stderr, "Cannot send route with unsupported IP version\n");
        return -1;
    }

    uint16_t priority = DEFAULT_PRIORITY;
    priority += static_cast<uint16_t>(mask.toCIDRMask());
    rm.add_option(Option(RFOT_PRIORITY, priority));

    return 0;
}

void FlowTable::sendToHw(RouteModType mod, const RouteEntry& re) {
    if (mod == RMT_DELETE) {
        sendToHw(mod, re.address, re.netmask, re.interface,
                 FlowTable::MAC_ADDR_NONE);
    } else {
        const MACAddress& remoteMac = getGateway(re.gateway, re.interface);

        sendToHw(mod, re.address, re.netmask, re.interface, remoteMac);
    }
}

void FlowTable::sendToHw(RouteModType mod, const HostEntry& he) {
    boost::scoped_ptr<IPAddress> mask;

    if (he.address.getVersion() == IPV6) {
        mask.reset(new IPAddress(FULL_IPV6_MASK));
    } else if (he.address.getVersion() == IPV4) {
        mask.reset(new IPAddress(FULL_IPV4_MASK));
    } else {
        fprintf(stderr, "Received HostEntry with unsupported IP version\n");
        return;
    }

    sendToHw(mod, he.address, *mask.get(), he.interface, he.hwaddress);
}

void FlowTable::sendToHw(RouteModType mod, const IPAddress& addr,
                         const IPAddress& mask, const Interface& local_iface,
                         const MACAddress& gateway) {
    if (is_port_down(local_iface.port)) {
        fprintf(stderr, "Cannot send RouteMod for down port\n");
        return;
    }

    RouteMod rm;

    rm.set_mod(mod);
    rm.set_id(FlowTable::vm_id);

    if (setEthernet(rm, local_iface, gateway) != 0) {
        return;
    }
    if (setIP(rm, addr, mask) != 0) {
        return;
    }

    /* Add the output port. Even if we're removing the route, RFServer requires
     * the port to determine which datapath to send to. */
    rm.add_action(Action(RFAT_OUTPUT, local_iface.port));

    FlowTable::ipc->send(RFCLIENT_RFSERVER_CHANNEL, RFSERVER_ID, rm);
}
#else /* ROUTEMOD_ENABLED */
void FlowTable::addFlowToHw(const RouteEntry& rentry) {
    if (is_port_down(rentry.interface.port)) {
        fprintf(stderr, "Cannot send RouteInfo for down port\n");
        return;
    }

    const MACAddress& dstMac = getGateway(rentry.gateway, rentry.interface);

    if (dstMac == FlowTable::MAC_ADDR_NONE) {
        fprintf(stderr, "Failed to resolve Gateway MAC\n");
        return;
    }

    RouteInfo msg;
    msg.set_is_removal(false);
    msg.set_vm_id(FlowTable::vm_id);
    msg.set_vm_port(rentry.interface.port);
    // Action
    msg.set_dst_port(rentry.interface.port);
    msg.set_src_hwaddress(rentry.interface.hwaddress);
    msg.set_dst_hwaddress(dstMac);
    // Rule
    msg.set_address(rentry.address);
    msg.set_netmask(rentry.netmask);

    // Send
    FlowTable::ipc->send(RFCLIENT_RFSERVER_CHANNEL, RFSERVER_ID, msg);
}

void FlowTable::addFlowToHw(const HostEntry& hentry) {
    if (is_port_down(hentry.interface.port)) {
        fprintf(stderr, "Cannot send RouteInfo for down port\n");
        return;
    }

    RouteInfo msg;
    msg.set_is_removal(false);
    msg.set_vm_id(FlowTable::vm_id);
    msg.set_vm_port(hentry.interface.port);
    // Action
    msg.set_dst_port(hentry.interface.port);
    msg.set_src_hwaddress(hentry.interface.hwaddress);
    msg.set_dst_hwaddress(hentry.hwaddress);
    // Rule
    msg.set_address(hentry.address);
    msg.set_netmask(IPAddress(FULL_IPV4_MASK));

    // Send
    FlowTable::ipc->send(RFCLIENT_RFSERVER_CHANNEL, RFSERVER_ID, msg);
}

void FlowTable::delFlowFromHw(const RouteEntry& rentry) {
	// We don't need to resolve the gateway's IP on route flow deletion.
	// The MAC address of the next-hop is useless when deleting flows.
    if (is_port_down(rentry.interface.port)) {
        fprintf(stderr, "Cannot send RouteInfo for down port\n");
        return;
    }

    RouteInfo msg;
    msg.set_is_removal(true);
    msg.set_vm_id(FlowTable::vm_id);
    msg.set_vm_port(rentry.interface.port);
    // Action
    msg.set_dst_port(0);
    msg.set_src_hwaddress(rentry.interface.hwaddress);
    msg.set_dst_hwaddress(MACAddress(EMPTY_MAC_ADDRESS));
    // Rule
    msg.set_address(rentry.address);
    msg.set_netmask(rentry.netmask);

    // Send
    FlowTable::ipc->send(RFCLIENT_RFSERVER_CHANNEL, RFSERVER_ID, msg);
}

void FlowTable::delFlowFromHw(const HostEntry& hentry) {
    if (is_port_down(hentry.interface.port)) {
        fprintf(stderr, "Cannot send RouteInfo for down port\n");
        return;
    }

    RouteInfo msg;
    msg.set_is_removal(true);
    msg.set_vm_id(FlowTable::vm_id);
    msg.set_vm_port(hentry.interface.port);
    // Action
    msg.set_dst_port(hentry.interface.port);
    msg.set_src_hwaddress(hentry.interface.hwaddress);
    msg.set_dst_hwaddress(hentry.hwaddress);
    // Rule
    msg.set_address(hentry.address);
    msg.set_netmask(IPAddress(FULL_IPV4_MASK));

    // Send
    FlowTable::ipc->send(RFCLIENT_RFSERVER_CHANNEL, RFSERVER_ID, msg);
}
#endif /* ROUTEMOD_ENABLED */
