
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;
import javax.swing.text.*;

public class DistributedTextEditor extends JFrame {

    private JTextArea area1 = new JTextArea(10,120);
    private JTextArea area2 = new JTextArea(10,120);
    private JTextField ipaddress = new JTextField("192.168.87.102");
    private JTextField portNumber = new JTextField("40604");
    private String localhostAddress = "";

    private EventReplayer er;
    private Thread ert;

    private JFileChooser dialog =
            new JFileChooser(System.getProperty("user.dir"));

    private String currentFile = "Untitled";
    private boolean changed = false;
    private boolean connected = false;
    private DocumentEventCapturer dec;

    private ChatClient client;
    private Thread clientThread;
    private ChatServer server;
    private Thread serverThread;
    //Et ID som bliver givet til server og client for at holde styr på hvem der har sendt hvad
    private String id;

    private DistributedTextEditor me = this;

    //private ArrayList<MyTextEvent> startingList = new ArrayList<MyTextEvent>();

    public DistributedTextEditor() {
        //Genererer et ID som er en tilfældig long for at undgå ens ID
        Random idGenerator = new Random();
        id = String.valueOf(idGenerator.nextLong());
        dec = new DocumentEventCapturer(id);



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
        edit.add(CopyIP);
        edit.getItem(0).setText("Copy");
        edit.getItem(1).setText("Paste");
        edit.getItem(2).setText("CopyIP");

        Save.setEnabled(false);
        SaveAs.setEnabled(false);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        area1.addKeyListener(k1);
        setTitle("Disconnected");
        setVisible(true);
    }

    private KeyListener k1 = new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
            changed = true;
            Save.setEnabled(true);
            SaveAs.setEnabled(true);
        }
    };

    /*
     * Sets up a server. Starts a Thread with the server.
     */
    Action Listen = new AbstractAction("Listen") {
        public void actionPerformed(ActionEvent e) {
            listen();
        }
    };

    public void listen() {
        disconnect();
        //Tænder for dec da man nu skal bruge den
        dec.setStarted(true);
        server = new ChatServer(dec, area1, id, me/*, startingList*/);
        serverThread = new Thread(server);
        serverThread.start();
        try {
            Thread.sleep(1000);                 //1000 milliseconds is one second.
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Server running");
        localhostAddress = server.localhostAddress;
        System.out.println("localhost address: " + localhostAddress);
        setTitle("I'm listening on " + localhostAddress);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(localhostAddress), new StringSelection(localhostAddress));
        //area1.setText("IP address copied to clip-board");


        changed = true;
        Save.setEnabled(true);
        SaveAs.setEnabled(true);
    }

    /*
     * Sets up a client and starts a Thread with the client.
     */
    Action Connect = new AbstractAction("Connect") {
        public void actionPerformed(ActionEvent e) {
            connect();
        }
    };

    public void connect() {
        disconnect();
        //saveOld();
        setTitle("Connecting to " + ipaddress.getText() + ":" + portNumber.getText() + "...");
        //Tænder for dec da man nu skal bruge den
        //area1.setText("");
        dec.setStarted(true);
        client = new ChatClient(ipaddress.getText(), portNumber.getText(), dec, area1, id, me);
        clientThread = new Thread(client);
        clientThread.start();
        try {
            Thread.sleep(1000);                 //1000 milliseconds is one second.
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        changed = true;
        Save.setEnabled(true);
        SaveAs.setEnabled(true);
    }

    /*
     * An attempt at disconnecting. Detects wether it is currently set to listen or connect.
     * Then changes the title and calls the disconnectmethod on the server or client.
     */
    Action Disconnect = new AbstractAction("Disconnect") {
        public void actionPerformed(ActionEvent e) {
            disconnect();
        }
    };

    private void disconnect() {
        setTitle("Disconnected");
        if (server != null){
            server.disconnect();
            server = null;
            serverThread.interrupt();
            area1.setText("");
            area2.setText("");
        }
        else if(client != null){
            client.disconnect();
            client = null;
            clientThread.interrupt();
            area1.setText("");
            area2.setText("");
        }
    }

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
    Action CopyIP = new AbstractAction("CopyIP") {
        @Override
        public void actionPerformed(ActionEvent e) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(localhostAddress), new StringSelection(localhostAddress));
        }
    };


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

    /*public void setStartingList(ArrayList<MyTextEvent> startingList) {
        this.startingList = startingList;
    }*/

    public void setIpaddressString(String newAddress){
        ipaddress.setText(newAddress);
    }
}

