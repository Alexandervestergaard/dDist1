import javax.swing.*;
import java.net.*;
import java.io.*;
import java.util.ArrayList;

/**
 *
 * A very simple server which will way for a connection from a client and print 
 * what the client sends. When the client closes the connection, the server is
 * ready for the next client.
 */

public class ChatServer implements Runnable{


    private DocumentEventCapturer serverDec;
    private JTextArea serverArea;
    private DistributedTextEditor owner;
    private boolean interrupted = false;
    private ArrayList<MyTextEvent> startingList;

    public ChatServer(DocumentEventCapturer serverDec, JTextArea serverArea, String sender, DistributedTextEditor owner/*, ArrayList<MyTextEvent> startingList*/) {
        this.serverDec = serverDec;
        this.serverArea = serverArea;
        this.sender = sender;
        serverDec.setServer(this);
        this.owner = owner;
        //this.startingList = startingList;
    }

    /*
         * Your group should use port number 40HGG, where H is your "hold nummer (1,2 or 3)
         * and GG is gruppe nummer 00, 01, 02, ... So, if you are in group 3 on hold 1 you
         * use the port number 40103. This will avoid the unfortunate situation that you
         * connect to each others servers.
         */

    /*
     * Variables that will be used by the program. socket is the Socket that is used for communication.
     * iep and oep are InputEventReplayers and OutputEventReplayer respectively. They will be used to send events.
     */
    protected int portNumber = 40604;
    protected ServerSocket serverSocket = null;
    protected Socket res = null;
    protected Socket socket = null;
    protected String localhostAddress = "0";
    public BufferedReader fromClient;
    private Thread iepThread = new Thread();
    private Thread oepThread = new Thread();
    //En liste over alle OutputEventReplayers
    private ArrayList<OutputEventReplayer> outputList = new ArrayList<OutputEventReplayer>();
    //En liste over all InoutEventReplayers
    private ArrayList<InputEventReplayer> updateList = new ArrayList<InputEventReplayer>();
    //Et ID sombliver givet fra teksteditoren
    private final String sender;
    private ArrayList<MyTextEvent> serverLog = new ArrayList<MyTextEvent>();


    /**
     *
     * Will print out the IP address of the local host and the port on which this
     * server is accepting connections.
     */
    protected void printLocalHostAddress() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            localhostAddress = localhost.getHostAddress();
            System.out.println("Contact this server on the IP address " + localhostAddress);
        } catch (UnknownHostException e) {
            System.err.println("Cannot resolve the Internet address of the local host.");
            System.err.println(e);
            System.exit(-1);
        }
    }

    /**
     *
     * Will register this server on the port number portNumber. Will not start waiting
     * for connections. For this you should call waitForConnectionFromClient().
     */
    protected void registerOnPort() {
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (IOException e) {
            //serverSocket = null;
            System.err.println("Cannot open server socket on port number" + portNumber);
            System.err.println(e);
            //System.exit(-1);
            interrupted = true;
        }
    }

    /*
     * Deregisters on the port by closing it and clearing the variable.
     */
    public void deregisterOnPort() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                System.err.println(e);
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * Waits for the next client to connect on port number portNumber or takes the
     * next one in line in case a client is already trying to connect. Returns the
     * socket of the connection, null if there were any failures.
     */
    protected Socket waitForConnectionFromClient() {
        res = null;
        try {
            res = serverSocket.accept();
        } catch (IOException e) {
            // We return null on IOExceptions
            e.printStackTrace();
        }
        return res;
    }

    /*
     * Modified code from DemoServer. Starts the server by creating and running threads with streams that will be used
     * to communicate with the client. Also registers on the socket.
     */
    public void run() {
        System.out.println("Hello world!");

        printLocalHostAddress();

        registerOnPort();

        /*
         * Venter på at en client forbinder sig og opretter streams. Efter en forbindelse er oprettet venter den på
         * at der kommer flere.
         */
        while (!interrupted) {
            socket = waitForConnectionFromClient();
            createIOStreams(socket, serverDec, serverArea);


            if (socket != null) {
                System.out.println("Connection from " + socket);

            } else {
                // We rather agressively terminate the server on the first connection exception
                break;
            }
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        disconnect();

        owner.connect();
        System.out.println("Goodbuy world!");
    }

    /*
     * Sets up Replayers and Threads to run them. The Replayers sends and reads textevents to communicate with the client.
     * Tilføjer også streams til listerne.
     */
    private void createIOStreams(Socket socket, DocumentEventCapturer serverDec, JTextArea serverArea) {
        //OutputEventReplayer del
        OutputEventReplayer oep = new OutputEventReplayer(serverDec, socket, null);
        oep.setIsFromServer(true);
        //Tilføjer outputeventreplayer til listen.
        outputList.add(oep);

        /*
         * Hvis listen men InputEventReplayers ikke er tom må der allerede være en forbindelse. Denne inputEventReplayer
         * kan antages at have en opdateret log (eventHistory). Loggen fra den første forbindelse bliver indsat
         * i køen for den nye OutPuteventReplayer. På den måde sikrer vi os at den bliver sendt først så den nye
         * client bliver opdateret.
         */

        removeDeadInput();
        System.out.println("updateList-size: " + updateList.size());
        if (!updateList.isEmpty()) {
            System.out.println("adding logevent");
            oep.forcedQueueAdd(new UpToDateEvent(-1, serverDec.getTimeStamp(), sender, updateList.get(0).getEventList()));
        }
        else {
            //oep.forcedQueueAdd(new UpToDateEvent(-1, serverDec.getTimeStamp(), sender, startingList));
        }

        //InputEventReplayer del
        InputEventReplayer iep = new InputEventReplayer(serverDec, serverArea, socket, oep, sender);
        iep.setFromServer(true, outputList);
        updateList.add(iep);

        //Sætter OutputEventReplayers InputEventReplayer så den kan tilføje elementer til loggen.
        oep.setIep(iep);
        oepThread = new Thread(oep);
        iepThread = new Thread(iep);
        oepThread.start();
        try {
            Thread.sleep(1000);                 //1000 milliseconds is one second.
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        iepThread.start();

        /*
         * Opdaterer outPutListen på alle InputEventReplayers ( en for hver forbindelse) så de kender alle serverens
         * OutputEventReplayers og kan sende beskeder til alle clients.
         */
        for (InputEventReplayer update : updateList){
            update.setFromServer(true, outputList);
        }
    }

    private void removeDeadInput() {
        int maxLogLength = 0;
        ArrayList<InputEventReplayer> tempRemoveList = new ArrayList<InputEventReplayer>();
        for (InputEventReplayer i : updateList){
            maxLogLength = Math.max(i.getEventList().size(), maxLogLength);
        }
        for (InputEventReplayer i2 : updateList) {
            if (i2.getEventList().size() < maxLogLength - 1) {
                tempRemoveList.add(i2);
            }
        }
        for (InputEventReplayer remove : tempRemoveList){
            outputList.remove(remove.getOer());
            updateList.remove(remove);
        }
    }

    /*
     * An attempt at disconnecting. Deregisters on the port and interrupts the streams. Works with both client and server disconnects.
     */
    public void disconnect() {
        deregisterOnPort();
        iepThread.interrupt();
        oepThread.interrupt();
    }

    public ArrayList<OutputEventReplayer> getOutputList(){
        return outputList;
    }

    public ArrayList<MyTextEvent> getServerLog() {
        return serverLog;
    }

    public void addToServerLog(MyTextEvent newEvent) {
        if (serverLog.contains(newEvent)){return;}
        this.serverLog.add(newEvent);
    }
}