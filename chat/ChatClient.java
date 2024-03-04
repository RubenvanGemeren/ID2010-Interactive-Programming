// ChatClient.java
// 2024-01-24/fki Version 8: no package, no Jini, no rmid
// 2018-08-22/fki Refactored for lab version 7
// 26-mar-2004/FK Small fixes
// 25-mar-2004/FK Given to its own package.
// 18-mar-2004/FK First version

// Standard JDK

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.*;

/**
 * This class implements the ChatClient application.
 */
public class ChatClient
        extends
        UnicastRemoteObject        // Since we accept remote calls
        implements
        RemoteEventListener // So we can receive chat notifications
{
    /**
     * Information string for the user. Printed by the help command.
     */
    protected static final String versionString = "fki-8.0";

    /**
     * Holds the names of found ChatServers.
     */
    protected ArrayList<String> servers = new ArrayList<>();

    /**
     * Refers to the service object of the currently connected chat-service.
     */
    protected ChatServerInterface myServer = null;

    /**
     * The name the user has choosen to present itself as.
     */
    protected String myName = null;

    public String getName() throws java.rmi.RemoteException {
        return myName;
    }

    /**
     * The user's current status. If true, the user is marked as away
     * from keyboard.
     */
    protected Boolean isAFK = false;

    /**
     * This is the time-out value for the user's status. If the user
     * does not send any messages for this many seconds, the user is
     * marked as away from keyboard.
     */
    protected Integer timeOut = 120;

    protected HashSet<String> blockedUsers;

    protected Boolean isRunning = false;

    protected boolean waitingForNameFromServer = false;

    /**
     * The timer object that is used to schedule the user's status change
     */
    Timer timer = new Timer();

    private TimerTask task;

    /* *** Constructor *** */

    /**
     * Creates a new ChatClient instance.
     */
    public ChatClient() throws RemoteException {
        blockedUsers = new HashSet<>();
        scanForChatServers();
    }

    /**
     * Scan the rmiregistry for services that name themselves ChatServer.
     * Save those names in the servers list.
     */
    protected void scanForChatServers() {
        try {

            // Get the default registry

            Registry registry = LocateRegistry.getRegistry(null);

            // Ask for all registered services

            String[] serviceNames = registry.list();

            // Save the names that starts with "ChatServer"

            servers.clear();

            for (String name : serviceNames) {
                if (name.startsWith("ChatServer"))
                    servers.add(name);
            }

        } catch (Exception e) {
            System.out.printf("[Scanning for servers failed: %s]\n",
                    e.toString());
            //e.printStackTrace();
        }
    }

    /* ***** Interface RemoteEventListener ***** */

    /**
     * The ChatServer we are registered with (connected to) calls this
     * method to notify us of a new chat message.
     *
     * @param rev The remote event that is the notification.
     */
    public void notify(RemoteEvent rev) throws RemoteException {
        if (rev instanceof ChatNotification) {
            ChatNotification chat = (ChatNotification) rev;
            if (waitingForNameFromServer){
                getNameFromServer(chat);
            }
            rename(chat);
            if (!isBlocked(chat)) {
                System.out.println(chat.getSequenceNumber() + " : " + chat.getText());
            }
        }
    }

    /* *** ChatClient *** */

    /**
     * Disconnects this chat client from the current chat server, if
     * any, by unregistering from the chat server.
     *
     * @param server The chat server to disconnect from.
     */
    protected void disconnect(ChatServerInterface server) {
        if (server != null) {
            try {
                String serverName = server.getName();
                server.unregister(this);
                System.out.println("[Disconnected from " + serverName + "]");
            } catch (RemoteException rex) {
            }
        }
    }

    /**
     * This method implements the '.disconnect' user command.
     */
    protected void userDisconnect() {
        if (myServer != null) {
            disconnect(myServer);
            myServer = null;
        } else {
            System.out.println("[Client is not currently connected]");
        }
    }

    /**
     * This method implements the '.connect' user command. If a
     * servername pattern is supplied, the known chat services are
     * scanned for names in which the pattern is a substring. If a null
     * or empty pattern is supplied, the connection attempt is directed
     * at the first known server (regardless of whether it is answering
     * or not). Any current service is disconnected.
     *
     * @param serviceName The substring to match against the server name.
     */
    protected void connectToChat(String serviceName) {
        if (servers.isEmpty())
            scanForChatServers();

        if (servers.isEmpty()) {
            System.out.println("[There are no known servers]");
            return;
        }

        // Count nof matching service names

        int nofMatches = 0;
        String selectedServiceName = null;

        if (serviceName == null || serviceName.isEmpty()) {
            nofMatches = 1;
            selectedServiceName = servers.get(0);
        } else
            for (String name : servers)
                if (name.contains(serviceName)) {
                    nofMatches++;
                    selectedServiceName = name;
                }

        if (nofMatches == 0) {
            System.out.printf("[No servers found matching '%s']\n", serviceName);
            return;
        } else if (1 < nofMatches) {
            System.out.printf("['%s' matches more than one server]\n", serviceName);
            return;
        }

        // One name matched. If we are already connected somewhere,
        // disconnect that first.

        if (myServer != null) {
            disconnect(myServer);
            myServer = null;
        }

        try {
            // Get the default registry

            Registry registry = LocateRegistry.getRegistry(null);

            // Retrieve the service stub from the registry

            Remote service = registry.lookup(selectedServiceName);

            // Verify that it is indeed what we expect

            if (service instanceof ChatServerInterface) {
                myServer = (ChatServerInterface) service;
                myServer.register(this);
                waitingForNameFromServer = true;
                System.out.printf("[Connected to %s]\n", selectedServiceName);
            }
        } catch (Exception e) {
            System.out.printf("[Unable to connect: %s]\n", e.toString());
            //e.printStackTrace();
        }

    } // method connectToChat

    /**
     * This method implements the '.name' user command. It sets the name
     * the user has choosen for herself on the chat. If the name is null
     * or the empty string, the &quot;user.name&quot; system property is
     * used as a substitute.
     *
     * @param newName The user's name.
     */
    protected void setName(String newName) {
        String oldName = myName;
        myName = newName;

        if (myName != null) {
            myName = myName.trim();
            if (myName.length() == 0) {
                myName = null;
            }
        }

        if (myName == null) {
            myName = System.getProperty("user.name");
        }

        if (myServer != null) {
            try {
                myServer.say("Username changed from: " + oldName + " to: " + myName);
                waitingForNameFromServer = true;
            } catch (RemoteException e) {
                System.out.println("[Sending to server failed]");
            }
        }
    }

    protected void getNameFromServer(ChatNotification chat) {
        if (chat.getText().startsWith("System: New chat member: " + myName) || chat.getText().startsWith("Username changed from: ")) {
            waitingForNameFromServer = false;
            String[] substring = chat.getText().split(": ");
            myName = substring[2];
            if (substring.length > 3){
                for (int i = 3; i < substring.length; i++) {
                    myName += ": " + substring[i];
                }
            }
        }
    }

    /**
     * This method implements the send command which is implicit in the
     * command interpreter (the input line does not start with a period).
     *
     * @param text The text to send to the currently connected server.
     */
    protected void sendToChat(String text) {
        if (myServer != null) {
            try {
                myServer.say(text);
            } catch (RemoteException rex) {
                System.out.println("[Sending to server failed]");
            }
        } else {
            System.out.println("[Cannot send chat text: not connected to a server]");
        }
    }

    /**
     * This method implements the '.list' user command.  All known chat
     * servers are listed and a call attempt is made with each.
     */
    protected void listServers() {
        scanForChatServers();

        if (servers.isEmpty()) {
            System.out.println("[There are no known servers at this time]");
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry(null);

            for (String name : servers) {
                System.out.printf("[%s ", name);
                try {
                    Remote service = registry.lookup(name);
                    if (service instanceof ChatServerInterface) {
                        ChatServerInterface csi = (ChatServerInterface) service;
                        // How can we detect which one we are connected to?
                        try {
                            String s = csi.getName();
                            System.out.printf("%s OK]\n", s);
                        } catch (Exception e) {
                            System.out.printf(" - server not responding: %s]\n",
                                    e.toString());
                        }
                    }
                } catch (Exception e) {
                    System.out.printf(" - registry lookup failed: %s]\n", e.toString());
                }
            }
        } catch (Exception e) {
            System.out.printf("[Unable to list servers: %s]\n", e.toString());
        }
    }

    /**
     * This array holds the strings of the user command help text.
     */
    protected String[] cmdHelp = {
            "Commands (can be abbreviated):",
            ".list              List the currently known chat servers",
            ".name <name>       Set the username presented by the chat client",
            ".c                 Connect to the default server",
            ".connect <string>  Connect to a server with a matching string",
            ".disconnect        Break the connection to the server",
            ".quit              Exit the client",
            ".help              This text",
            ".users             Lists all current members of the chat.",
            ".myname            Prints your name in the chat",
            ".block <name>      Blocks the user with this name",
            ".unblock <name>    Unblocks this user if he was blocked before",
            ".listblock         List all currently blocked users"
    };

    /**
     * Implements the '.help' user command.
     *
     * @param argv Reserved for future used (e.g. '.help connect').
     */
    protected void showHelp(String[] argv) {
        System.out.println("[" + versionString + "]");
        for (int i = 0; i < cmdHelp.length; i++) {
            System.out.println("[" + cmdHelp[i] + "]");
        }
    }

    /**
     * Creates a new string which is the concatenation of the elements
     * in a string array, joined around a given delimiter string. This
     * is slightly different from String.join() in that this method can
     * start from any position in the array.
     *
     * @param sa         The string array to join together.
     * @param firstIndex The index of the first element in sa to consider.
     * @param delimiter  Delimiter string between elements in sa, or null.
     * @return The concatenated result or at least the empty string.
     */
    protected String stringJoin(String[] sa, int firstIndex, String delimiter) {
        StringBuilder sb = new StringBuilder();
        String delim = (delimiter == null) ? "" : delimiter;

        if (sa != null) {
            if (firstIndex < sa.length) {
                sb.append(sa[firstIndex]);
                for (int i = firstIndex + 1; i < sa.length; i++)
                    sb.append(delim).append(sa[i]);
            }
        }

        return sb.toString();
    }

    protected void setAFK(Boolean status) {
        isAFK = status;
    }

    protected Boolean getAFK() {
        return isAFK;
    }

    // Method to find out if the timer is cancelled or not
    protected Boolean isCancelled(Timer timer) {
        return timer != null && timer.purge() > 0;
    }

    protected void cancelTimer() {
        if (isRunning) {
            System.out.println("Timer is already running. Timer cancelled.");
            timer.cancel();
        }

    }

    protected void runTimer() {
        if (getAFK()) {
            System.out.println("Client is already afk. Timer cancelled.");
            return;
        }

        if (isRunning) {
            // If the timer is already running, cancel the current task
            task.cancel();
        }

        // Schedule a new task
        task = new TimerTask() {
            @Override
            public void run() {

                if (myServer != null) {
                    sendToChat(myName + " is now AFK.");
                } else {
                    System.out.println("Timer expired after " + timeOut + " seconds. Changing status to AFK.");
                }
                setAFK(true);
                isRunning = false; // Reset the running flag
            }
        };

        // Schedule the task with the specified timeout
        timer.schedule(task, timeOut * 1000); // Convert seconds to milliseconds
        isRunning = true;
    }

    /**
     * The user command interpreter. Commands are read from standard
     * input, parsed and dispatched to methods that either implement
     * user commands or sends the text to the ChatServer (when
     * connected).
     */

    protected void blockUser(String user){
        boolean added = blockedUsers.add(user.trim());
        if (added) {
            System.out.println(user + " is now blocked");
        }
    }

    protected void unblockUser(String user){
        boolean removed = blockedUsers.remove(user.trim());
        if (removed){
            System.out.println(user + " is not blocked anymore");
        }
    }

    protected void listBlockUser(){
        System.out.println("Blocked users:");
        for (String user: blockedUsers){
            System.out.println(user);
        }
    }

    protected boolean isBlocked(String user){
        return blockedUsers.contains(user.trim());
    }

    protected boolean isBlocked(ChatNotification chat){
        return isBlocked(chat.getText().split(": ")[0]);
    }

    protected void rename(ChatNotification chat){
        boolean renamed = chat.getText().startsWith("Username changed from: ");
        if (renamed){
            String[] splitted = chat.getText().split(": ");
            String oldName = splitted[1].substring(0, splitted[1].length() - 3);
            String newName = splitted[2];
            if (isBlocked(oldName)){
                blockedUsers.remove(oldName);
                blockedUsers.add(newName);
                System.out.println("A blocked user changed his username, changed blocked user from " + oldName + " to " + newName);
            }
        }
    }

    protected void readLoop() {

        boolean halted = false;
        BufferedReader d = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("[Output from the client is in square brackets]");
        System.out.println("[Commands start with '.' (period). Try .help]");
        System.out.println("[When connected, type text and hit return to send]");

        setName(myName);        // set default name

        while (!halted) {
            System.out.print("Client> ");
            System.out.flush();
            String buf = null;
            if (!getAFK()) {
                runTimer();
            }
            try {
                buf = d.readLine();
            } catch (IOException iox) {
                iox.printStackTrace();
                System.out.println("\n[I/O error in command interface]");
                halted = true;
                continue;
            }

            if (buf == null) {    // EOF in command input.
                halted = true;
                continue;
            }

            // Trim away leading and trailing space from the raw input.

            String arg = buf.trim();

            // Check if the input starts with a period.

            if (arg.startsWith(".")) {

                // Skip the leading period and split the string into
                // fragments, separated by whitespace.

                String[] argv = arg.substring(1).split("\\s++");

                // Treat the first word as a command verb and make it
                // lowercase for easier matching.

                String verb = argv[0].toLowerCase();

                // Accept leading commend verb abbreviations.

                if ("quit".startsWith(verb)) {
                    halted = true;
                    isAFK = false;
                } else if ("connect".startsWith(verb)) {
                    connectToChat(stringJoin(argv, 1, " "));
                    isAFK = false;
                } else if ("disconnect".startsWith(verb)) {
                    userDisconnect();
                    isAFK = false;
                } else if ("list".startsWith(verb)) {
                    listServers();
                    isAFK = false;
                } else if ("name".startsWith(verb)) {
                    setName(stringJoin(argv, 1, " "));
                    isAFK = false;
                } else if ("help".startsWith(verb)) {
                    showHelp(argv);
                    isAFK = false;
                } else if ("users".startsWith(verb)) {
                    if (myServer != null) {
                        sendToChat(arg);
                    } else {
                        System.out.println("Please connect to a server before using this command.");
                    }
                    isAFK = false;
                } else if ("myname".startsWith(verb)) {
                    System.out.println(myName);
                    isAFK = false;
                } else if ("block".startsWith(verb)){
                    blockUser(stringJoin(argv, 1, " "));
                    isAFK = false;
                } else if ("unblock".startsWith(verb)){
                    unblockUser(stringJoin(argv, 1, " "));
                    isAFK = false;
                } else if ("listblock".startsWith(verb)){
                    listBlockUser();
                    isAFK = false;
                } else {
                    System.out.println("[" + verb + ": unknown command]");
                    isAFK = false;
                }
            } else if (0 < arg.length()) {
                sendToChat(myName + ": " + arg);
                isAFK = false;
            }

        } // while not halted

        System.out.println("[Quitting, please wait...]");

        disconnect(myServer);

        System.out.println("[Done]");

    }

    // The main method.

    public static void main(String[] argv) throws RemoteException {
        ChatClient cc = new ChatClient();
        cc.readLoop();

        // For unknown reasons we need to force the exit.
        System.exit(0);
    }
}
