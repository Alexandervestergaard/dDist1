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

    private final InputWriter inputWriter;
    private Socket socket;
    private ObjectInputStream ois;
    private OutputEventReplayer oer;
    private final String sender;
    private boolean isFromServer = false;
    private ArrayList<OutputEventReplayer> outputList;
    private DistributedTextEditor owner;
    private ChatClient client;
    private String localhostAddress;

    public InputEventReplayer(DocumentEventCapturer dec, JTextArea area, Socket socket, OutputEventReplayer oer, String sender, InputWriter inputWriter) {
        this.socket = socket;
        this.sender = sender;
        this.oer = oer;
        this.inputWriter = inputWriter;
        System.out.println("Inputstream created and event queing thread started");
    }

    public void run() {
        while (true) {
            try {
                ois = new ObjectInputStream(socket.getInputStream());
                MyTextEvent mte;
                while ((mte = (MyTextEvent) ois.readObject()) != null) {
                    System.out.println("my id: " + sender + " mte id: " + mte.getSender() + " says EventQueueThread and: " + (mte.getSender() != sender));
                    if (!mte.getSender().equals(sender)) {
                        System.out.println("mte being added to event queue: " + mte);
                        if (!isFromServer) {
                            inputWriter.addToQueue(mte);
                            System.out.println("added mte to queue");
                        }
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

    public void addToLog(MyTextEvent mte){
        inputWriter.addToLog(mte);
    }
}