/**
 * Created by Alexander on 16-04-2016.
 */

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
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
public class InputEventReplayer implements Runnable, ReplayerInterface {

    private DocumentEventCapturer dec;
    private JTextArea area;
    public Socket socket;
    private ObjectInputStream ois;
    //private LinkedBlockingQueue<MyTextEvent> eventHistory;
    private PriorityBlockingQueue<MyTextEvent> eventHistory;
    private Thread EventQueThread = new Thread();
    private OutputEventReplayer oer;

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
                        MyTextEvent mte = null;
                        while ((mte = (MyTextEvent) ois.readObject()) != null){
                            System.out.println("mte being added to event queue: " + mte);
                            dec.setTimeStamp(Math.max(mte.getTimeStamp(), dec.getTimeStamp()) + 1);
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
                MyTextEvent mte = null;
                if(!dec.getKeystroke()) {
                    mte = eventHistory.take();
                }
                if (mte instanceof TextInsertEvent) {
                    final TextInsertEvent tie = (TextInsertEvent)mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {
                                System.out.println("tie in event queue, trying to write to area2 ");
                                System.out.print(tie.getText());
                                System.out.println("  " + tie.getTimeStamp());
                                area.insert(tie.getText(), tie.getOffset());
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
                                dec.setActive(false);
                                area.replaceRange(null, tre.getOffset(), tre.getOffset()+tre.getLength());
                                dec.setActive(true);
                            } catch (Exception e) {
                                System.err.println(e);
				    /* We catch all exceptions, as an uncaught exception would make the
				     * EDT unwind, which is now healthy.
				     */
                            }
                        }
                    });
                } else if (mte instanceof TextReplaceEverythingEvent){
                    final TextReplaceEverythingEvent tree = (TextReplaceEverythingEvent) mte;
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            try {
                                area.replaceRange(tree.getText(), 0, area.getText().length());
                            } catch (Exception e){
                                System.err.println(e);
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