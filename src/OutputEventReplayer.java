import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.PriorityBlockingQueue;

/**
 *
 * Takes the event recorded by the DocumentEventCapturer and replays
 * them in a JTextArea. The delay of 1 sec is only to make the individual
 * steps in the reply visible to humans.
 *
 * @author Jesper Buus Nielsen
 *
 */
public class OutputEventReplayer implements ReplayerInterface, Runnable {

    private DocumentEventCapturer dec;
    private Socket socket;
    private ObjectOutputStream oos;
    private InputEventReplayer iep;
    private boolean eventListActive = true;
    private PriorityBlockingQueue<MyTextEvent> forcedQueue = new PriorityBlockingQueue<MyTextEvent>();
    private boolean isFromServer = false;

    /*
    Konstruktøren sørger for at oprette en OjectOutputStream på socket'en.
    Denne bruges til at sende MyTextEvent-objekter, hentet fra den originale DocumentEventCapturer, ud
    på streamen.
     */
    public OutputEventReplayer(DocumentEventCapturer dec, Socket socket, InputEventReplayer iep) {
        this.dec = dec;
        this.socket = socket;
        this.iep = iep;
        try {
            System.out.println("Outputeventreplayer about to create outputstream");
            oos = new ObjectOutputStream(this.socket.getOutputStream());
            oos.flush();
            System.out.println("Outputeventreplayer created outputstream");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Denne tråd vil løbende sende de MyTextEvent-objekter som dec'en registrerer, ud på streamen.
     * Tilføjer også eventet til InputEventReplayerens log. Bliver påvirket af turnOn() of turnOff().
     */
    @Override
    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            try {
                MyTextEvent mte;
                if (isFromServer) {
                     mte = (MyTextEvent) forcedQueue.take();
                }
                else {
                    mte = (MyTextEvent) dec.take();
                }

                if ((mte != null) && eventListActive) {
                    System.out.println("oos write to stream: " + mte.toString());
                    oos.writeObject(mte);
                    System.out.println("Adding to eventlist from outputreplayer");
                    if (!iep.getEventList().contains(mte)) {
                        iep.getEventList().add(mte);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                wasInterrupted = true;
            }
        }
        System.out.println("I'm the thread running the EventReplayer, now I die!");
    }

    public void waitForOneSecond() {
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
        }
    }

    public void setIep(InputEventReplayer iep) {
        this.iep = iep;
    }

    public void setEventListActive (boolean active){
        this.eventListActive = active;
    }

    public void forcedQueueAdd(MyTextEvent mte){
        forcedQueue.add(mte);
    }

    public void setIsFromServer(boolean isFromServer){
        this.isFromServer = isFromServer;
    }
}