package samba.domain.messages.handler;

import org.ethereum.beacon.discovery.schema.NodeRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import samba.domain.messages.requests.Ping;
import samba.network.history.HistoryNetworkIncomingRequests;


public class PingHandler implements PortalWireMessageHandler<Ping> {

    private static final Logger LOG = LoggerFactory.getLogger(PingHandler.class);

    @Override
    public void handle(HistoryNetworkIncomingRequests network, NodeRecord srcNode, Ping ping) {
        LOG.info("{} message received", ping.getMessageType());
        network.handlePing(srcNode,ping);
    }
}