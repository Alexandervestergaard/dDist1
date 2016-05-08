/**
 * Created by Alexander on 16-04-2016.
 */

import javafx.collections.transformation.SortedList;

import javax.swing.*;
import javax.swing.tree.ExpandVetoException;
import javax.swing.undo.UndoManager;
import javax.xml.soap.Text;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
    private PriorityBlockingQueue<MyTextEvent> eventHistory;
    private Thread EventQueThread = new Thread();
    private OutputEventReplayer oer;
    private ArrayList<MyTextEvent> eventList = new ArrayList<MyTextEvent>();
    private ReentrantLock rollBackLock = new ReentrantLock();
    private Comparator<? super MyTextEvent> mteSorter;

    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket, OutputEventReplayer oer) {
        this.dec = dec;
        this.area = area;
        this.socket = socket;
        eventHistory = new PriorityBlockingQueue<MyTextEvent>();
        this.oer = oer;
        startEventQueThread();
        mteSorter = new Comparator<MyTextEvent>() {
            @Override
            public int compare(MyTextEvent o1, MyTextEvent o2) {
                return o1.compareTo(o2);
            }
        };
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

    private void rollback(int rollbackTo) {
        rollBackLock.lock();
        try {
            dec.setActive(false);
            oer.setEventListActive(false);

            //noinspection Since15
            eventList.sort(mteSorter);
            System.out.println("list:");
            for (MyTextEvent q: eventList){
                System.out.println("test loop");
                if (q instanceof TextInsertEvent) {
                    System.out.print(((TextInsertEvent) q).getText() + ", ");
                }
                else if (q instanceof TextRemoveEvent){
                    System.out.print("remove, " + ((TextRemoveEvent) q).getLength());
                }
            }
            Collections.reverse(eventList);
            for (MyTextEvent undo : eventList){
                undoEvent(undo);
            }
            Collections.reverse(eventList);
            for (MyTextEvent m : eventList){
                doMTE(m);
            }
            oer.setEventListActive(true);
            dec.setActive(true);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            rollBackLock.unlock();
        }
    }

    private void undoEvent(MyTextEvent m) {
        System.out.println("Should undo: " + m.getTimeStamp());
        if (m instanceof TextInsertEvent){
            safelyRemoveRange(new TextRemoveEvent(m.getOffset(), m.getOffset() + ((TextInsertEvent) m).getText().length(), -1));
        }
        else if (m instanceof  TextRemoveEvent){
            if (m.getOffset() >= 0 && (m.getOffset()+((TextRemoveEvent) m).getLength()) <= area.getText().length()) {
                area.replaceRange(null, m.getOffset(), m.getOffset() + ((TextRemoveEvent) m).getLength());
            }
        }
    }

    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            try {
                /*
                MyTextEvent-objekter hives ud af eventHistory, meget lig EventReplayer
                 */
                final MyTextEvent mte = eventHistory.take();
                eventList.add(mte);
                if (mte.getTimeStamp() >= dec.getTimeStamp()) {
                    System.out.println("impossible time");
                    dec.setTimeStamp(mte.getTimeStamp() + 1);
                    //doMTE(mte);
                    rollback(mte.getTimeStamp());
                }
            else {
                    System.out.println("everything is fine");
                    rollback(mte.getTimeStamp());
                }
            } catch (Exception e) {
                e.printStackTrace();
                wasInterrupted = true;
            }
        }
        System.out.println("I'm the thread running the EventReplayer, now I die!");
    }

    private void doMTE(MyTextEvent mte) {
        if (mte instanceof TextInsertEvent) {
            final TextInsertEvent tie = (TextInsertEvent)mte;
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    try {
                        System.out.println("tie in event queue, trying to write to area2 ");
                        dec.setActive(false);
                        System.out.println(tie.getOffset() <= area.getText().length());
                        if (tie.getOffset() <= area.getText().length()) {
                            area.insert(tie.getText(), tie.getOffset());
                        }
                        dec.setActive(true);
                    } catch (Exception e) {
                        System.err.println(e);
                        e.printStackTrace();
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
                    safelyRemoveRange(tre);
                }
            });
        }
    }

    private void safelyRemoveRange(TextRemoveEvent tre) {
        try {
            if (tre.getOffset() >= 0 && (tre.getOffset()+tre.getLength()) <= area.getText().length()) {
                dec.setActive(false);
                tre.setRemovedText(area.getText(tre.getOffset(), tre.getLength()));
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

    public ArrayList<MyTextEvent> getEventList (){
        return eventList;
    }
}