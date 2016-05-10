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
    private boolean eventHistoryActive = true;

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

    private void rollback(int rollbackTo, MyTextEvent rollMTE) {
        rollBackLock.lock();
        try {
            turnOff();
            ArrayList<MyTextEvent> tempList = new ArrayList<MyTextEvent>();
            tempList.add(rollMTE);

            //noinspection Since15
            eventList.sort(mteSorter);
            System.out.println("list:");
            for (MyTextEvent q: eventList){
                System.out.println("loop size: " + eventList.size());
                if (q instanceof TextInsertEvent) {
                    System.out.print(((TextInsertEvent) q).getText() + ", ");
                }
                else if (q instanceof TextRemoveEvent){
                    System.out.print("remove, " + ((TextRemoveEvent) q).getLength());
                }
            }
            System.out.println();
            Collections.reverse(eventList);
            for (MyTextEvent undo : eventList) {
                //if (undo.getTimeStamp() >= rollbackTo) {
                    undoEvent(undo);
                    tempList.add(undo);
                //}
            }
            System.out.println("Adding to eventlist from inputreplayer");
            eventList.add(rollMTE);
            //noinspection Since15
            Collections.reverse(eventList);
            //noinspection Since15
            tempList.sort(mteSorter);
            System.out.println("tempList: ");
            for (MyTextEvent m : tempList){
                    doMTE(m);
                if (m instanceof TextInsertEvent) {
                    System.out.print(((TextInsertEvent) m).getText() + ", ");
                }
                else if (m instanceof TextRemoveEvent){
                    System.out.print("remove, " + ((TextRemoveEvent) m).getLength());
                }
            }
            turnOn();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            rollBackLock.unlock();
        }
    }

    private void turnOff() {
        dec.setActive(false);
        oer.setEventListActive(false);
        setEventHistoryActive(false);
    }

    private void undoEvent(MyTextEvent m) {
        turnOff();
        System.out.println("Should undo: " + m.getTimeStamp());
        if (m instanceof TextInsertEvent){
            if (((TextInsertEvent) m).getText() != null) {
                safelyRemoveRange(new TextRemoveEvent(m.getOffset(), m.getOffset() + ((TextInsertEvent) m).getText().length(), -1));
            }
        }
        else if (m instanceof  TextRemoveEvent){
            if (m.getOffset() >= 0 && m.getOffset() <= area.getText().length()) {
                area.insert(((TextRemoveEvent) m).getRemovedText(), m.getOffset());
            }
        }
        turnOn();
    }

    public void run() {
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            try {
                /*
                MyTextEvent-objekter hives ud af eventHistory, meget lig EventReplayer
                 */
                if (eventHistoryActive) {
                    final MyTextEvent mte = eventHistory.take();
                    if (mte.getTimeStamp() >= dec.getTimeStamp()) {
                        dec.setTimeStamp(mte.getTimeStamp() + 1);
                        System.out.println("impossible time");
                        doMTE(mte);
                        eventList.add(mte);
                        //rollback(mte.getTimeStamp(), mte);
                    } else {
                        System.out.println("everything is fine");
                        rollback(mte.getTimeStamp(), mte);
                    }
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
                        turnOff();
                        System.out.println(tie.getOffset() <= area.getText().length());
                        if (area.getText() == null || tie.getOffset() <= area.getText().length()) {
                            area.insert(tie.getText(), tie.getOffset());
                        }
                        turnOn();
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
            turnOff();
            if (tre.getOffset() >= 0 && (tre.getOffset()+tre.getLength()) <= area.getText().length()) {
                tre.setRemovedText(area.getText(tre.getOffset(), tre.getLength()));
                area.replaceRange(null, tre.getOffset(), tre.getOffset() + tre.getLength());
            }
            else {
                area.setText("");
            }
            turnOn();
        }
        catch (Exception e) {
            e.printStackTrace();
            /* We catch all exceptions, as an uncaught exception would make the
             * EDT unwind, which is not healthy.
             */
        }
    }

    private void turnOn() {
        setEventHistoryActive(true);
        oer.setEventListActive(true);
        dec.setActive(true);
    }

    public ArrayList<MyTextEvent> getEventList (){
        return eventList;
    }

    private void setEventHistoryActive(boolean active){
        this.eventHistoryActive = active;
    }
}