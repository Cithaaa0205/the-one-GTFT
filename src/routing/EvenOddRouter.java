package routing;

import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

public class EvenOddRouter extends ActiveRouter {

    public EvenOddRouter(Settings s) {
        super(s);
    }

    public EvenOddRouter(EvenOddRouter r) {
        super(r);
    }

    @Override
    public void changedConnection(Connection con) {
        if(!con.isUp()) {
            return;
        }
        
        DTNHost me = getHost();
        DTNHost other = con.getOtherNode(me); 
        
        int myId = me.getAddress();
        int otherId = other.getAddress();
        boolean isSameType = (myId % 2) == (otherId % 2);
        
        if (isSameType) {
            System.out.println("[SUKSES] Transfer Valid: " + me + " (" + myId + ") -> " + other + " (" + otherId + ")");
            super.changedConnection(con);

            List<Message> m = new ArrayList<>(this.getMessageCollection());

            tryAllMessages(con, m);
        } else {
            System.out.println("[BLOKIR] Beda Jenis: " + me + " (" + myId + ") ketemu " + other + " (" + otherId + ")");
        }
    }

    @Override
    public MessageRouter replicate() {
        return new EvenOddRouter(this);
    }
    
}