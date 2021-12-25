// automatically generated by the FlatBuffers compiler, do not modify

package battlecode.schema;

/**
 * The possible types of things that can exist.
 * Note that bullets are not treated as bodies.
 */
public final class BodyType {
  private BodyType() { }
  public static final byte MINER = 0;
  public static final byte BUILDER = 1;
  public static final byte SOLDIER = 2;
  public static final byte SAGE = 3;
  public static final byte ARCHON = 4;
  public static final byte LABORATORY = 5;
  public static final byte WATCHTOWER = 6;

  public static final String[] names = { "MINER", "BUILDER", "SOLDIER", "SAGE", "ARCHON", "LABORATORY", "WATCHTOWER", };

  public static String name(int e) { return names[e]; }
}

