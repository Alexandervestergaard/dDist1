/**
 * Created by Alexander on 16-04-2016.
 */

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
@SuppressWarnings("Since15")
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
    private final String sender;
    private boolean isFromServer = false;
    private ArrayList<OutputEventReplayer> outputList;
    private DistributedTextEditor owner;
    private ChatClient client;
    private String localhostAddress;

    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket, OutputEventReplayer oer, String sender) {
        this.dec = dec;
        this.area = area;
        this.socket = socket;
        this.sender = sender;
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
                        while ((mte = (MyTextEvent) ois.readObject()) != null) {
                            System.out.println("my id: " + sender + " mte id: " + mte.getSender() + " says EventQueueThread and: " + (mte.getSender() != sender));
                            if (!mte.getSender().equals(sender)) {
                                System.out.println("mte being added to event queue: " + mte);
                                eventHistory.add(mte);
                                if (isFromServer) {
                                    for (OutputEventReplayer oer : outputList) {
                                        oer.forcedQueueAdd(mte);
                                    }
                                }
                            }
                            mte = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        });
        EventQueThread.start();
    }

    /*
     * En tråd der tager MyTextEvents ud af eventHistrory, som er køen fra den anden client/server.
     * Afhængig af eventets timestamp, køres eventet normalt og tilføjes til loggen eventList,
     * eller også bliver rollback metoden kaldt med eventets timstamp og eventet selv.
     */
    public void run() {
        boolean test = true;
        boolean wasInterrupted = false;
        while (!wasInterrupted) {
            try {
                /*
                MyTextEvent-objekter hives ud af eventHistory, meget lig EventReplayer
                 */
                if (eventHistoryActive) {
                    test = true;
                    final MyTextEvent mte = eventHistory.take();
                    System.out.println("my id: " + sender + " mte id: " + mte.getSender());
                    if (mte.getTimeStamp() >= dec.getTimeStamp()) {
                        dec.setTimeStamp(mte.getTimeStamp() + 1);
                        System.out.println("impossible time");
                        if (!eventList.contains(mte) && !(mte instanceof Unlogable)) {
                            eventList.add(mte);
                        }
                        doMTE(mte);
                    } else {
                        System.out.println("everything is fine");
                        rollback(mte.getTimeStamp(), mte);
                    }
                }
                if (test) {
                    System.out.print("turned on: " + eventHistoryActive);
                    System.out.println(eventList.toString());
                }
                test = false;
            } catch (Exception e) {
                e.printStackTrace();
                wasInterrupted = true;
            }
        }
        System.out.println("I'm the thread running the EventReplayer, now I die!");
    }

    /*
     * En metoder der retter op på fejl når der kommer et TextEvent som skulle have været indsat før.
     * turnOff() er en metode der midlertidigt slukker for de tråde der opfanger ændringer i tekstfeltet
     * og tilføjer MyTextEvents tilkøen og loggen.
     * tempList er en liste som kommer til at indeholde de MyTextEvents, som bliver fortrudt samt den nye event.
     * Først sorteres loggen efter timestamp med mindste timestamp først. Derefter er der en løkke der printer
     * indholdet af loggen for at se at client og server har samme log.
     * Loggen bliver reversed for at få de sidst udførte events først så de kan blive fortrudt i rigtig rækkefølge.
     * De events der har et timestamp >= det nye events timestamp bliver fortrudt og tilføjet til tempList.
     * Denne liste bliver nu sorteret og elementerne bliver udført. Indholdet bliver også printet.
     */
    private void rollback(int rollbackTo, MyTextEvent rollMTE) {
        rollBackLock.lock();
        try {
            turnOff();
            ArrayList<MyTextEvent> tempList = new ArrayList<MyTextEvent>();
            tempList.add(rollMTE);

            // Loop der printer indholdet af loggen
            System.out.println("list:");
            for (MyTextEvent q: eventList){
                if (q instanceof TextInsertEvent) {
                    System.out.print(((TextInsertEvent) q).getText() + ", ");
                }
                else if (q instanceof TextRemoveEvent){
                    System.out.print("remove" + ((TextRemoveEvent) q).getLength() + ", ");
                }
            }
            System.out.println();
            System.out.println("Log done.");

            // Loop der fortryder events
            Collections.reverse(eventList);
            for (MyTextEvent undo : eventList) {
                if (undo.getTimeStamp() >= rollbackTo) {
                    //waitForOneSecond();
                    undoEvent(undo);
                    tempList.add(undo);
                }
            }
            eventList.sort(mteSorter);
            tempList.sort(mteSorter);

            if (!eventList.contains(rollMTE) && !(rollMTE instanceof Unlogable)) {
                eventList.add(rollMTE);
            }

            // Loope der printer og udfører indholdet af tempList
            System.out.println("tempList: ");
            for (MyTextEvent m : tempList){
                //waitForOneSecond();
                doMTE(m);
                if (m instanceof TextInsertEvent) {
                    System.out.print(((TextInsertEvent) m).getText() + ", ");
                }
                else if (m instanceof TextRemoveEvent){
                    System.out.print("remove" + ((TextRemoveEvent) m).getLength() + ", ");
                }
            }
            System.out.println("Temp done.");

            turnOn();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            rollBackLock.unlock();
        }
    }

    /*
     * En metode der slukker for de tråde der opfanger ændringer i tekstfeltet eller ændrer på loggen.
     */
    private void turnOff() {
        dec.setActive(false);
        oer.setEventListActive(false);
        setEventHistoryActive(false);
    }

    /*
     * En metode der fortryder et event.
     * Hvis eventet er et InsertEvent, bliver der slettet fra feltet på det aktuelle offset hentil længden.
     * Hvis eventet er et RemoveEvent bliver den gamle tekst indsat på offsettet. Den gamle tekst bliver sat
     * når RemoveEventet bliver udført.
     */
    private void undoEvent(MyTextEvent m) {
        turnOff();
        System.out.println("Should undo: " + m.getTimeStamp());
        if (m instanceof TextInsertEvent){
            if (((TextInsertEvent) m).getText() != null) {
                safelyRemoveRange(new TextRemoveEvent(m.getOffset(), ((TextInsertEvent) m).getText().length(), -1, "error"));
            }
        }
        else if (m instanceof  TextRemoveEvent){
            if (m.getOffset() >= 0 && m.getOffset() <= area.getText().length()) {
                area.insert(((TextRemoveEvent) m).getRemovedText(), m.getOffset());
            }
        }
        turnOn();
    }

    /*
     * En metode der udfører et TextEvent.
     * Hvis det er et InsertEvent, bliver det skrevet på tekstfeltet. Hvis der er gået noget galt og offset
     * er større en tekstfeltets teksts længde, bliver det ikke indsat.
     * Hvis det er et RemoveEvent bliver safelyRemoveRange kaldt.
     */
    private void doMTE(MyTextEvent mte) {
        if (mte instanceof TextInsertEvent) {
            final TextInsertEvent tie = (TextInsertEvent)mte;
            try {
                System.out.println("tie in event queue, trying to write to area2 ");
                turnOff();
                if (tie.getOffset() <= area.getText().length()) {
                    area.insert(tie.getText(), tie.getOffset());
                }
                turnOn();
            } catch (Exception e) {
                System.err.println(e);
                e.printStackTrace();
            }
        } else if (mte instanceof TextRemoveEvent) {
            final TextRemoveEvent tre = (TextRemoveEvent)mte;
            safelyRemoveRange(tre);
        }
        /*
         * Hvis TextEventet er et UpToDateEvent, er clienten forbundet til en samtale som allerede er i gang.
         * Den opdaterer sin egen log til at være eventets log. Dette kan antages at være den nyeste log, og hvis den
         * ikke er komplet kommer resten snart. Hele indholdet af eventets log bliver tilføjet til denne clients egen log.
         * Der bliver derefter kaldt rollback til 0 for at blive konsistent. tempEvent er et dummy event der bliver
         * brugt da rollback metoden skal tage en event. Både tempEvent og UpToDateEventet bliver fjernet fra
         * loggen når den er færdig.
         */
        else  if(mte instanceof UpToDateEvent){
            //eventList.addAll(((UpToDateEvent) mte).getLog());
            eventList = ((UpToDateEvent) mte).getLog();
            //eventList.remove(mte);
            area.setText("");
            System.out.println("log length: " + eventList.size());
            System.out.println(eventList.toString());
            for (MyTextEvent m : eventList){
                doMTE(m);
                System.out.println("log length: " + eventList.size());
            }
        }
        else if (mte instanceof LocalhostEvent){
            localhostAddress = ((LocalhostEvent) mte).getLocalhostAddress();
        }
        else if (mte instanceof ConnectToEvent){
            System.out.println("TRYING TO CONNECT IN 2!!!");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            owner.setIpaddressString(((ConnectToEvent) mte).getNewAddress());
            owner.connect();

        }
        else if (mte instanceof CreateServerEvent){
            owner.listen();
        }
    }

    /*
     * En metode der udfører RemoveEvents.
     * Hvis eventets offset eller længde ikke passer i dokumentet bliver der ikek slettet noget.
     */
    private void safelyRemoveRange(TextRemoveEvent tre) {
        try {
            turnOff();
            if (tre.getOffset() >= 0 && (tre.getOffset()+tre.getLength()) <= area.getText().length()) {
                tre.setRemovedText(area.getText(tre.getOffset(), tre.getLength()));
                area.replaceRange(null, tre.getOffset(), tre.getOffset() + tre.getLength());
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

    /*
     * Tænder for trådene der ændrer på loggen. Omvendt af turnOff.
     */
    private void turnOn() {
        setEventHistoryActive(true);
        oer.setEventListActive(true);
        dec.setActive(true);
    }

    public void waitForOneSecond(){
        try{
            Thread.sleep(1000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public ArrayList<MyTextEvent> getEventList (){
        return eventList;
    }

    private void setEventHistoryActive(boolean active){
        this.eventHistoryActive = active;
    }

    /*
     * Sætter at denne InputEventReplayer er lavet af en server og opdaterer listen over serverens OurputEventReplayers.
     */
    public void setFromServer(boolean fromServer, ArrayList<OutputEventReplayer> list) {
        isFromServer = fromServer;
        outputList = list;
    }

    public void setOwner(DistributedTextEditor owner) {
        this.owner = owner;
    }

    public void setClient(ChatClient client) {
        this.client = client;
    }

    public OutputEventReplayer getOer(){
        return oer;
    }

    public String getLocalhostAddress() {
        return localhostAddress;
    }

    public String getSender (){
        return sender;
    }

    public void addToLog(MyTextEvent mte) {
        if (eventHistoryActive) {
            eventList.add(mte);
            System.out.println("log add");
        }
    }
}