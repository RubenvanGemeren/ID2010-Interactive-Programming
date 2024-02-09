2024-01-25/fki Lab 1 version 8 - massive revision
2018-08-22/fki Revised for lab 1 version 7
07-feb-2011/FK
06-feb-2009/FK
07-feb-2008/FK

---
2024-01-25: Changes in lab 1 version 8

- Jini/Apache River and rmid were removed.

  In version 8 servers and clients now interact directly with the
  rmiregistry.

- The webserver-based deployment infrastructre was removed.

  In version 8, the rmiregistry, the chat server, and the chat client
  are started directly on the commandline.

- The java packages chat.server and chat.client were removed and with
  them the corresponding source directories. The scripts for
  compiling, building, and installing jar files were removed. The
  directories for building, distribution, manifest files, and
  libraries were removed. The test directory tree and all its files
  was removed.

  In version 8 the Java source and class files reside in the default
  package in a single directory. Sources are compiled and run from
  that directory.

- The source files ChatClient.java and ChatServer.java were refactored
  to remove all dependencies on Jini/Apache River and instead interact
  directly with the rmiregistry service.

  Two new source files were added: RemoteEvent.java and
  RemoteEventListener.java. These were reimplemented from Jini/Apache
  River in order to support the applications and minimize the need for
  refactoring.

- This Readme.txt file was updated to reflect the changes.

- Benefits:

  * A massive simplification of the whole project, including building
    and running.

  * The project no longer relies on obsolete (Java/Apache River) or
    deprecated (rmid and service activation) technologies.

- Drawbacks:

  * The project no longer demonstrates distributed deployment and
    automatic service discovery through the use of a middleware.

---
2018-08-22: Changes in lab 1 version 7

- Source code and compilation brought up to be compatible with Java 5
  and upwards.

- Use of the rmic tool was removed, including the generation of static
  skeleton and stub files.

- The download files ChatServer-dl.jar and CharClient-dl.jar were removed.

- The separate client and server directories in the codebase server
  directory were removed. All jar files are now in the top directory
  of the codebase server.

- Build and run files for Windows (develop/bat, test/bin/pc) were
  reviewed, debugged, and refactored.

- ChatClient.java and ChatServer.java:

    = Import clauses are no longer wildcarded

    = Code upgraded to use generic collection types and iterators

    = An issue with hanging threads on shutdown was circumvented
      by calling System.exit(0) at the end of main.

    = The deprecated RMISecurityManager was replaced with the more
      modern SecurityManager.

- ChatClient.java:

    = The method connectToChat was rewritten to lock the servers
      Vector for a minimal amount of time and provide a cleaner
      logic. The server name pattern matching was changed from infix
      to prefix, and to be case-sensitive.

    = The method stringJoin was modified to use a StringBuilder
      instead of a String.

    = The command interpreter in method readLoop was simplified in its
      string management. It performs exactly as in previous versions,
      but with less code.

    = The client takes a default name from the user.name
      property. This can still be changed with the .name command.

- ChatServer.java:

    = Synchronization on the clients Vector is applied to protect
      iterators created from it.

    = The serverName string was changed so that it begins with the
      user-supplied name (defaults to user.name).

    = The method getNextMessage was rewritten to be more efficient.

  --

INTRODUCTION TO LAB CHAT

  The system presented here consists of a chatserver and a client.
  The server and the clients are both RMI applications, so they depend
  on the rmiregistry service being present in order to find each
  other.

  Your assignment is to select a development task from a list of
  alternatives (see file lab1v8.txt), and then to modify the system
  according to that task.

Source code

  The distribution comes with all source code files in a single
  directory. They can be compiled and executed in that directory. The
  files are:

  ChatClient.java           The chat client application
  ChatNotification.java     Remote event for text delivery to clients
  ChatServer.java           The chat server application
  ChatServerInterface.java  Interface definition
  RemoteEvent.java          Describes a remote event
  RemoteEventListener.java  Interface definition

  The source code directory can be placed anywhere, but remember that
  on a Windows system, the legacy command shell CMD.EXE only works in
  the local filesystem, i.e. in volumes prefixed by a drive letter,
  like C: or H:. The PowerShell works everywhere.

System requirements

  Windows, Mac/OS, or Linux, of a recently modern flavour, with
  networking.

  Java Development Kit (JDK) 8 or later.

  A source code editor for Java.

Compiling

  Open a commmand shell in the source code directory and type (or use
  an IDE as per preference):

    > javac *.java

Running the rmiregistry

  Open a command shell in the source code directory and start the
  rmiregistry:

    > rmiregistry

Running a chat server

  Open a new command shell in the source code directory and start a
  chat server. Supply commandline arguments as required:
  
    > java ChatServer

Running chat clients

  Open a new command shell in the source code directory and start a
  chat client. Supply commandline arguments as required:
  
    > java ChatClient

  Use the .list and .connect commands to find and connect to a server.

  Repeat to start additional clients as required.

  

Shutting down:

  Chat clients can use the .quit command or be externally terminated
  (Ctrl-C).

  Chat servers should exit via a quit command given on the console, so
  that they can unbind themselves from the rmiregistry. If a chat
  server is terminated externally, its registration will remain in the
  rmiregistry for some time, or until the rmiregistry is restarted.

  The rmiregistry can be externally terminated (Ctrl-C).
