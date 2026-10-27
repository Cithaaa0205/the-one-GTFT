package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimClock;

/**
* Custom report sederhana yang mencatat waktu pengiriman dan ukuran pesan
*/
public class MyCustomReport extends Report implements MessageListener {
    
    public MyCustomReport() {
        super();
    }
    
    @Override
    public void newMessage(Message m) {
        // Dipanggil saat pesan baru dibuat
    }
    
    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        // Dipanggil saat transfer dimulai
    }
    
    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        // Dipanggil jika koneksi putus saat transfer
    }
    
    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        // Dipanggil saat pesan dihapus atau dropped karena buffer penuh
    }
    
    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (m.getTo() == to || firstDelivery) {    
            String output = "Time: " + SimClock.getTime() + 
            " ID: " + m.getId() + 
            " Size: " + m.getSize() + 
            " Latency: " + (SimClock.getTime() - m.getCreationTime());

            // Menulis ke file report (otomatis ditangani oleh parent class Report)
            write(output);
        }
    }
    
    @Override
    public void done() {
        // Dipanggil saat simulasi berakhir (cocok untuk hitung rata-rata)
        super.done();
    }
}