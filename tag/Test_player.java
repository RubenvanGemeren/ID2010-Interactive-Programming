

import java.io.Serializable;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Test Player jumps around randomly among the Bailiffs. Test Player can be used
 * to test that the system is operating, and as a template for more
 * evolved agents. Since objects of class Test Player move between JVMs, it
 * must be implement the Serializable marker interface. Reference type
 * members that are not serializable should be declared as transient.
 */
public class Test_player implements Serializable
{
  /**
   * List of viable Bailiff names
   */
  private ArrayList<String> goodNames = new ArrayList<>();

  /**
   * List of defunct Bailiff names
   */
  private ArrayList<String> badNames = new ArrayList<>();

  /**
   * Identification string used in debug messages.
   */
  private String id = "anon";

  public UUID externalId = UUID.randomUUID();

  /**
   * Default sleep time so that we have time to track what it does.
   */
  private long restraintSleepMs = 3000;

  /**
   * The jump count variable is incremented each time method topLevel
   * is entered. Its value is printed by the debugMsg routine.
   */
  private int jumpCount = 0;

  /**
   * The default sleep time between subsequent queries of the
   * rmiregistry.
   */
  private long retrySleep = 20 * 1000; // 20 seconds

  /**
   * The debug flag controls the amount of diagnostic info we put out.
   */
  protected boolean debug = false;

  /**
   * This player is the tagged player.
   */
  private boolean tagged = false;

  /**
   * If the player wants to migrate to a Bailiff
   */
    private boolean willMigrate = false;


  /**
   * Sets the id string of this Test Player.
   * @param id The id string. A null argument is replaced with the
   * empty string.
   */
  public void setId(String id) {
    this.id = (id != null) ? id : "";
  }

  /**
   * Sets the restraint sleep duration.
   * @param ms The number of milliseconds in restraint sleep.
   */
  public void setRestraintSleep(long ms) {
    restraintSleepMs = Math.max(0, ms);
  }

  /**
   * Sets the query retry sleep duration.
   * @param ms The number of milliseconds between each query.
   */
  public void setRetrySleep(long ms) {
    retrySleep = Math.max(0, ms);
  }

  /**
   * Sets or clears the global debug flag. When enabled, trace and
   * diagnostic messages are printed on stdout.
   */
  public void setDebug(boolean isDebugged) {
    debug = isDebugged;
  }

  /**
   * Outputs a diagnostic message on standard output. This will be on
   * the host of the launching JVM before Test Player moves. Once he has migrated
   * to another Bailiff, the text will appear on the console of that Bailiff.
   * @param msg The message to print.
   */
  protected void debugMsg (String msg) {
    if (debug)
      System.out.printf("%s(%d):%s%n", id, jumpCount, msg);
  }

  /**
   * Creates a new Test Player.
   */
  public Test_player() {}

  /**
   * Sleep for the given number of milliseconds.
   * @param ms  The number of milliseconds to sleep.
   */
  protected void snooze (long ms) {
    try {
      Thread.currentThread ().sleep (ms);
    }
    catch (InterruptedException e) {}
  }

  /**
   * Scan for Baliff services.
   */
  protected void scanForBailiffs() {

    try {

      // Get the default RMI registry
      Registry registry = LocateRegistry.getRegistry(null);

      // Ask for all registered services
      String [] serviceNames = registry.list();

      // Print the list of found services to the console
      debugMsg("All found services: " + String.join(", ", serviceNames));

      // Inspect the list of service names
      for (String name : serviceNames) {

        if (name.startsWith("Bailiff")) {

          // If the name already is on the bad list, ignore it
          if (badNames.contains(name))
              continue;

          // If the name already is on the good list, ignore it
          if (goodNames.contains(name))
            continue;

          // Else, optimistically add it to the good names
          goodNames.add(name);
        }
      }
      
    }
    catch (Exception e) {
      debugMsg("Scanning for Bailiffs failed: " + e.toString());
    }
  }
  
  /**
   * This is Test Player's main program once he is on his way. In short, he
   * goes into an infinite loop in which the only exit is a
   * successfull migrate to a Bailiff.
   *
   * for (;;) {
   *
   *   while no good Bailiffs are found
   *     look for Bailiffs
   *
   *   while good Bailiffs are known
   *     jump to a random Bailiff
   *     or
   *     update the lists of good and bad bailiffs
   * }
   *
   * Test Player has no concept of where he is, and may happily migrate to
   * the Bailiff he is already in.
   */
  public void topLevel ()
    throws
      java.io.IOException
  {
    jumpCount++;

    // Loop forever until we have successfully jumped to a Bailiff.
    for (;;) {

      long retryInterval = 0;	// incremented when no Bailiffs are found

      // Sleep a bit so that humans can keep up.
      debugMsg("Is here - entering restraint sleep.");
      snooze(restraintSleepMs);
      debugMsg("Leaving restraint sleep.");

      // Try to find Bailiffs.
      // The loop keeps going until we get a non-empty list of good names.
      // If no results are found, we sleep a bit between attempts.
      do {

        if (0 < retryInterval) {
          debugMsg("No Bailiffs detected - sleeping.");
          snooze(retryInterval);
          debugMsg("Waking up, looking for Bailiffs.");
        }

        scanForBailiffs();

        retryInterval = retrySleep;

        // If no lookup servers or bailiffs are found, go back up to
        // the beginning of the loop, sleep a bit, and then try again.
      } while (goodNames.isEmpty());

      // Enter a loop in which we:
      // - randomly pick one Bailiff
      // - migrate to it, or if that fail, try another one
      while (!goodNames.isEmpty()) {

        // Prepare some state flags
        boolean noRegistry = false;
        boolean badName = false;

        // Now, at least one possibly good Bailiff has been found.
        debugMsg("Found " + goodNames.size() + " Bailiffs");

        // Change code to pick bailiff with players (not tagged player(s))
        // Randomly pick one of the good names
        String name = goodNames.get((int)(goodNames.size() * Math.random()));

        // try to get the current Bailiff
        try {

          // Get the default RMI registry
          Registry registry = LocateRegistry.getRegistry(null);

          try {

            // Lookup the service name we selected
            Remote service = registry.lookup(name);

            // Verify it is what we want
            if (service instanceof BailiffInterface) {

              // Cast the service to a Bailiff
              BailiffInterface currentBailiff = (BailiffInterface) service;

              // Check if the Bailiff has (tagged) players
              willMigrate = !currentBailiff.hasTaggedPlayer();

              // Attempt to migrate to the selected Bailiff
              try {

                if (willMigrate) {
                  debugMsg("Trying to migrate");

                  debugMsg("Letting the Bailiff know that I am migrating");

                  // Notify the Bailiff that we are leaving and ask if we can migrate
                  Boolean canMigrate = currentBailiff.notify(this, "leaving");

                  // If the Bailiff allows us to migrate, do so
                  if (canMigrate) {
                    currentBailiff.migrate(this, "topLevel", new Object[]{}, tagged, externalId);

                    debugMsg("Has migrated");

                    return; // SUCCESS, we are done here
                  } else {
                    debugMsg("The Bailiff did not allow me to migrate");
                    // The Bailiff did not allow us to migrate, remove it from the list of good names
                    goodNames.clear();
                    badNames.clear();
                  }
                }
              }
              catch (RemoteException rex) {
                debugMsg(rex.toString());
                badName = true;
              }
            }
            else
              badName = true;
          }
          catch (Exception e) {
            badName = true;
          }
        }
        catch (Exception e) {
          noRegistry = true;
        }

        // If we come here the migrate failed. Check the state flags
        // and take appropriate action.
        if (noRegistry) {
          debugMsg("No registry found - resetting name lists");
          goodNames.clear();
          badNames.clear();
        }
        else if (badName) {
          debugMsg(String.format("Bad service name found: %s", name));
          goodNames.remove(name);
          badNames.add(name);
        }

    }	// while candidates remain

      debugMsg("All Bailiffs failed.");
    } // for ever
  }   // topLevel

  /**
   * Prints commandline help.
   */
  private static void showUsage() {
    String [] msg = {
      "Usage: {?,-h,-help}|[-debug][-id string][-rs ms][-qs ms]",
      "? -h -help   Show this text",
      "-debug       Enable trace and diagnostic messages",
      "-id  string  Set the id string printed by debug messages",
      "-rs  ms      Set the restraint sleep in milliseconds",
      "-qs  ms      Set the lookup query retry delay"
    };
    for (String s : msg)
      System.out.println(s);
  }

  // =============================================================
  // The main method is only used by the initial launch. After the
  // first jump, Test Player always restarts in method topLevel.
  // =============================================================
  
  public static void main (String [] argv)
    throws
      java.io.IOException, ClassNotFoundException
  {

    // Make a new Test Player and configure it from commandline arguments.
    
    Test_player dx = new Test_player();

    // Parse and act on the commandline arguments.

    int state = 0;

    for (String av : argv) {

      switch (state) {

      case 0:
	if (av.equals("?") || av.equals("-h") || av.equals("-help")) {
	  showUsage();
	  return;
	}
	else if (av.equals("-debug"))
	  dx.setDebug(true);
	else if (av.equals("-id"))
	  state = 1;
	else if (av.equals("-rs"))
	  state = 2;
	else if (av.equals("-qs"))
	  state = 3;
	else {
	  System.err.println("Unknown commandline argument: " + av);
	  return;
	}
	break;

      case 1:
	dx.setId(av);
	state = 0;
	break;

      case 2:
	dx.setRestraintSleep(Long.parseLong(av));
	state = 0;
	break;

      case 3:
	dx.setRetrySleep(Long.parseLong(av));
	state = 0;
	break;
      }	// switch
    }	// for all commandline arguments

    dx.topLevel();		// Start the Test Player

  } // main
}
