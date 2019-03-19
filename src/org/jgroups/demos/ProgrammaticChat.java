package org.jgroups.demos;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCPPING;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

/**
 * @author Bela Ban
 */
public class ProgrammaticChat {

    private static final JChannel ch;


    static {


        try {
            Protocol[] prot_stack={
              new TCP().setBindAddress(InetAddress.getByName("127.0.0.1")).setBindPort(7800),
              new TCPPING().initialHosts(Collections.singletonList(new InetSocketAddress("127.0.0.1", 7800))),
              // new MERGE3(),
              //new FD_SOCK(),
              // new FD_ALL(),
              //new VERIFY_SUSPECT(),
              new NAKACK2(),
              // new UNICAST3(),
              // new STABLE(),
              new GMS()};
            //new UFC(),
            //new MFC(),
            //new FRAG2()};
            ch=new JChannel(prot_stack);
            ch.setReceiver(new ReceiverAdapter() {
                public void viewAccepted(View new_view) {
                    System.out.printf("view: %s\n", new_view);
                }

                public void receive(Message msg) {
                    String s=new String(msg.getRawBuffer(), msg.getOffset(), msg.getLength());
                    System.out.printf("%s (from %s)\n", s, msg.getSrc());
                }
            });

        }
        catch(Exception e) {

            System.out.printf("******** exception: %s\n", e);


            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) throws Exception {
        String name=null;

        for(int i =0; i < args.length; i++) {
            if("-name".equals(args[i])) {
                name=args[++i];
                continue;
            }
            System.out.println("ProgrammaticChat [-h] [-name >name>]");
            return;
        }


        ch.connect("ChatCluster");


        for(;;) {
            String line=Util.readStringFromStdin(": ");
            ch.send(null, line.getBytes());
        }
    }

}


