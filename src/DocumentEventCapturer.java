/**
 * Created by Alexander on 16-04-2016.
 */
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 *
 * This class captures and remembers the text events of the given document on
 * which it is put as a filter. Normally a filter is used to put restrictions
 * on what can be written in a buffer. In out case we just use it to see all
 * the events and make a copy.
 *
 * @author Jesper Buus Nielsen
 *
 */
public class DocumentEventCapturer extends DocumentFilter {

    private boolean active = true;
    private int timeStamp = 0;
    private ChatServer server;
    private boolean isFromServer = false;
    private final String sender;
    //En variable der siger om dette objekt er started eller ej.
    private boolean started = false;

    /*
     * We are using a blocking queue for two reasons:
     * 1) They are thread safe, i.e., we can have two threads add and take elements
     *    at the same time without any race conditions, so we do not have to do
     *    explicit synchronization.
     * 2) It gives us a member take() which is blocking, i.e., if the queue is
     *    empty, then take() will wait until new elements arrive, which is what
     *    we want, as we then don't need to keep asking until there are new elements.
     */
    protected LinkedBlockingQueue<MyTextEvent> eventHistory = new LinkedBlockingQueue<MyTextEvent>();

    public DocumentEventCapturer(String sender) {
        this.sender = sender;
    }

    /**
     * If the queue is empty, then the call will block until an element arrives.
     * If the thread gets interrupted while waiting, we throw InterruptedException.
     *
     * @return Head of the recorded event queue.
     */
    MyTextEvent take() throws InterruptedException {
        return eventHistory.take();
    }

    /*
     * Denne klasse holder styr på timestamps. De bliver brugt til at lave rollback.
     * Tilføjet en variable der holder styr på om den er tændt eller slukket. Hvis den er slukket
     * skal den ikke tælle timstamp op eller tilføje events til køen.
     */
    public void insertString(FilterBypass fb, int offset,
                             String str, AttributeSet a)
            throws BadLocationException {
        if (active) {
        /* Queue a copy of the event and then modify the textarea */
            addInsertToStream(offset, str);
            timeStamp++;
        }
            super.insertString(fb, offset, str, a);
    }

    /*
     * Tilføjer insertEvents til den rigtige stream som afhænger af om dette objekt er lavet fra client eller server.
     * Hvis Dette objekt tilhører en server tager OutputEventReplayers ikke direkte fra listen, men i stedet bliver
     * alle events indsat i deres kø. Dette er for at undgå raceconditions i take().
     */
    private void addInsertToStream(int offset, String str) {
        /*
         * Hvis denne DocumentEventCapturer er lavet af en server skal den ikke tilføje eventet til sin kø.
         * I stedet skal den tilføje den til OutputEventReplayerens kø for alle serverens OutputEventReplayers.
         */
        if (isFromServer){
            for (OutputEventReplayer oep : server.getOutputList()){
                oep.forcedQueueAdd(new TextInsertEvent(offset, str, timeStamp, sender));
            }
        }
        else {
            if (started) {
                eventHistory.add(new TextInsertEvent(offset, str, timeStamp, sender));
            }
        }
    }

    public void remove(FilterBypass fb, int offset, int length)
            throws BadLocationException {
        if (active) {
        /* Queue a copy of the event and then modify the textarea */
            addRemoveToStream(offset, length);
            timeStamp++;
        }
            super.remove(fb, offset, length);
    }

    public void replace(FilterBypass fb, int offset, int length, String str, AttributeSet a) throws BadLocationException {
        if (active) {
            /* Queue a copy of the event and then modify the text */
            if (length > 0) {
                addRemoveToStream(offset, length);
                timeStamp++;
            }
            addInsertToStream(offset, str);
            timeStamp++;
        }
        super.replace(fb, offset, length, str, a);
    }

    /*
     * Tilføjer insertEvents til den rigtige stream som afhænger af om dette objekt er lavet fra client eller server.
     * Hvis Dette objekt tilhører en server tager OutputEventReplayers ikke direkte fra listen, men i stedet bliver
     * alle events indsat i deres kø. Dette er for at undgå raceconditions i take().
     */
    private void addRemoveToStream(int offset, int length) {
        /*
         * Hvis denne DocumentEventCapturer er lavet af en server skal den ikke tilføje eventet til sin kø.
         * I stedet skal den tilføje den til OutputEventReplayerens kø for alle serverens OutputEventReplayers.
         */
        if (isFromServer){
            for (OutputEventReplayer oep : server.getOutputList()){
                oep.forcedQueueAdd(new TextRemoveEvent(offset, length, timeStamp, sender));
            }
        }
        else {
            if (started){
            eventHistory.add(new TextRemoveEvent(offset, length, timeStamp, sender));
            }
        }
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(int timeStamp) {
        this.timeStamp = timeStamp;
    }

    /*
     * En metode der bliver brugt til at sætte om dette objekt tilhører en server eller client. Hvis det er fra en server
     * har den også en liste over hvilke OutputEventReplayers der skal have beskederne.
     */
    public void setServer(ChatServer server) {
        if (server != null) {
            this.server = server;
            isFromServer = true;
        }
        else {
            this.server = null;
            isFromServer = false;
        }
    }

    /*
     * Sætter startd som er en variable der bestemme om events skal tilføjes til queues. Hvis man hverken har
     * kaldt listen eller connect skal input ikke registreres.
     */
    public void setStarted(boolean started){
        this.started = started;
    }
}
