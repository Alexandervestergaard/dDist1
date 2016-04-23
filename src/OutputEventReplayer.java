import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

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

    public OutputEventReplayer(DocumentEventCapturer dec, Socket socket) {
        this.dec = dec;
        this.socket = socket;
        try {
            System.out.println("Outputeventreplayer about to create outputstream");
            oos = new ObjectOutputStream(this.socket.getOutputStream());
            oos.flush();
            System.out.println("Outputeventreplayer created outputstream");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            waitForOneSecond();
            try {
                MyTextEvent mte = (MyTextEvent) dec.take();
                if (mte != null) {
                    System.out.println("oos write to stream: " + mte.toString());
                    oos.writeObject(mte);
                }

            } catch (Exception _) {
                wasInterrupted = true;
            }
        }
        System.out.println("I'm the thread running the EventReplayer, now I die!");
    }

    public void waitForOneSecond() {
        try {
            Thread.sleep(1000);
        } catch(InterruptedException _) {
        }
    }
}