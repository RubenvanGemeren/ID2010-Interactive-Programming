

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
public class Test_player implements Serializable {
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

  /**
   * Recognizable name of the player
   */
  private String playerName = "test_player";

  public UUID externalId = UUID.randomUUID();

  /**
   * Default sleep time so that we have time to track what it does.
   */
  private long restraintSleepMs = 5000;

  /**
   * The jump count variable is incremented each time method topLevel
   * is entered. Its value is printed by the debugMsg routine.
   */
  private int jumpCount = 0;

  /**
   * The default sleep time between subsequent queries of the
   * rmiregistry.
   */
  private long retrySleep = 10 * 1000; // 10 seconds

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
   * Current Bailiff where the player is located
   */
  private BailiffInterface currentBailiff;

  /**
   * Sets the id string of this Test Player.
   *
   * @param id The id string. A null argument is replaced with the
   *           empty string.
   */
  public void setId(String id) {
    this.id = (id != null) ? id : "";
  }

  /**
   * Sets the name string of this Test Player.
   *
   * @param name The name string. A null argument is replaced with the
   *           empty string.
   */
  public void setName(String name) {
    this.playerName = (name != null) ? name : "test_player" + UUID.randomUUID();
  }

  /**
   * Sets the restraint sleep duration.
   *
   * @param ms The number of milliseconds in restraint sleep.
   */
  public void setRestraintSleep(long ms) {
    restraintSleepMs = Math.max(0, ms);
  }

  /**
   * Sets the query retry sleep duration.
   *
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
   *
   * @param msg The message to print.
   */
  protected void debugMsg(String msg) {
    if (debug)
      System.out.printf("%s(%d):%s%n", id, jumpCount, msg);
  }

  /**
   * Creates a new Test Player.
   */
  public Test_player() {
  }

  /**
   * Sleep for the given number of milliseconds.
   *
   * @param ms The number of milliseconds to sleep.
   */
  protected void snooze(long ms) {
    try {
      Thread.currentThread().sleep(ms);
    } catch (InterruptedException e) {
    }
  }

  /**
   * Scan for Baliff services.
   */
  protected void scanForBailiffs() {

    try {

      // Get the default RMI registry
      Registry registry = LocateRegistry.getRegistry(null);

      // Ask for all registered services
      String[] serviceNames = registry.list();

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

    } catch (Exception e) {
      debugMsg("Scanning for Bailiffs failed: " + e.toString());
    }
  }

  /**
   * This is Test Player's main program once he is on his way. In short, he
   * goes into an infinite loop in which the only exit is a
   * successfull migrate to a Bailiff.
   * <p>
   * for (;;) {
   * <p>
   * while no good Bailiffs are found
   * look for Bailiffs
   * <p>
   * while good Bailiffs are known
   * jump to a random Bailiff
   * or
   * update the lists of good and bad bailiffs
   * }
   * <p>
   * Test Player has no concept of where he is, and may happily migrate to
   * the Bailiff he is already in.
   */
  public void topLevel()
          throws
          java.io.IOException, NoSuchMethodException {
    jumpCount++;

    // Loop forever until we have successfully jumped to a Bailiff.
    for (; ; ) {

      long retryInterval = 0;    // incremented when no Bailiffs are found

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

        // If we are in a bailiff, figure out if we are tagged
        if (currentBailiff != null) {
          tagged = didIGetTagged(currentBailiff);
        }

        // Prepare some state flags
        boolean noRegistry = false;
        boolean badName = false;

        // Now, at least one possibly good Bailiff has been found.
        debugMsg("Found " + goodNames.size() + " Bailiffs");

        // Change code to pick bailiff with players (not tagged player(s)/player(s))
        // Randomly pick one of the good names
        String name = chooseBailiff(goodNames);

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
              BailiffInterface chosenBailiff = (BailiffInterface) service;

              // Before migrating to a new bailiff as a tagged player, try to tag a player at the current bailiff
              // If this fails, we will try to migrate to a new bailiff as a (still) tagged player
              // If the tagging is successful, we will migrate to a new bailiff as a non-tagged player
              if (tagged && currentBailiff != null) {
                tagged = !tryTagging(currentBailiff);
              }

              // Determine if we will migrate to the selected Bailiff
              willMigrate = willMigrateToBailiff(chosenBailiff);

              // Attempt to migrate to the selected Bailiff
              try {

                if (willMigrate) {
                  debugMsg("Trying to migrate");

                  // Set parameter for migration
                  Boolean canMigrate = true;

                  // If we are located at a Bailiff, notify it that we are leaving
                  if (currentBailiff != null) {
                    debugMsg("Letting the Bailiff know that I am migrating");

                    // Lock the player before migrating
                    currentBailiff.lock(externalId);

                    tagged = didIGetTagged(currentBailiff);

                    // Notify the Bailiff that we are leaving and ask if we can migrate
                    canMigrate = currentBailiff.notify(this, "leaving");

                    // Note: We only need to unlock the player if we are not allowed to migrate
                    // If we are allowed to migrate, the player will be deleted from the Bailiff
                    // thus not needing to be unlocked
                  }

                  // If the Bailiff allows us to migrate, do so
                  if (canMigrate) {
                    // Before migrating, update our current Bailiff
                    currentBailiff = chosenBailiff;

                    chosenBailiff.migrate(this, "topLevel", new Object[]{}, playerName, tagged, externalId);

                    debugMsg("Has migrated");

                    return; // SUCCESS, we are done here
                  } else {
                    debugMsg("The Bailiff did not allow me to migrate");

                    // Unlock the player before trying to migrate again,
                    // this is not necessary if we are allowed to migrate
                    currentBailiff.unlock(externalId);

                    // The Bailiff did not allow us to migrate, remove it from the list of good names
                    goodNames.clear();
                    badNames.clear();
                  }
                }
              } catch (RemoteException rex) {
                debugMsg(rex.toString());
                badName = true;
              }
            } else
              badName = true;
          } catch (Exception e) {
            badName = true;
          }
        } catch (Exception e) {
          noRegistry = true;
        }

        // If we come here the migrate failed. Check the state flags
        // and take appropriate action.
        if (noRegistry) {
          debugMsg("No registry found - resetting name lists");
          goodNames.clear();
          badNames.clear();
        } else if (badName) {
          debugMsg(String.format("Bad service name found: %s", name));
          goodNames.remove(name);
          badNames.add(name);
        }

      }    // while candidates remain

      debugMsg("All Bailiffs failed.");
    } // for ever
  }   // topLevel

  private Boolean tryTagging(BailiffInterface currentBailiff) throws RemoteException, NoSuchMethodException {
    // Ask the Bailiff to tag a player and return the result
    debugMsg("Asking the Bailiff to tag a player");

    return currentBailiff.tagPlayer(externalId);
  }

  // Not needed anymore since we are already checking if the Bailiff satisfies the conditions
  private boolean willMigrateToBailiff(BailiffInterface bailiff) throws RemoteException {
    // If player is tagged and the Bailiff has players, migrate
    if (tagged && bailiff.hasPlayers()) {
      return true;
    }

    // Else only migrate if the Bailiff has players and no tagged player
    return bailiff.hasPlayers() && !bailiff.hasTaggedPlayer();
  }

  // Choose a Bailiff beneficial to the player
  private String chooseBailiff(ArrayList<String> goodNames) {

    // If the player is tagged, choose a Bailiff with players. Loop until a Bailiff with players is found
    for (String name : goodNames) {
      try {
        // Get the default RMI registry
        Registry registry = LocateRegistry.getRegistry(null);

        // Lookup the service name we selected
        Remote service = registry.lookup(name);

        // Verify it is what we want
        if (service instanceof BailiffInterface) {

          // Cast the service to a Bailiff
          BailiffInterface chosenBailiff = (BailiffInterface) service;

          // Get player count of the Bailiff
          int playerCount = chosenBailiff.getPlayerCount();

          // Get if the Bailiff has a tagged player
          boolean taggedPlayerInBailiff = chosenBailiff.hasTaggedPlayer();

          // If the p§layer is tagged and the Bailiff has players, migrate
          if (!taggedPlayerInBailiff && playerCount > 1 && tagged) {
            return name;
          }

          // If the player is not tagged and the Bailiff has no tagged player, migrate
          if (!taggedPlayerInBailiff && playerCount > 0 && !tagged) {
            return name;
          }
        }
      } catch (Exception e) {
        // If the lookup fails, ignore the Bailiff
        debugMsg("Failed to lookup Bailiff: " + e.toString());
      }
    }
    // If no suitable Bailiff is found, return a random Bailiff
    return goodNames.get((int) (Math.random() * goodNames.size()));
  }

  private Boolean didIGetTagged(BailiffInterface currentBailiff) throws RemoteException, NoSuchMethodException {
    return currentBailiff.isTagged(externalId);
  }

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
          java.io.IOException, ClassNotFoundException, NoSuchMethodException {

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
    else if (av.equals("-tagged"))
      state = 4;
	else {
	  System.err.println("Unknown commandline argument: " + av);
	  return;
	}
	break;
      case 1:
	    dx.setId(av);
        dx.setName(av);
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

      case 4:
        dx.tagged = Boolean.parseBoolean(av);
        state = 0;
        break;
      }	// switch
    }	// for all commandline arguments

    dx.topLevel();		// Start the Test Player

  } // main
}
