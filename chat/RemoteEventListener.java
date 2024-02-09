// RemoteEventListener.java
// 2024-01-25/fki: For version 8 of lab 1.

/**
 * A reimplementation of the RemoteEventListener interface in the Jini
 * system. This was done because the client and the server needed
 * something similar anyway, and were already using
 * RemoveEventListener.
 *
 * This interface is implemented by listeners for instances of RemoteEvent.
 */
public interface RemoteEventListener
  extends java.rmi.Remote, java.util.EventListener
{

  /**
   * Called to deliver a RemoteEvent to the listener.
   *
   * @param theEvent  The RemoteEvent to deliver.
   */
  void notify(RemoteEvent theEvent) throws java.rmi.RemoteException;
  String getName() throws java.rmi.RemoteException;

}
