package org.jgroups.protocols.pbcast;

import org.jgroups.annotations.GuardedBy;
import org.jgroups.logging.Log;
import org.jgroups.util.BoundedList;
import org.jgroups.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Responsible for dispatching JOIN/LEAVE/MERGE requests to the GMS protocol. Bundles multiple concurrent requests into
 * a request list
 * @param <R> the type of the request
 * @author Bela Ban
 * @since  4.0.5
 */
public class ViewHandler<R> {
    protected final Collection<R>           requests=new ConcurrentLinkedQueue<>();
    protected final Lock                    lock=new ReentrantLock();
    protected final AtomicInteger           count=new AtomicInteger();
    protected final AtomicBoolean           suspended=new AtomicBoolean(false);
    @GuardedBy("lock")
    protected boolean                       processing;
    protected final GMS                     gms;
    protected final Consumer<Collection<R>> req_processor;
    protected final BiPredicate<R,R>        req_matcher;
    protected final BoundedList<String>     history=new BoundedList<>(20); // maintains a list of the last 20 requests
    protected static final long             THREAD_WAIT_TIME=5000;


    /**
     * Constructor
     * @param gms The ref to GMS
     * @param req_processor A request processor which processes a list of requests
     * @param req_matcher The matcher which determines whether any given 2 requests can be processed together
     */
    public ViewHandler(GMS gms, Consumer<Collection<R>> req_processor, BiPredicate<R,R> req_matcher) {
        if(req_processor == null)
            throw new IllegalArgumentException("request processor cannot be null");
        this.gms=gms;
        this.req_processor=req_processor;
        this.req_matcher=req_matcher != null? req_matcher : (a,b) -> true;
    }

    public boolean        suspended()         {return suspended.get();}
    public int            size()              {return requests.size();}

    public void add(R req) {
        if(_add(req))
            process(requests);
    }

    @SuppressWarnings("unchecked")
    public void add(R ... reqs) {
        if(_add(reqs))
            process(requests);
    }


    /** Clears the queue and discards new requests from now on */
    public void suspend() {
        if(suspended.compareAndSet(false, true))
            requests.clear();
    }


    public void resume() {
        suspended.compareAndSet(true, false);
    }


    public String dumpQueue() {
        return requests.stream()
          .collect(StringBuilder::new, (sb,el) -> sb.append(el).append("\n"), StringBuilder::append).toString();
    }

    public String dumpHistory() {
        return history.stream()
          .collect(StringBuilder::new, (sb,el) -> sb.append(el + "\n"), StringBuilder::append).toString();
    }

    public String toString() {
        return Util.printListWithDelimiter(requests, ", ");
    }

    protected Log log() {return gms.getLog();}


    protected boolean _add(R req) {
        if(req == null || suspended.get()) {
            log().trace("%s: queue is suspended; request %s is discarded", gms.getLocalAddress(), req);
            return false;
        }
        count.incrementAndGet();

        lock.lock();
        try {
            if(!requests.contains(req)) {
                requests.add(req);
                history.add(new Date() + ": " + req.toString());
                return count.decrementAndGet() == 0 && !processing && (processing=true);
            }
            return false;
        }
        finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    protected boolean _add(R ... reqs) {
        if(reqs == null || reqs.length == 0 || suspended.get()) {
            log().trace("%s: queue is suspended; requests are discarded", gms.getLocalAddress());
            return false;
        }

        count.incrementAndGet();
        lock.lock();
        try {
            for(R req: reqs) {
                if(!requests.contains(req)) {
                    requests.add(req);
                    history.add(new Date() + ": " + req.toString());
                }
            }
            return count.decrementAndGet() == 0 && !processing && (processing=true);
        }
        finally {
            lock.unlock();
        }
    }

    /** We're guaranteed that only one thread will be called with this method at any time */
    protected void process(Collection<R> requests) {
        for(;;) {
            while(!requests.isEmpty()) {
                removeAndProcess(requests); // remove matching requests and process them
            }
            lock.lock();
            try {
                if(requests.isEmpty()) {
                    processing=false;
                    return;
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Removes requests as long as they match - breaks at the first non-matching request or when requests is empty
     * This method must catch all exceptions; or else process() might return without setting processing to true again!
     */
    protected void removeAndProcess(Collection<R> requests) {
        try {
            Collection<R> removed=new ArrayList<>();
            Iterator<R> it=requests.iterator();
            R first_req=it.next();
            removed.add(first_req);
            it.remove();

            while(it.hasNext()) {
                R next=it.next();
                if(req_matcher.test(first_req, next)) {
                    removed.add(next);
                    it.remove();
                }
                else
                    break;
            }
            req_processor.accept(removed);
        }
        catch(Throwable t) {
            log().error("failed processing requests", t);
        }
    }

    protected static void join(Thread t) {
        try {
            t.join(THREAD_WAIT_TIME);
        }
        catch(InterruptedException e) {
        }
    }


}
