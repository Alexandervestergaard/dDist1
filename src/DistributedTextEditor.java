
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.*;

public class DistributedTextEditor extends JFrame {

    private JTextArea area1 = new JTextArea(20,120);
    private JTextArea area2 = new JTextArea(20,120);
    private JTextField ipaddress = new JTextField("IP address here");
    private JTextField portNumber = new JTextField("Port number here");
    private int portNumberInteger = 40604;

    private EventReplayer er;
    private Thread ert;

    private JFileChooser dialog =
            new JFileChooser(System.getProperty("user.dir"));

    private String currentFile = "Untitled";
    private boolean changed = false;
    private boolean connected = false;
    private DocumentEventCapturer dec = new DocumentEventCapturer();

    protected ServerSocket serverSocket;

    public DistributedTextEditor() {
        area1.setFont(new Font("Monospaced",Font.PLAIN,12));

        area2.setFont(new Font("Monospaced",Font.PLAIN,12));
        ((AbstractDocument)area1.getDocument()).setDocumentFilter(dec);
        area2.setEditable(false);

        Container content = getContentPane();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JScrollPane scroll1 =
                new JScrollPane(area1,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        content.add(scroll1,BorderLayout.CENTER);

        JScrollPane scroll2 =
                new JScrollPane(area2,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        content.add(scroll2,BorderLayout.CENTER);

        content.add(ipaddress,BorderLayout.CENTER);
        content.add(portNumber,BorderLayout.CENTER);

        JMenuBar JMB = new JMenuBar();
        setJMenuBar(JMB);
        JMenu file = new JMenu("File");
        JMenu edit = new JMenu("Edit");
        JMB.add(file);
        JMB.add(edit);

        file.add(Listen);
        file.add(Connect);
        file.add(Disconnect);
        file.addSeparator();
        file.add(Save);
        file.add(SaveAs);
        file.add(Quit);

        edit.add(Copy);
        edit.add(Paste);
        edit.getItem(0).setText("Copy");
        edit.getItem(1).setText("Paste");

        Save.setEnabled(false);
        SaveAs.setEnabled(false);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        area1.addKeyListener(k1);
        setTitle("Disconnected");
        setVisible(true);
        area1.insert("Example of how to capture stuff from the event queue and replay it in another buffer.\n" +
                "Try to type and delete stuff in the top area.\n" +
                "Then figure out how it works.\n", 0);

        er = new EventReplayer(dec, area2);
        ert = new Thread(er);
        ert.start();
    }

    private KeyListener k1 = new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
            changed = true;
            Save.setEnabled(true);
            SaveAs.setEnabled(true);
        }
    };

    Action Listen = new AbstractAction("Listen") {
        public void actionPerformed(final ActionEvent e) {
            saveOld();
            area1.setText("");
            // TODO: Become a server listening for connections on some port.
            //Homemade code start
            System.out.println("Hello world!");

            printLocalHostAddress();

            registerOnPort();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        final Socket socket = waitForConnectionFromClient();

                        if (socket != null) {
                            setTitle("Connection from " + socket);
                            new Thread(new Runnable() {
                                public void run() {
                                    try {
                                        // For reading from standard input
                                        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
                                        // For sending text to the server
                                        PrintWriter toServer = new PrintWriter(socket.getOutputStream(), true);
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
                            }).start();

                            try {
                                BufferedReader fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                String s;
                                // Read and print what the client is sending
                                while ((s = fromClient.readLine()) != null) { // Ctrl-D terminates the connection
                                    area1.insert("From the client: " + s, 0);
                                }
                                socket.close();
                            } catch (IOException ex) {
                                // We report but otherwise ignore IOExceptions
                                System.err.println(e);
                            }
                            System.out.println("Connection closed by client.");
                        } else {
                            // We rather agressively terminate the server on the first connection exception
                            break;
                        }
                        //Homemade code end
                        changed = false;
                        Save.setEnabled(false);
                        SaveAs.setEnabled(false);
                    }
                }
            }).start();

        }
    };

    protected String printLocalHostAddress() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String localhostAddress = localhost.getHostAddress();
            setTitle("IP Adress = " + localhostAddress + " And Portnumber = " + portNumberInteger);
            return localhostAddress;
        } catch (UnknownHostException e) {
            System.err.println("Cannot resolve the Internet address of the local host.");
            System.err.println(e);
            System.exit(-1);
            return "fejl";
        }
    };

    public void deregisterOnPort() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    };

    protected Socket waitForConnectionFromClient() {
        Socket res = null;
        try {
            res = serverSocket.accept();
        } catch (Exception e) {

        }
        return res;
    };

    protected void registerOnPort() {
        try {
            serverSocket = new ServerSocket(portNumberInteger);
        } catch (IOException e) {
            serverSocket = null;
            System.err.println("Cannot open server socket on port number" + portNumberInteger);
            System.err.println(e);
            System.exit(-1);
        }
    };

    Action Connect = new AbstractAction("Connect") {
        public void actionPerformed(ActionEvent e) {
            saveOld();
            area1.setText("");
            //setTitle("Connecting to " + ipaddress.getText() + ":" + portNumberInteger + "...");
            changed = false;
            Save.setEnabled(false);
            SaveAs.setEnabled(false);
            System.out.println("Hello world!");
            System.out.println("Type CTRL-D to shut down the client.");

            final String serverName = ipaddress.getText();
            final Socket socket = connectToServer(serverName);

            if (socket != null) {
                setTitle("Connected to " + socket);
                new Thread(new Runnable() {
                    public void run() {
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
                    }}).start();

                try {
                    // For reading from standard input
                    final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
                    // For sending text to the server
                    final PrintWriter toServer = new PrintWriter(socket.getOutputStream(),true);
                    final String[] s = new String[1];
                    // Read from standard input and send to server
                    // Ctrl-D terminates the connection
                    System.out.print("Type something for the server and then RETURN> ");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while ((s[0] = stdin.readLine()) != null && !toServer.checkError()) {
                                    System.out.print("Type something for the server and then RETURN> ");
                                    toServer.println(s[0]);
                                }
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }).start();

                    socket.close();
                } catch (IOException ex) {
                    // We ignore IOExceptions
                }
            }
            else {
                setTitle("failed to connect");
            }

        }
    };

    protected Socket connectToServer(String serverName) {
        Socket res = null;
        try {
            res = new Socket(serverName,40604);
        } catch (IOException e) {
            // We return null on IOExceptions
        }
        return res;
    };

    Action Disconnect = new AbstractAction("Disconnect") {
        public void actionPerformed(ActionEvent e) {
            setTitle("Disconnected");
            // TODO
        }
    };

    Action Save = new AbstractAction("Save") {
        public void actionPerformed(ActionEvent e) {
            if(!currentFile.equals("Untitled"))
                saveFile(currentFile);
            else
                saveFileAs();
        }
    };

    Action SaveAs = new AbstractAction("Save as...") {
        public void actionPerformed(ActionEvent e) {
            saveFileAs();
        }
    };

    Action Quit = new AbstractAction("Quit") {
        public void actionPerformed(ActionEvent e) {
            saveOld();
            System.exit(0);
        }
    };

    ActionMap m = area1.getActionMap();

    Action Copy = m.get(DefaultEditorKit.copyAction);
    Action Paste = m.get(DefaultEditorKit.pasteAction);

    private void saveFileAs() {
        if(dialog.showSaveDialog(null)==JFileChooser.APPROVE_OPTION)
            saveFile(dialog.getSelectedFile().getAbsolutePath());
    }

    private void saveOld() {
        if(changed) {
            if(JOptionPane.showConfirmDialog(this, "Would you like to save "+ currentFile +" ?","Save",JOptionPane.YES_NO_OPTION)== JOptionPane.YES_OPTION)
                saveFile(currentFile);
        }
    }

    private void saveFile(String fileName) {
        try {
            FileWriter w = new FileWriter(fileName);
            area1.write(w);
            w.close();
            currentFile = fileName;
            changed = false;
            Save.setEnabled(false);
        }
        catch(IOException e) {
        }
    }

    public static void main(String[] arg) {
        new DistributedTextEditor();
    }

}