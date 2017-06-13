package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.annotations.Experimental;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.util.AverageMinMax;
import org.jgroups.util.DefaultThreadFactory;
import org.jgroups.util.RingBuffer;
import org.jgroups.util.Runner;

import java.util.List;

/**
 * Bundler implementation which sends message batches (or single messages) as soon as the remove queue is full
 * (or max_bundler_size would be exceeded).<br/>
 * Messages are removed from the main queue  and processed as follows (assuming they all fit into the remove queue):<br/>
 * A B B C C A causes the following sends: {AA} -> {CC} -> {BB}<br/>
 * Note that <em>null</em> is also a valid destination (send-to-all).<br/>
 * JIRA: https://issues.jboss.org/browse/JGRP-2171
 * @author Bela Ban
 * @since  4.0.4
 */
@Experimental
public class RemoveQueueBundler extends BaseBundler {
    protected RingBuffer<Message>   rb;
    protected Runner                runner;
    protected Message[]             remove_queue;
    protected final AverageMinMax   avg_batch_size=new AverageMinMax();
    protected int                   queue_size=512;

    @ManagedAttribute(description="Remove queue size",name="rqb.remove_queue_size")
    public int getRemoveQueueSize() {return remove_queue.length;}

    @ManagedAttribute(description="Sets the size of the remove queue",name="rqb.remove_queue_size")
    public void setRemoveQueueSize(int size) {
        if(size == queue_size) return;
        queue_size=size;
        remove_queue=new Message[queue_size];
    }

    @ManagedAttribute(description="Average batch length",name="rqb.avg_batch_size")
    public String getAvgBatchSize() {return avg_batch_size.toString();}


    public void init(TP transport) {
        super.init(transport);
        rb=new RingBuffer(Message.class, transport.getBundlerCapacity());
        remove_queue=new Message[queue_size];
        runner=new Runner(new DefaultThreadFactory("aqb", true, true), "runner", this::run, null);
    }

    public synchronized void start() {
        super.start();
        runner.start();
    }

    public synchronized void stop() {
        runner.stop();
        super.stop();
    }

    public void send(Message msg) throws Exception {
        rb.put(msg);
    }

    public void run() {
        try {
            int drained=rb.drainToBlocking(remove_queue);
            if(drained == 1) {
                output.position(0);
                sendSingleMessage(remove_queue[0]);
                return;
            }

            for(int i=0; i < drained; i++) {
                Message msg=remove_queue[i];
                long size=msg.size();
                if(count + size >= transport.getMaxBundleSize())
                    sendBundledMessages();
                addMessage(msg, msg.size());
            }

            sendBundledMessages();

            // iterate through the remove queue and try to send as many batches as possible
/*            int start=0;
            for(;;) {
                for(; start < drained && remove_queue[start] == null; ++start) ;
                if(start >= drained)
                    return;
                Address dest=remove_queue[start].getDest();
                int numMsgs=1;
                for(int i=start + 1; i < drained; ++i) {
                    Message msg=remove_queue[i];
                    if(msg != null && (dest == msg.getDest() || (Objects.equals(dest, msg.getDest())))) {
                        msg.setDest(dest); // avoid further equals() calls
                        numMsgs++;
                    }
                }
                try {
                    output.position(0);
                    if(numMsgs == 1) {
                        sendSingleMessage(remove_queue[start]);
                        remove_queue[start]=null;
                    }
                    else {
                        Util.writeMessageListHeader(dest, remove_queue[start].getSrc(), transport.cluster_name.chars(), numMsgs, output, dest == null);
                        for(int i=start; i < drained; ++i) {
                            Message msg=remove_queue[i];
                            // since we assigned the matching destination we can do plain ==
                            if(msg != null && msg.getDest() == dest) {
                                msg.writeToNoAddrs(msg.getSrc(), output, transport.getId());
                                remove_queue[i]=null;
                            }
                        }
                        transport.doSend(output.buffer(), 0, output.position(), dest);
                    }
                    start++;
                }
                catch(Exception e) {
                    log.error("Failed to send message", e);
                }
            }*/

        }
        catch(Throwable t) {
        }
    }

    public int size() {
        return rb.size();
    }

    protected void sendMessageList(Address dest, Address src, List<Message> list) {
        super.sendMessageList(dest, src, list);
        avg_batch_size.add(list.size());
    }


}
