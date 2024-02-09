// RemoteEvent.java
// 2024-01-25/fki: For version 8 of lab 1.

/**
 * A reimplementation of the remote event class in the Jini
 * system. This was done because the client and server needed
 * something similar anyway, and were already using RemoteEvent.
 *
 * Instances of RemoteEvent are sent to remote event listeners.
 */
public class RemoteEvent extends java.util.EventObject
{
  protected long eventID;
  protected java.rmi.MarshalledObject handback;
  protected long seqNum;

  /**
   * Creates a new RemoteEvent instance.
   *
   * @param source The instance creating the remote event.
   * @param eventID An ID idendifying the type of event.
   * @param seqNum  A sequence number coupled to the event ID.
   * @param handback Additional information, or null.
   */
  public RemoteEvent(java.lang.Object source,
		     long eventID,
		     long seqNum,
		     java.rmi.MarshalledObject handback)
  {
    super(source);
    this.eventID = eventID;
    this.handback = handback;
    this.seqNum = seqNum;
  }

  public long getID() {
    return eventID;
  }

  public java.rmi.MarshalledObject getRegistrationObject() {
    return handback;
  }

  public long getSequenceNumber() {
    return seqNum;
  }
}
