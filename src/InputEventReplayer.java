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
 * them in a JTextArea.
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
    private ArrayList<MyTextEvent> eventList;
    private ReentrantLock rollBackLock = new ReentrantLock();
    private Comparator<? super MyTextEvent> mteSorter;
    private boolean eventHistoryActive = true;
    private final String sender;
    private boolean isFromServer = false;
    private ArrayList<OutputEventReplayer> outputList;
    private DistributedTextEditor owner;
    private ChatClient client;
    private String localhostAddress;
    private PriorityBlockingQueue<MyTextEvent> waitingToGoToLogQueue = new PriorityBlockingQueue<MyTextEvent>();
    private boolean interrupted = false;

    /*
     * sender variablen er et id som man får fra DistributedTextEditor.
     * mteSorter er en sorter der skal bruges til at sortere MyTextEvents i rollback.
     */
    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket, OutputEventReplayer oer, String sender) {
        this.dec = dec;
        this.area = area;
        this.socket = socket;
        this.sender = sender;
        eventHistory = new PriorityBlockingQueue<MyTextEvent>();
        this.oer = oer;
        this.eventList = new ArrayList<MyTextEvent>();
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
     * En tråd bruges til løbende at hive objekter ud fra streamen, og gemme dem i eventHistory-køen.
     * Hvis denne InputEventReplayer er lavet af en server skal events sendes videre till alle clients. Dette gør
     * at den client som har sendt eventet får det tilbage igen, og det er derfor vigtigt at ignorere events med samme
     * id som ens eget.
     */
    private void startEventQueThread() {
        if(EventQueThread.isAlive()) {
            EventQueThread.interrupt();
        }
        EventQueThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!socket.isClosed()) {
                        ois = new ObjectInputStream(socket.getInputStream());
                    }
                    while (!interrupted) {
                        MyTextEvent mte;
                        while (ois != null && (mte = (MyTextEvent) ois.readObject()) != null) {
                            if (!mte.getSender().equals(sender)) {
                                eventHistory.add(mte);
                                if (isFromServer && !(mte instanceof Unlogable)) {
                                    for (OutputEventReplayer oer : outputList) {
                                        System.out.println("Adding to forcequeue");
                                        oer.forcedQueueAdd(mte);
                                    }
                                }
                            }
                            mte = null;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        });
        EventQueThread.start();
    }

    /*
     * En tråd der tilføjer MyTextEvents til loggen når man ikke er i gang med rollback. Bliver primært brugt
     * når man skal tilføje sit eget output til loggen.
     */
    public void startAddToLogThread(){
        Thread addToLogThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!interrupted) {
                    try {
                        MyTextEvent addToLogEvent = waitingToGoToLogQueue.take();
                        while (!eventHistoryActive) {
                        }
                        if (!(addToLogEvent instanceof Unlogable) && !eventList.contains(waitingToGoToLogQueue)) {
                            eventList.add(addToLogEvent);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        addToLogThread.start();
    }

    /*
     * En tråd der tager MyTextEvents ud af eventHistrory, som er køen fra den anden client/server.
     * Afhængig af eventets timestamp, køres eventet normalt og tilføjes til loggen eventList,
     * eller også bliver rollback metoden kaldt med eventets timstamp og eventet selv.
     */
    public void run() {
        startAddToLogThread();
        while (!interrupted) {
            try {
                /*
                MyTextEvent-objekter hives ud af eventHistory, meget lig EventReplayer
                 */
                if (eventHistoryActive) {
                    final MyTextEvent mte = eventHistory.take();
                    System.out.println("my id: " + sender + " mte id: " + mte.getSender());
                    if (mte.getTimeStamp() >= dec.getTimeStamp()) {
                        dec.setTimeStamp(mte.getTimeStamp() + 1);
                        if (!eventList.contains(mte) && !(mte instanceof Unlogable)) {
                            eventList.add(mte);
                        }
                        doMTE(mte);
                    } else {
                        rollback(mte.getTimeStamp(), mte);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                interrupted = true;
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

            ArrayList<MyTextEvent> rollbackList = eventList;

            // Loop der printer indholdet af loggen
            System.out.println("list:");
            for (MyTextEvent q: rollbackList){
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
            Collections.reverse(rollbackList);
            for (MyTextEvent undo : eventList) {
                if (undo.getTimeStamp() >= rollbackTo) {
                    undoEvent(undo);
                    tempList.add(undo);
                }
            }
            tempList.add(rollMTE);
            tempList.sort(mteSorter);

            if (!eventList.contains(rollMTE) && !(rollMTE instanceof Unlogable)) {
                eventList.add(rollMTE);
            }
            eventList.sort(mteSorter);

            // Loope der printer og udfører indholdet af tempList
            System.out.println("tempList: ");
            for (MyTextEvent m : tempList){
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
     * Hvis det er et RemoveEvent bliver safelyRemoveRange kaldt.     *
     */
    private void doMTE(MyTextEvent mte) {
        if (mte instanceof TextInsertEvent) {
            final TextInsertEvent tie = (TextInsertEvent)mte;
            try {
                System.out.println("tie in event queue, trying to write to area");
                System.out.println("offset: " + mte.getOffset());
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
         * Når man får et UpToDateEvent sletter man alt i sit tekstfelt og udfører alle events i den nye log.
         */
        else  if(mte instanceof UpToDateEvent){
            eventList = ((UpToDateEvent) mte).getLog();
            area.setText("");
            System.out.println("log length: " + eventList.size());
            System.out.println(eventList.toString());
            eventList.sort(mteSorter);
            for (MyTextEvent m : eventList){
                doMTE(m);
                System.out.println("log length: " + eventList.size());
            }
        }
        /*
         * Et LocalhostEvent sætter localhostAddressvariablen som bliver brugt til elections.
         */
        else if (mte instanceof LocalhostEvent){
            localhostAddress = ((LocalhostEvent) mte).getLocalhostAddress();
        }
        /*
         * Et ConnectToEvent sætter tråden til at vente i 2 seunder hvorefter man forbinder sig til den nye ip.
         */
        else if (mte instanceof ConnectToEvent){
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
     * Hvis eventets offset er indenfor teksten med er for langt, bliver teksten fra det offset og frem til slutningen
     * af teksten slettet.
     */
    private void safelyRemoveRange(TextRemoveEvent tre) {
        try {
            turnOff();
            if (tre.getOffset() >= 0 && (tre.getOffset() + tre.getLength()) <= area.getText().length()) {
                tre.setRemovedText(area.getText(tre.getOffset(), tre.getLength()));
                area.replaceRange(null, tre.getOffset(), tre.getOffset() + tre.getLength());
            }
            turnOn();
        } catch (Exception e) {
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

    /*
     * Klargører events til at blive indsat i loggen når den er klar. Loggen accepterer ikke duplikater og events
     * som implementerer Unlogable de disse ikke skal med i loggen.
     */
    public void addToLog(MyTextEvent mte) {
        if (!(mte instanceof Unlogable) && !eventList.contains(mte)) {
            waitingToGoToLogQueue.add(mte);
        }
    }

    public Socket getSocket(){
        return socket;
    }

    public void setEventList(ArrayList<MyTextEvent> newEventList){
        this.eventList = newEventList;
    }

    public void stopThreads() {
        interrupted = true;
    }

    public void close() {
        try {
            ois.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}