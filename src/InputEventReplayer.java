/**
 * Created by Alexander on 16-04-2016.
 */

import javax.swing.*;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

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
    private Socket socket;
    private ObjectInputStream ois;
    //private LinkedBlockingQueue<MyTextEvent> eventHistory;
    private PriorityBlockingQueue<MyTextEvent> eventHistory;
    private Thread EventQueThread = new Thread();
    private OutputEventReplayer oer;
    private ReentrantLock lock = new ReentrantLock();

    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket, OutputEventReplayer oer) {
        this.dec = dec;
        this.area = area;
        this.socket = socket;
        eventHistory = new PriorityBlockingQueue<MyTextEvent>();
        this.oer = oer;
        startEventQueThread();
        System.out.println("Inputstream created and event queing thread started");
    }

    /*
    En tråd bruges til løbende at hive objekter ud fra streamen, og gemme dem i eventHistory-køen
     */
    private void startEventQueThread() {
        if(EventQueThread.isAlive()) {
            EventQueThread.interrupt();
        }
        EventQueThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        ois = new ObjectInputStream(socket.getInputStream());
                        MyTextEvent mte;
                        while ((mte = (MyTextEvent) ois.readObject()) != null){
                            System.out.println("mte being added to event queue: " + mte);
                            dec.setTimeStamp(Math.max( mte.getTimeStamp(), dec.getTimeStamp()) + 1);
                            eventHistory.add(mte);
                            mte = null;
                        }
                    } catch (EOFException e){

                    } catch (OptionalDataException e){

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
            try {
                /*
                MyTextEvent-objekter hives ud af eventHistory, meget lig EventReplayer
                 */
                final MyTextEvent mte = eventHistory.take();
                if (mte instanceof TextInsertEvent) {
                    final TextInsertEvent tie = (TextInsertEvent)mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {
                                System.out.println("tie in event queue, trying to write to area2 ");
                                dec.setActive(false);
                                area.insert(tie.getText(), tie.getOffset());
                                dec.setActive(true);
                            } catch (Exception e) {
                                System.err.println(e);
				    /* We catch all exceptions, as an uncaught exception would make the
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
                                if (tre.getOffset() >= 0 && (tre.getOffset()+tre.getLength()) <= area.getText().length()) {
                                    dec.setActive(false);
                                    area.replaceRange(null, tre.getOffset(), tre.getOffset() + tre.getLength());
                                    dec.setActive(true);
                                }
                                else {
                                    area.setText("");
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                /* We catch all exceptions, as an uncaught exception would make the
				                 * EDT unwind, which is not healthy.
				                 */
                            }
                        }
                    });
                }
            } catch (Exception e) {
                wasInterrupted = true;
            }
        }
        System.out.println("I'm the thread running the EventReplayer, now I die!");
    }
}