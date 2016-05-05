import javax.swing.*;
import java.net.*;
import java.io.*;

/**
 *
 * A very simple server which will way for a connection from a client and print 
 * what the client sends. When the client closes the connection, the server is
 * ready for the next client.
 */

public class ChatServer implements Runnable{


    private DocumentEventCapturer serverDec;
    private JTextArea serverArea2;

    public ChatServer(DocumentEventCapturer serverDec, JTextArea serverArea2) {
        this.serverDec = serverDec;
        this.serverArea2 = serverArea2;
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
            serverSocket = null;
            System.err.println("Cannot open server socket on port number" + portNumber);
            System.err.println(e);
            System.exit(-1);
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

        socket = waitForConnectionFromClient();

        createIOStreams(socket, serverDec, serverArea2);

        while (true) {
            socket = waitForConnectionFromClient();


            if (socket != null) {
                System.out.println("Connection from " + socket);

            } else {
                // We rather agressively terminate the server on the first connection exception
                break;
            }
        }

        deregisterOnPort();

        System.out.println("Goodbuy world!");
    }

    /*
     * sets up Replayers and Threads to run them. The Replayers sends and reads textevents to communicate with the client.
     */
    private void createIOStreams(Socket socket, DocumentEventCapturer serverDec, JTextArea serverArea2) {
        if(oepThread.isAlive()) {
            oepThread.interrupt();
        }
        if(iepThread.isAlive()) {
            iepThread.interrupt();
        }
        OutputEventReplayer oep = new OutputEventReplayer(serverDec, socket, null);
        InputEventReplayer iep = new InputEventReplayer(serverDec, serverArea2, socket, oep);
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
     * An attempt at disconnecting. Deregisters on the port and interrupts the streams. Works with both client and server disconnects.
     */
    public void disconnect() {
        deregisterOnPort();
        iepThread.interrupt();
        oepThread.interrupt();
    }
}