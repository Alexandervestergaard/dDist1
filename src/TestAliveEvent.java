/**
 * Created by Morten on 24-05-2016.
 */
public class TestAliveEvent extends MyTextEvent implements Unlogable {
    /*
     * Et tomt MyTextEvent der tester om en forbindelse stadig er aktiv.
     */
    TestAliveEvent(int offset, int timeStamp, String sender) {
        super(offset, timeStamp, sender);
    }
}
