// ChatServer.java
// 2024-01-24/fki Version 8: no package, no Jini, no rmid
// 2018-08-21/fki Refactored for lab version 7.
// 18-mar-2004/FK First version
//
// This program is a simple chat-server. It answers to requests from
// ChatClient instances, which deposit message strings on the methods
// that implement ChatServerInterface. The message strings are then
// sent back out as CharNotification events to all callbacks
// (i.e. RemoteEventListeners) that are registered with the server.

// Standard Java

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.LinkedList;
import java.util.Vector;

/**
 * The ChatServer class is a main program application that implements
 * a simple chat service. It provides service to ChatClient instances
 * which also host the user interface application.
 */
public class ChatServer
        extends
        UnicastRemoteObject        // for Java RMI
        implements
        ChatServerInterface,    // for clients
        Runnable            // for the distribution thread.
{
    /**
     * The server's message counter. Increments monotonically with each
     * message dispatched.
     */
    protected int msgCount = 0;

    /**
     * Incoming messages are placed on the message queue. The
     * distribution thread consumes the queue by sending copies off to
     * registered clients. Class LinkedList is not thread-safe, so access
     * to it must be synchronized.
     */
    protected LinkedList<String> msgQueue = new LinkedList<String>();

    /**
     * The notification objects of registered clients are held in this
     * vector. The Vector class is thread-safe, but since we are using
     * an iterator from it while sending messages we must synchronize on
     * it anyway. The iterator will not survive the vector being
     * modified.
     */
    protected Vector<RemoteEventListener> clients =
            new Vector<RemoteEventListener>();

    protected Vector<String> clientNames = new Vector<>();

    public boolean containsClientName(String username) {
        return clientNames.contains(username);
    }

    /**
     * The printed name of this server instance.
     */
    protected String serverName = null;

    /**
     * The delivery thread runs while this flag is true.
     */
    protected boolean runDelivery = true;

    /**
     * Creates a new ChatServer.
     *
     * @param name The identifying name of this server instance.
     */
    public ChatServer(String name)
            throws
            IOException,
            RemoteException,        // if join doesn't work
            UnknownHostException    // if we don't know where we are
    {
        // Find out our hostname so that clients can see it in the registration.

        String host = InetAddress.getLocalHost().getHostName().toLowerCase();

        // Make sure the idName contains something useful

        String idName = (name == null) ? "" : name.trim();
        if (idName.isEmpty())
            idName = System.getProperty("user.name");

        // Compose the name under which to register
        //
        // ChatServer.fki@KTH-11355.97863746234

        serverName =
                getClass().getName()
                        + "." + idName + "@" + host
                        + "." + Long.toString(System.currentTimeMillis());


        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("windows")) {
            System.out.println("Running on Windows");

            Naming.rebind("///" + serverName, this);

        } else if (osName.contains("linux")) {
            System.out.println("Running on Linux");

            // Register with the rmiregistry
            Registry rgsty = LocateRegistry.createRegistry(1099);
            rgsty.rebind("///" + serverName, this);
            Naming.rebind("///" + serverName, this);

        } else {
            System.out.println("Running on another operating system: " + osName);

            // Register with the rmiregistry
            Registry rgsty = LocateRegistry.createRegistry(1099);
            rgsty.rebind("///" + serverName, this);
            Naming.rebind("///" + serverName, this);
        }

        // Start the service thread.

        new Thread(this).start();
    }

    /**
     * Unbind (remove) this service from the rmiregistry.
     */
    protected void unbind() {
        try {
            Naming.unbind("///" + serverName);
        } catch (Exception e) {
            System.out.printf("When unbinding from rmiregistry: %s\n",
                    e.toString());
        }
    }

    /**
     * Adds a message the the output queue.
     *
     * @param msg The text message to add.
     */
    protected void addMessage(String msg) {
        synchronized (msgQueue) {
            msgQueue.addLast(msg);
        }
        msgCount++;

        // Output a server console diagnostic
        System.out.println("MSG#" + msgCount + ":" + msg);

        // Wake up the distribution thread.
        wakeUp();
    }

    /**
     * Retrieves the oldest (first) message from the message queue.
     *
     * @return The next message, or null if the queue is empty.
     */
    protected String getNextMessage() {
        if (msgQueue.isEmpty())
            return null;
        else synchronized (msgQueue) {
            return msgQueue.removeFirst();
        }
    }

    /**
     * Adds a registration to the list of clients currently connected to
     * this ChatServer instance.
     *
     * @param rel The RemoteEventListener implementation to add.
     */
    protected void addClient(RemoteEventListener rel) {
        synchronized (clients) {
            try {
                clients.add(rel);
                String msg = changeClientName(rel.getName(), true);
                say(msg);
            } catch (java.rmi.RemoteException e) {
                System.out.println("Exception");
            }
        }
        System.out.println("Added client : " + rel.toString());
    }

    /**
     * Removes a registration from the list of clients currently
     * connected to this ChatServer instance.
     *
     * @param rel The RemoteEventListener implementation to remove.
     */
    protected void removeClient(RemoteEventListener rel) {
        synchronized (clients) {
            clients.remove(rel);
            try {
                String clientName = rel.getName();
                clientNames.remove(clientName);
                say(clientName + " disconnected");
            } catch (RemoteException e) {
                System.out.println("Can't remove user from list of current users");
            }
        }
        System.out.println("Removed client : " + rel.toString());
    }

    protected String changeClientName(String msg, boolean newClient) {
        String newName;
        String message;
        if (newClient) {
            newName = msg;
            message = "System: New chat member: " + newName;
        } else {
            String[] changeName = msg.split(": ");
            clientNames.remove(changeName[1].substring(0, changeName[1].length() - 3));
            newName = changeName[2];
            if (changeName.length > 3) {
                for (int i = 3; i < changeName.length; i++) {
                    newName += ": " + changeName[i];
                }
            }
            message = msg;
        }
        System.out.println("maybe new Name: newName");
        if (clientNames.contains(newName)) {
            int postfix = 1;
            while (clientNames.contains(newName + postfix)) {
                postfix++;
            }
            newName += postfix;
            message += postfix;
        }
        clientNames.add(newName);
        return message;
    }


    /* *** Interface ChatServerInterface *** */

    @Override
    public void say(String msg) throws RemoteException {
        if (msg != null) {
            if (msg.split(": ", 2)[0].equalsIgnoreCase("Username changed from")) {
                msg = changeClientName(msg, false);
            } else if (msg.equalsIgnoreCase(".users")) {
                msg += "\n Current users: ";
                for (String user : clientNames) {
                    msg += "\n" + user;
                }
            }
            addMessage(msg);
        }
    }

    @Override
    public String getName() throws RemoteException {
        return serverName;
    }

    @Override
    public void register(RemoteEventListener rel) throws RemoteException {
        if (rel != null) {
            addClient(rel);
        }
    }

    @Override
    public void unregister(RemoteEventListener rel) throws RemoteException {
        if (rel != null) {
            removeClient(rel);
        }
    }

    /* *** Internal code *** */

    /**
     * This method is where the delivery thread (in method run()) rests
     * while the message queue is empty.
     */
    protected synchronized void snooze() {
        try {
            wait();
        } catch (InterruptedException iex) {
        } catch (IllegalMonitorStateException ims) {
        }
    }

    /**
     * This method is called when the service interface has added a new
     * message to the message queue. If the delivery thread is waiting
     * in snooze(), it will continue as soon as this method has exited.
     * The thread that calls this method is the RMI service thread, the
     * thread that channels remote requests into the service interface code.
     * The call sequence is: say(String):addMessage(String):wakeUp().
     */
    protected synchronized void wakeUp() {
        notify();
    }

    /**
     * This is where the distribution thread spends its time. It dequeues
     * the message queue, builds a ChatNotification event and sends it to
     * each client that has registered a remote event listener with us.
     * When the message queue is empty, the thread calls snooze() and does
     * nothing until it is awakened by the code that has added a new
     * message to the message queue.
     */
    public void run() {

        while (runDelivery) {

            String msg = getNextMessage();
            if (msg != null) {
                // Prepare a notification
                ChatNotification note = new ChatNotification(this, msg, msgCount);
                // Send it to all registered listeners.
                synchronized (clients) {
                    try {
                        for (RemoteEventListener rel : clients)
                            rel.notify(note);
                    } catch (RemoteException rex) {
                    }
                }
            } else {
                snooze();
            }
        } // while runDelivery

        System.out.println("\nDelivery thread exiting.");
    }

    /**
     * This method implements a small command interpreter which only
     * exists to perform a graceful shutdown of the server.
     */
    public void readLoop() {
        boolean halted = false;
        BufferedReader d = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Server " + serverName + " started.");

        while (!halted) {
            System.out.print("Server> ");
            System.out.flush();
            String buf = null;
            try {
                buf = d.readLine();
            } catch (java.io.IOException iox) {
                iox.printStackTrace();
                System.out.println("\nI/O error in command interface.");
                halted = true;
                continue;
            }

            if (buf == null) { // EOF on System.in
                halted = true;
                continue;
            }

            String arg = buf.trim();

            if (arg.length() == 0) { // The empty string
                continue;
            }

            if (arg.equalsIgnoreCase("quit") ||
                    arg.equalsIgnoreCase("stop") ||
                    arg.equalsIgnoreCase("halt") ||
                    arg.equalsIgnoreCase("exit")) {
                halted = true;
            } else if (arg.equalsIgnoreCase("help")) {
                System.out.println("Available commands:");
                System.out.println("quit      Shuts down the server.");
                System.out.println("help      This text.");
            } else {
                System.out.println("\nUnknown server command : " + arg);
            }
        }

        System.out.println("\nShutting down, please wait...");
        runDelivery = false;
        wakeUp();
    }

    /**
     * This method implements the commandline help command.
     */
    protected static void usage() {
        String[] msg = {
                "Usage: {'?'|-h|-help}|[-n server-name]"
        };

        for (String s : msg)
            System.out.println(s);
    }

    // The ChatServer main program.

    public static void main(String[] argv)
            throws
            IOException,
            RemoteException,
            UnknownHostException {

        String serverName = null;
        int state = 0;

        for (int i = 0; i < argv.length; i++) {
            String av = argv[i];
            if (state == 0) {
                if (av.equalsIgnoreCase("-n")) {
                    state = 1;
                } else if (av.equals("?") ||
                        av.equalsIgnoreCase("-h") ||
                        av.equalsIgnoreCase("-help") ||
                        av.equalsIgnoreCase("--help")) {
                    usage();
                    return;
                } else {
                    System.out.printf("Unknown commandline option:%s%n", av);
                    return;
                }
            } else if (state == 1) {
                serverName = av;
                state = 0;
            }
        }

        // This may or may not be required
        // if (System.getSecurityManager() == null)
        //   System.setSecurityManager(new SecurityManager());

        ChatServer cs = new ChatServer(serverName);
        cs.readLoop();
        cs.unbind();
        System.exit(0);
    }
}
