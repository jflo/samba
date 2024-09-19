package samba.domain.messages;

import org.apache.tuweni.units.bigints.UInt64;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;

/***
 * Response message to Offer (0x06).
 */
public class Accept implements PortalWireMessage {

    private final UInt64 connectionId;
    private final Bytes content_keys;

    public Accept(UInt64 connectionId, Bytes contentKeys) {

        //content_keys limit 64
        this.connectionId = connectionId;
        this.content_keys = contentKeys;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.ACCEPT;
    }

    public UInt64 getConnectionId() {
        return connectionId;
    }

    public Bytes getContentKeys() {
        return content_keys;
    }

    @Override
    public Bytes serialize() {
        Bytes connectionIdSerialized = SSZ.encodeUInt64(connectionId.toLong());
        Bytes contentKeysSerialized = SSZ.encodeBytes(content_keys);
        return Bytes.concatenate(
                SSZ.encodeUInt8(getMessageType().ordinal()),
                connectionIdSerialized,
                contentKeysSerialized);
    }
}
