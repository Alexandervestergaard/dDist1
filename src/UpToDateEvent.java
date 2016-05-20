import java.util.ArrayList;

/**
 * Created by Morten on 20-05-2016.
 */
public class UpToDateEvent extends MyTextEvent {

    private ArrayList<MyTextEvent> log;

    UpToDateEvent(int offset, int timeStamp, String sender, ArrayList<MyTextEvent> log) {
        super(offset, timeStamp, sender);
        this.log = log;
    }

    public ArrayList<MyTextEvent> getLog() {
        return log;
    }
}
