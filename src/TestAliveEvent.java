/**
 * Created by Morten on 24-05-2016.
 */
public class TestAliveEvent extends MyTextEvent implements Unlogable {
    TestAliveEvent(int offset, int timeStamp, String sender) {
        super(offset, timeStamp, sender);
    }
}
