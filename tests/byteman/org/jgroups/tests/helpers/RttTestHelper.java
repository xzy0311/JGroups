package org.jgroups.tests.helpers;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.jgroups.Message;
import org.jgroups.protocols.RTTHeader;

import java.util.Iterator;

/**
 * @author Bela Ban
 * @since  5.2.1
 */
public class RttTestHelper extends Helper {
    protected RttTestHelper(Rule rule) {
        super(rule);
    }

    public static void activated() {
        System.out.println("helper activated");
    }
    public static void installed(Rule rule) {
        System.out.printf("helper installs rule %s\n", rule);
    }

    public static void installed(String ruleName) {
        System.out.printf("helper installs rule %s\n", ruleName);
    }
    public static void uninstalled(Rule rule) {
        System.out.printf("helper uninstalled rule %s\n", rule);
    }
    public static void uninstalled(String ruleName) {
        System.out.printf("helper uninstalled rule %s\n", ruleName);

    }
    public static void deactivated() {
        System.out.println("helper deactivated");
    }


    @SuppressWarnings("MethodMayBeStatic")
    public void attachHeader(Message msg) {
        msg.putHeader(RTTHeader.RTT_ID, new RTTHeader());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setSendRequestTime(Message msg) {
        RTTHeader hdr=getHeader(msg);
        if(hdr != null)
            hdr.sendReq(System.nanoTime());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setSerializeRequestTime(Message msg) {
        RTTHeader hdr=getHeader(msg);
        if(hdr != null)
            hdr.serializeReq(System.nanoTime());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setSerializeRequestTime(Iterator<Message> it) {
        long time=System.nanoTime();
        while(it.hasNext()) {
            Message msg=it.next();
            if(msg != null) {
                RTTHeader hdr=getHeader(msg);
                if(hdr != null)
                    hdr.serializeReq(time);
            }
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void copyHeader(Message request, Message response) {
        RTTHeader hdr=getHeader(request);
        if(hdr != null)
            response.putHeader(RTTHeader.RTT_ID, hdr);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public String printHeader(Message msg) {
        RTTHeader hdr=getHeader(msg);
        return hdr == null? "n/a" : hdr.toString();
    }

    protected static RTTHeader getHeader(Message msg) {
        return msg.getHeader(RTTHeader.RTT_ID);
    }

}
