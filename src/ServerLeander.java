import javax.swing.*;
import java.net.*;
import java.io.*;

/**
 *
 * A very simple server which will way for a connection from a client and print 
 * what the client sends. When the client closes the connection, the server is
 * ready for the next client.
 */

public class ServerLeander implements Runnable{


    private DocumentEventCapturer serverDec;
    private JTextArea serverArea2;

    public ServerLeander(DocumentEventCapturer serverDec, JTextArea serverArea2) {
        this.serverDec = serverDec;
        this.serverArea2 = serverArea2;
    }

    /*
         * Your group should use port number 40HGG, where H is your "hold nummer (1,2 or 3)
         * and GG is gruppe nummer 00, 01, 02, ... So, if you are in group 3 on hold 1 you
         * use the port number 40103. This will avoid the unfortunate situation that you
         * connect to each others servers.
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

    public void run() {
        System.out.println("Hello world!");

        printLocalHostAddress();

        registerOnPort();

        socket = waitForConnectionFromClient();

        createIOStreams(socket, serverDec, serverArea2);
        //deregisterOnPort();

        while (true) {
            socket = waitForConnectionFromClient();

            //createIOStreams(socket, dec, area2);


            if (socket != null) {
                System.out.println("Connection from " + socket);
                new Thread(new Runnable() {
                    public void run() {
                        try {


                            // For reading from standard input
                            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
                            // For sending text to the server
                            PrintWriter toServer = new PrintWriter(socket.getOutputStream(),true);
                            String s;
                            // Read from standard input and send to server
                            // Ctrl-D terminates the connection
                            System.out.print("Type something for the server and then RETURN> ");
                            while ((s = stdin.readLine()) != null && !toServer.checkError()) {
                                System.out.print("Type something for the server and then RETURN> ");
                                toServer.println(s);
                            }
                            socket.close();
                        } catch (IOException e) {
                            // We ignore IOExceptions
                        }
                    }}).start();

                try {
                    BufferedReader fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String s;
                    // Read and print what the client is sending
                    while ((s = fromClient.readLine()) != null) { // Ctrl-D terminates the connection
                        System.out.println("From the client: " + s);
                    }
                    socket.close();
                } catch (IOException e) {
                    // We report but otherwise ignore IOExceptions
                    System.err.println(e);
                }
                System.out.println("Connection closed by client.");
            } else {
                // We rather agressively terminate the server on the first connection exception
                break;
            }
        }

        deregisterOnPort();

        System.out.println("Goodbuy world!");
    }

    private void createIOStreams(Socket socket, DocumentEventCapturer serverDec, JTextArea serverArea2) {
        if(oepThread.isAlive()) {
            oepThread.interrupt();
        }
        if(iepThread.isAlive()) {
            iepThread.interrupt();
        }
        OutputEventReplayer oep = new OutputEventReplayer(serverDec, socket);
        InputEventReplayer iep = new InputEventReplayer(serverDec, serverArea2, socket);
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

}