/**
 * Created by Alexander on 16-04-2016.
 */
import javax.swing.JTextArea;
import java.awt.EventQueue;
import java.io.ObjectInputStream;
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
public class EventReplayer implements Runnable {

    private DocumentEventCapturer dec;
    private JTextArea area;
    public Socket socket;

    public EventReplayer(DocumentEventCapturer dec, JTextArea area) {
        this.dec = dec;
        this.area = area;
        socket = null;
    }

    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            waitForOneSecond();
            try {
                MyTextEvent mte = dec.take();
                if (mte instanceof TextInsertEvent) {
                    final TextInsertEvent tie = (TextInsertEvent)mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {
                                if (socket!=null) {
                                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                                    oos.writeObject(tie);
                                }

                                if (socket!=null) {
                                    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                                    MyTextEvent myTextEvent = (MyTextEvent)ois.readObject();
                                    if (myTextEvent instanceof TextInsertEvent) {
                                        TextInsertEvent myTextEventInsert = (TextInsertEvent) myTextEvent;
                                        area.insert(myTextEventInsert.getText(), myTextEventInsert.getOffset());
                                    }
                                }

                                //area.insert(tie.getText(), tie.getOffset());

                            } catch (Exception e) {
                                System.err.println(e);
				    /* We catch all axceptions, as an uncaught exception would make the
				     * EDT unwind, which is now healthy.
				     */
                            }
                        }
                    });
                } else if (mte instanceof TextRemoveEvent) {
                    final TextRemoveEvent tre = (TextRemoveEvent)mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {
                                area.replaceRange(null, tre.getOffset(), tre.getOffset()+tre.getLength());
                            } catch (Exception e) {
                                System.err.println(e);
				    /* We catch all axceptions, as an uncaught exception would make the
				     * EDT unwind, which is now healthy.
				     */
                            }
                        }
                    });
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