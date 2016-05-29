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
    /*
     * sender parameteren er et id som bliver lavet når man starter programmet.
     * owner parameteren er den DistributedTextEditor som laver clienten.
     */
    public ChatServer(DocumentEventCapturer serverDec, JTextArea serverArea, String sender, DistributedTextEditor owner) {
        this.serverDec = serverDec;
        this.serverArea = serverArea;
        this.sender = sender;
        serverDec.setServer(this);
        this.owner = owner;

        printLocalHostAddress();
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

    /*
     * Waits for the next client to connect on port number portNumber or takes the
     * next one in line in case a client is already trying to connect. Returns the
     * socket of the connection, null if there were any failures.
     */
    protected Socket waitForConnectionFromClient() {
        res = null;
        try {
            if (!serverSocket.isClosed()) {
                res = serverSocket.accept();
            }
        } catch (IOException e) {
            // We return null on IOExceptions
            e.printStackTrace();
        }
        return res;
    }

    /*
     * Starter med at lave en ServerSocket som clients kan forbidnde til.
     * Derefter venter serveren på ny forbindelser som den laver objekter til at kommunikere med.
     */
    public void run() {
        System.out.println("Hello world!");


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
        disconnect();

        System.out.println("Goodbuy world!");
    }

    /*
     * Sets up Replayers and Threads to run them. The Replayers sends and reads textevents to communicate with the client.
     * Tilføjer også streams til listerne.
     */
    private void createIOStreams(Socket socket, DocumentEventCapturer serverDec, JTextArea serverArea) {
        //OutputEventReplayer del
        OutputEventReplayer oep = new OutputEventReplayer(serverDec, socket, null, null);
        oep.setIsFromServer(true);
        //Tilføjer outputeventreplayer til listen.
        outputList.add(oep);

        /*
         * Hvis listen med InputEventReplayers ikke er tom må der allerede være en forbindelse. Denne inputEventReplayer
         * kan antages at have en opdateret log (eventHistory). Loggen fra den første forbindelse bliver indsat
         * i køen for den nye OutPuteventReplayer. På den måde sikrer vi os at den bliver sendt først så den nye
         * client bliver opdateret.
         * Hvis listen er tom er der ikke nogen clients forbundet og serveren sender et nyt tekstevent med tekstfeltets
         * nuværende tekst.
         * Først skal listen med forbindelser dog opdateres.
         */

        removeDeadInput();
        System.out.println("updateList-size: " + updateList.size());
        if (!updateList.isEmpty()) {
            System.out.println("adding logevent");
            System.out.println("Getting log og size: " + updateList.get(0).getEventList().size());
            oep.forcedQueueAdd(new UpToDateEvent(-1, serverDec.getTimeStamp(), sender, updateList.get(0).getEventList()));
        }
        else {
            oep.forcedQueueAdd(new TextInsertEvent(0,serverArea.getText(),serverDec.getTimeStamp(),sender));
        }

        //InputEventReplayer del
        InputEventReplayer iep = new InputEventReplayer(serverDec, serverArea, socket, oep, sender);
        if(!updateList.isEmpty()) {
            iep.setEventList(updateList.get(0).getEventList());
        }
        /*
         * Tilføjer InputEventReplayeren til listen af InputEventReplayers. Ved en ny forbindelse skal deres liste over
         * OutputEventReplayers opdateres så input kan sendes til alle clients.
         */

        updateList.add(iep);
        /*
         * Opdaterer outPutListen på alle InputEventReplayers (en for hver forbindelse) så de kender alle serverens
         * OutputEventReplayers og kan sende beskeder til alle clients.
         */
        for (InputEventReplayer update : updateList){
            update.setFromServer(true, outputList);
        }

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


    }

    /*
     * Rydder op i forbindelserne ved at sende en tom besked ud til alle clients. Hvis der komme en Exception er
     * forbindelsen ikke længere åben og streamsne fjernes fra listerne.
     */
    private void removeDeadInput() {
        System.out.println("Before size: " + updateList.size());
        ArrayList<OutputEventReplayer> tempRemoveList = new ArrayList<>();
        for (OutputEventReplayer o : outputList){
            try {
                o.getOos().writeObject(new TestAliveEvent(-1, serverDec.getTimeStamp(), sender));
            } catch (Exception e) {
                tempRemoveList.add(o);
                e.printStackTrace();
            }
        }
        for (OutputEventReplayer remove : tempRemoveList){
            System.out.println("Removing streams form lists");
            updateList.remove(remove.getIep());
            outputList.remove(remove);
        }
        System.out.println("After size: " + updateList.size());
    }

    /*
     * Ved disconnect bliver der først sendt besked til clients om at de skal lave ny forbindelser. Derefter lukker
     * serveren for al kommunikation.
     */
    public void disconnect() {
        notifyClients();
        deregisterOnPort();
        for (InputEventReplayer iep : updateList) {
            iep = null;
        }
        for (OutputEventReplayer oep : outputList) {
            oep = null;
        }
        interrupted = true;
    }

    /*
     * Den første client i listen får et CreateServerEvent som får den client til at kalde listen() metoden.
     * De andre får et ConnectToEvent med den første clients ip. De vil efter 2 sekunders forsinkelse forbinde til denne.
     * IP-adressen bliver sendt af clients som det første når de opretter en forbindelse. Den bliver gemt i den dertilhørende
     * InputEventReplayer.
     */
    private void notifyClients() {
        removeDeadInput();
        if (!outputList.isEmpty()){
            outputList.get(0).forcedQueueAdd(new CreateServerEvent(-1, serverDec.getTimeStamp(), sender));
        }
        String connectTo = updateList.get(0).getLocalhostAddress();
        System.out.println("Outputlist size: " + outputList.size());
        for (int i=1; i<outputList.size(); i++){
            System.out.println("sending connectevent");
            outputList.get(i).forcedQueueAdd(new ConnectToEvent(-1, serverDec.getTimeStamp(), sender, connectTo));
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (OutputEventReplayer close : outputList){
            close.getIep().close();
            close.close();
        }
    }

    public ArrayList<OutputEventReplayer> getOutputList(){
        return outputList;
    }

    public ArrayList<MyTextEvent> getServerLog() {
        return serverLog;
    }
}