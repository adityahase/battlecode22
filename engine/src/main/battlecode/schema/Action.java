// automatically generated by the FlatBuffers compiler, do not modify

package battlecode.schema;

/**
 * Actions that can be performed.
 * Purely aesthetic; have no actual effect on simulation.
 * (Although the simulation may want to track the 'parents' of
 * particular robots.)
 * Actions may have 'targets', which are the units on which
 * the actions were performed.
 */
public final class Action {
  private Action() { }
  /**
   * Politicians self-destruct and affect nearby bodies.
   * Target: none
   */
  public static final byte EMPOWER = 0;
  /**
   * Slanderers passively generate influence for the
   * Enlightenment Center that created them.
   * Target: parent ID
   */
  public static final byte EMBEZZLE = 1;
  /**
   * Slanderers turn into Politicians.
   * Target: none
   */
  public static final byte CAMOUFLAGE = 2;
  /**
   * Muckrakers can expose a slanderer.
   * Target: an enemy body
   */
  public static final byte EXPOSE = 3;
  /**
   * Units can change their flag.
   * Target: new flag value
   */
  public static final byte SET_FLAG = 4;
  /**
   * Builds a unit.
   * Target: spawned unit
   */
  public static final byte SPAWN_UNIT = 5;
  /**
   * Places a bid.
   * Target: bid value
   */
  public static final byte PLACE_BID = 6;
  /**
   * A robot can change team after being empowered,
   * or when a Enlightenment Center is taken over.
   * Target: teamID
   */
  public static final byte CHANGE_TEAM = 7;
  /**
   * A robot's influence changes.
   * Target: delta value
   */
  public static final byte CHANGE_INFLUENCE = 8;
  /**
   * A robot's conviction changes.
   * Target: delta value, i.e. red 5 -> blue 3 is -2
   */
  public static final byte CHANGE_CONVICTION = 9;
  /**
   * Dies due to an uncaught exception.
   * Target: none
   */
  public static final byte DIE_EXCEPTION = 10;

  public static final String[] names = { "EMPOWER", "EMBEZZLE", "CAMOUFLAGE", "EXPOSE", "SET_FLAG", "SPAWN_UNIT", "PLACE_BID", "CHANGE_TEAM", "CHANGE_INFLUENCE", "CHANGE_CONVICTION", "DIE_EXCEPTION", };

  public static String name(int e) { return names[e]; }
}

