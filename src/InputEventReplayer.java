/**
 * Created by Alexander on 16-04-2016.
 */

import javax.swing.*;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * Takes the event recorded by the DocumentEventCapturer and replays
 * them in a JTextArea. The delay of 1 sec is only to make the individual
 * steps in the reply visible to humans.
 *
 * @author Jesper Buus Nielsen
 *
 */
public class InputEventReplayer implements Runnable, ReplayerInterface {

    private DocumentEventCapturer dec;
    private JTextArea area;
    public Socket socket;
    private ObjectInputStream ois;
    private LinkedBlockingQueue<MyTextEvent> eventHistory;
    private Thread EventQueThread = new Thread();

    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket) {
        this.dec = dec;
        this.area = area;
        this.socket = socket;
        try {
            System.out.println("About to create input stream");
            //this.socket = new Socket("192.168.43.123", 40604);
            System.out.println("Socket being used: " + this.socket);
            ois = new ObjectInputStream(this.socket.getInputStream());
            System.out.println("Created objectinput stream");
        } catch (IOException e) {
            e.printStackTrace();
        }
        eventHistory = new LinkedBlockingQueue<MyTextEvent>();
        startEventQueThread();
        System.out.println("Inputstream created and event queing thread started");
    }

    private void startEventQueThread() {
        if(EventQueThread.isAlive()) {
            EventQueThread.interrupt();
        }
        EventQueThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        MyTextEvent mte = null;
                        while ((mte = (MyTextEvent) ois.readObject()) != null){
                            System.out.println("mte being added to event queue: " + mte);
                            eventHistory.add(mte);
                            mte = null;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        EventQueThread.start();
    }

    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            //waitForOneSecond();
            try {
                MyTextEvent mte = eventHistory.take();
                if (mte instanceof TextInsertEvent) {
                    final TextInsertEvent tie = (TextInsertEvent)mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {

                                System.out.println("tie in event queue, trying to write to area2 ");
                                area.insert(tie.getText(), tie.getOffset());


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