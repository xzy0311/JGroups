package org.jgroups.protocols;

import org.jgroups.Header;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Header to measure round-trip times (in nanoseconds) for sync RPCs (https://issues.redhat.com/browse/JGRP-2604)
 * @author Bela Ban
 * @since  5.2.1
 */
public class RTTHeader extends Header {
    public static final short RTT_ID=1500; // dummy protocol ID to get the RTTHeader from a message

    protected long send_req;        // when the request is sent (e.g. in RequestCorrelator)
    protected long serialize_req;   // when the request is serialized at the caller

    protected long deserialize_req; // the request is de-serialized at the receiver
    protected long receive_req;     // the RequestCorrelator receives the request
    protected long send_rsp;        // the RequestCorrelator is about to send the request
    protected long serialize_rsp;   // just before the response is serialized at the receiver

    protected long deserialize_rsp; // just after the response has been deserialized at the caller
    protected long receive_rsp;     // the RequestCorrelator has received the response
    protected long rsp_dispatched;  // the RequestCorrelator has handed over the response to the caller


    public RTTHeader() {
    }

    public Supplier<? extends Header> create()   {return RTTHeader::new;}
    public short getMagicId()                    {return 97;}

    public RTTHeader sendReq(long nanos)         {send_req=nanos; return this;}
    public RTTHeader serializeReq(long nanos)    {serialize_req=nanos; return this;}
    public RTTHeader deserializeReq(long nanos)  {deserialize_req=nanos; return this;}

    /** Time to send a request down, from sending until just before serialization */
    public long downRequest() {
        return send_req > 0 && serialize_req > 0? serialize_req-send_req : 0;
    }

    /** The time the request has spent on the network, between serializing it at the caller and de-serializing it
        at the receiver */
    public long networkRequest() {
        return deserialize_req > 0 && serialize_req > 0? deserialize_req-serialize_req : 0;
    }

    public String toString() {
        return String.format("down req=%s network req=%s\n", print(downRequest()), print(networkRequest()));
    }

    public int serializedSize() {
        return 0;
    }

    public void writeTo(DataOutput out) throws IOException {

    }

    public void readFrom(DataInput in) throws IOException, ClassNotFoundException {

    }

    protected static String print(long r) {
        return r <= 0? "n/a" : Util.printTime(r, TimeUnit.NANOSECONDS);
    }
}
