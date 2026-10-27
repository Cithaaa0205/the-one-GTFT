package routing.policies.queue;

import core.Message;
import routing.ActiveRouter;

public interface QueuePolicy {
    /**
     * Menentukan pesan mana yang harus dihapus (di-drop) dari buffer.
     * * @param router Instance ActiveRouter saat ini
     * @param excludeMsgBeingSent true jika pesan yang sedang dikirim tidak boleh dihapus
     * @return Pesan yang akan dihapus, atau null jika tidak ada
     * @link https://www.diva-portal.org/smash/get/diva2:1007858/FULLTEXT01.pdf
     */
    Message getMessageToRemove(ActiveRouter router, boolean excludeMsgBeingSent);
}