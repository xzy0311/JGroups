package org.jgroups.protocols;

import org.jgroups.*;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.Util;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests {@link MFC} and {@link MFC_NB}
 * @author Bela Ban
 * @since  4.2.0
 */
@Test(groups=Global.FUNCTIONAL,singleThreaded=true,dataProvider="create")
public class MFC_Test {
    protected static final int MAX_CREDITS=10000;
    protected static final int MSG_SIZE=1000;
    protected JChannel a,b,c,d;


    @BeforeMethod protected void setup() throws Exception {
        a=new JChannel(Util.getTestStack()).name("A").connect("MFC_Test");
        b=new JChannel(Util.getTestStack()).name("B").connect("MFC_Test");
        c=new JChannel(Util.getTestStack()).name("C").connect("MFC_Test");
        d=new JChannel(Util.getTestStack()).name("D").connect("MFC_Test");
        Util.waitUntilAllChannelsHaveSameView(10000, 500, a,b,c,d);
    }

    @AfterMethod protected void destroy() throws Exception {
        Util.shutdown(c);
        Util.closeReverse(a,b,d);
    }

    @DataProvider
    protected static Object[][] create() {
        return new Object[][] {
          {MFC.class},
          {MFC_NB.class}
        };
    }


    /** A blocks threads on sending messages to C. When C is removed from the view, the threads should unblock */
    public void testBlockingAndViewChange(Class<MFC> clazz) throws Exception {
        inject(clazz, a,b,d); // C has no MFC, won't send any credits -> this will make threads in A (sending to C) block

        for(JChannel ch: Arrays.asList(a,b,d))
            send(ch, null, 9950); // uses up the 10'000 initial credits, so the next threads block on C
        if(clazz.equals(MFC_NB.class)) {
            // we send another 9K to fill the queue
            for(JChannel ch: Arrays.asList(a,b,d))
                send(ch, null, 9950); // uses up the 10'000 initial credits, so the next threads block on C
        }

        Thread[] threads=new Thread[10];
        for(int i=0; i < threads.length; i++) {
            threads[i]=new Thread(() -> send(a, null, MSG_SIZE));
            threads[i].start();
        }

        Util.waitUntil(10000, 500,
                       () -> Arrays.stream(threads)
                         .allMatch(t -> t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING),
                       () -> "threads:\n" + Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
                         .collect(Collectors.joining("\n")));
        System.out.printf("threads:\n%s\n", Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
          .collect(Collectors.joining("\n")));

        // install a new view in A excluding C (this should unblock the senders):
        View v=View.create(a.getAddress(), a.getView().getViewId().getId() +1, a.getAddress(), b.getAddress(), d.getAddress());

        for(JChannel ch: Arrays.asList(a,b,d)) {
            ((GMS)ch.getProtocolStack().findProtocol(GMS.class)).installView(v);
        }

        System.out.printf("view:\n%s\n", Stream.of(a, b, c, d).map(ch -> ch.getAddress() + ": " + ch.getView()).collect(Collectors.joining("\n")));

        Util.waitUntil(10000, 500,
                       () -> Arrays.stream(threads).allMatch(t -> t.getState() == Thread.State.TERMINATED),
                       () -> "threads:\n" + Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
                         .collect(Collectors.joining("\n")));
        System.out.printf("threads:\n%s\n", Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
          .collect(Collectors.joining("\n")));
    }


    /** A blocks threads on sending messages to C. When A is stopped, the threads should unblock */
    public void testBlockingAndStop(Class<MFC> clazz) throws Exception {
        inject(clazz, a,b,d); // C has no MFC, won't send any credits -> this will make threads in A (sending to C) block

        for(JChannel ch: Arrays.asList(a,b,d))
            send(ch, null, 9950); // uses up the 10'000 initial credits, so the next threads block on C
        if(clazz.equals(MFC_NB.class)) {
            // we send another 9K to fill the queue
            for(JChannel ch: Arrays.asList(a,b,d))
                send(ch, null, 9950); // uses up the 10'000 initial credits, so the next threads block on C
        }

        Thread[] threads=new Thread[10];
        for(int i=0; i < threads.length; i++) {
            threads[i]=new Thread(() -> send(a, null, MSG_SIZE));
            threads[i].start();
        }

        Util.waitUntil(10000, 500,
                       () -> Arrays.stream(threads)
                         .allMatch(t -> t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING),
                       () -> "threads:\n" + Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
                         .collect(Collectors.joining("\n")));
        System.out.printf("threads:\n%s\n", Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
          .collect(Collectors.joining("\n")));

        a.close();

        Util.waitUntil(10000, 500,
                       () -> Arrays.stream(threads).allMatch(t -> t.getState() == Thread.State.TERMINATED),
                       () -> "threads:\n" + Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
                         .collect(Collectors.joining("\n")));
        System.out.printf("threads:\n%s\n", Arrays.stream(threads).map(t -> t.getId() + ": " + t.getState())
          .collect(Collectors.joining("\n")));

        View v=View.create(b.getAddress(), b.getView().getViewId().getId() +1, b.getAddress(), d.getAddress());

        for(JChannel ch: Arrays.asList(b,d)) {
            ((GMS)ch.getProtocolStack().findProtocol(GMS.class)).installView(v);
        }
    }



    protected static void inject(Class<MFC> clazz, JChannel ... channels) throws Exception {
        for(JChannel ch: channels) {
            MFC mfc=clazz.getConstructor().newInstance();
            mfc.maxCredits(MAX_CREDITS).maxBlockTime(60000);
            if(mfc instanceof MFC_NB)
                ((MFC_NB)mfc).setMaxQueueSize(MAX_CREDITS);
            mfc.init();
            ProtocolStack stack=ch.getProtocolStack();
            stack.removeProtocol(MFC.class); // just in case we already have an MFC protocol
            stack.insertProtocol(mfc, ProtocolStack.Position.ABOVE, GMS.class);
            View v=ch.getView();
            mfc.handleViewChange(v.getMembers());
        }
    }


    protected static void send(JChannel ch, Address target, int length) {
        try {
            ch.send(new Message(target, new byte[length]));
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

}