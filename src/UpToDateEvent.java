import java.util.ArrayList;

/**
 * Created by Morten on 20-05-2016.
 */
public class UpToDateEvent extends MyTextEvent implements Unlogable{

    private ArrayList<MyTextEvent> log;

    /*
     * Et TextEvent der indeholder en log. Bliver brugt til at opdatere nye clients når de kommer
     * ind i en samtale som er started.
     */
    UpToDateEvent(int offset, int timeStamp, String sender, ArrayList<MyTextEvent> log) {
        super(offset, timeStamp, sender);
        this.log = log;
    }

    public ArrayList<MyTextEvent> getLog() {
        return log;
    }
}
