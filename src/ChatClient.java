
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
    private JTextArea clientArea2;
    private String localhostAddress;
    protected Socket socket = null;

    public ChatClient(String serverName, String portNumber, DocumentEventCapturer clientDec, JTextArea clientArea2) {
        this.serverName = serverName;
        this.portNumber = Integer.parseInt(portNumber);
        this.clientDec = clientDec;
        this.clientArea2 = clientArea2;
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

    public void run() {
        System.out.println("Hello world!");
        System.out.println("Type CTRL-D to shut down the client.");

        printLocalHostAddress();

        socket = connectToServer(serverName);

        createIOStreams(socket, clientDec, clientArea2);

        if (socket != null) {
            System.out.println("Connected to " + socket);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        socket = connectToServer(serverName);

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
                }}).start();

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
        }

        System.out.println("Goodbuy world!");
    }

    private void createIOStreams(Socket socket, DocumentEventCapturer clientDec, JTextArea clientArea2) {
        if(oepThread.isAlive()) {
            oepThread.interrupt();
        }
        if(iepThread.isAlive()) {
            iepThread.interrupt();
        }
        OutputEventReplayer oep = new OutputEventReplayer(clientDec, socket);
        InputEventReplayer iep = new InputEventReplayer(clientDec, clientArea2, socket);
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