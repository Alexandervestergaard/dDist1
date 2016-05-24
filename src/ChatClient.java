
import javax.swing.*;
import java.net.*;
        import java.io.*;

/**
 *
 * A very simple client which will connect to a server, read from a prompt and
 * send the text to the server.
 */

public class ChatClient implements Runnable {

    /*
     * Your group should use port number 40HGG, where H is your "hold nummer (1,2 or 3)
     * and GG is gruppe nummer 00, 01, 02, ... So, if you are in group 3 on hold 1 you
     * use the port number 40103. This will avoid the unfortunate situation that you
     * connect to each others servers.
     */
    private int portNumber = 40604;
    private String serverName;
    protected Socket res = null;
    private Thread iepThread = new Thread();
    private Thread oepThread = new Thread();
    private DocumentEventCapturer clientDec;
    private JTextArea clientArea;
    private String localhostAddress;
    protected Socket socket = null;
    //Et ID som bliver sendt fra texteditoren
    private final String sender;
    private DistributedTextEditor owner;
    private InputWriter clientWriter;
    private Thread clientWriterThread;

    public ChatClient(String serverName, String portNumber, DocumentEventCapturer clientDec, JTextArea clientArea, String sender, DistributedTextEditor owner) {
        this.serverName = serverName;
        this.portNumber = Integer.parseInt(portNumber);
        this.clientDec = clientDec;
        this.clientArea = clientArea;
        this.sender = sender;
        this.owner = owner;
        clientDec.setServer(null);
        this.clientWriter = new InputWriter(clientDec, clientArea, sender);
    }

    /**
     *
     * Will print out the IP address of the local host on which this client runs.
     */
    protected void printLocalHostAddress() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            localhostAddress = localhost.getHostAddress();
            System.out.println("I'm a client running with IP address " + localhostAddress);
        } catch (UnknownHostException e) {
            System.err.println("Cannot resolve the Internet address of the local host.");
            System.err.println(e);
            System.exit(-1);
        }
    }

    /**
     *
     * Connects to the server on IP address serverName and port number portNumber.
     */
    protected Socket connectToServer(String serverName) {
        res = null;
        try {
            res = new Socket(serverName,portNumber);
        } catch (IOException e) {
            // We return null on IOExceptions
        }
        return res;
    }

    /*
     * Modified code from DEmoClient. Sets up a connection with the server by creating the streams that will be used to communicate.
     * Also sets up the socket.
     */
    public void run() {
        printLocalHostAddress();

        socket = connectToServer(serverName);

        createIOStreams(socket, clientDec, clientArea);

        if (socket != null) {
            System.out.println("Connected to " + socket);
        }

        System.out.println("Goodbuy world!");
    }

    /*
     * An attempt at disconnectiing. Closes the socket, stops teh threads with the streams and clears the variables.
     * Works when both client and server disconnects.
     */
    public void disconnect(){
        try {
            iepThread.interrupt();
            oepThread.interrupt();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = null;
        res = null;
    }

    /*
     * sets up Replayers and Threads to run them. The Replayers sends and reads textevents to communicate with the client.
     */
    private void createIOStreams(Socket socket, DocumentEventCapturer clientDec, JTextArea clientArea) {
        if(oepThread.isAlive()) {
            oepThread.interrupt();
        }
        if(iepThread.isAlive()) {
            iepThread.interrupt();
        }
        OutputEventReplayer oep = new OutputEventReplayer(clientDec, socket, null, this);
        InputEventReplayer iep = new InputEventReplayer(clientDec, clientArea, socket, oep, sender, clientWriter);
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
        iep.setOwner(owner);
        iep.setClient(this);
        this.clientWriterThread = new Thread(clientWriter);
        clientWriterThread.start();
    }

    public String getLocalhostAddress(){
        return localhostAddress;
    }
}