package processing.data;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;

import processing.core.PApplet;


/**
 * A simple class to use a String as a lookup for an int value.
 */
public class IntDict {

  /** Number of elements in the table */
  protected int count;

  protected String[] keys;
  protected int[] values;

  /** Internal implementation for faster lookups */
  private HashMap<String, Integer> indices = new HashMap<String, Integer>();


//  /**
//   * Create a new object by counting the number of times each unique entry
//   * shows up in the specified String array.
//   */
//  static public IntHash fromTally(String[] list) {
//    IntHash outgoing = new IntHash();
//    for (String s : list) {
//      outgoing.inc(s);
//    }
//    outgoing.crop();
//    return outgoing;
//  }
//
//
//  static public IntHash fromOrder(String[] list) {
//    IntHash outgoing = new IntHash();
//    for (int i = 0; i < list.length; i++) {
//      outgoing.set(list[i], i);
//    }
//    return outgoing;
//  }


  public IntDict() {
    count = 0;
    keys = new String[10];
    values = new int[10];
  }


  /**
   * Create a new lookup with a specific size. This is more efficient than not
   * specifying a size. Use it when you know the rough size of the thing you're creating.
   */
  public IntDict(int length) {
    count = 0;
    keys = new String[length];
    values = new int[length];
  }


  /**
   * Read a set of entries from a Reader that has each key/value pair on
   * a single line, separated by a tab.
   */
  public IntDict(BufferedReader reader) {
//  public IntHash(PApplet parent, String filename) {
    String[] lines = PApplet.loadStrings(reader);
    keys = new String[lines.length];
    values = new int[lines.length];

//    boolean csv = (lines[0].indexOf('\t') == -1);
    for (int i = 0; i < lines.length; i++) {
//      String[] pieces = csv ? Table.splitLineCSV(lines[i]) : PApplet.split(lines[i], '\t');
      String[] pieces = PApplet.split(lines[i], '\t');
      if (pieces.length == 2) {
        keys[count] = pieces[0];
        values[count] = PApplet.parseInt(pieces[1]);
        count++;
      }
    }
  }


  public IntDict(String[] keys, int[] values) {
    if (keys.length != values.length) {
      throw new IllegalArgumentException("key and value arrays must be the same length");
    }
    this.keys = keys;
    this.values = values;
    count = keys.length;
    for (int i = 0; i < count; i++) {
      indices.put(keys[i], i);
    }
  }


  public int size() {
    return count;
  }


  /** Remove all entries. */
  public void clear() {
    count = 0;
    indices = new HashMap<String, Integer>();
  }


  public String key(int index) {
    return keys[index];
  }


  private void crop() {
    if (count != keys.length) {
      keys = PApplet.subset(keys, 0, count);
      values = PApplet.subset(values, 0, count);
    }
  }


  /**
   * Return the internal array being used to store the keys. Allocated but
   * unused entries will be removed. This array should not be modified.
   */
  public String[] keys() {
    crop();
    return keys;
  }


//  public Iterable<String> keys() {
//    return new Iterable<String>() {
//
//      @Override
//      public Iterator<String> iterator() {
  public Iterator<String> keyIterator() {
    return new Iterator<String>() {
      int index = -1;

      public void remove() {
        removeIndex(index);
      }

      public String next() {
        return key(++index);
      }

      public boolean hasNext() {
        return index+1 < size();
      }
    };
//      }
//    };
  }


  /**
   * Return a copy of the internal keys array. This array can be modified.
   */
  public String[] keyArray() {
    return keyArray(null);
  }


  public String[] keyArray(String[] outgoing) {
    if (outgoing == null || outgoing.length != count) {
      outgoing = new String[count];
    }
    System.arraycopy(keys, 0, outgoing, 0, count);
    return outgoing;
  }


  public int value(int index) {
    return values[index];
  }


  public Iterable<Integer> values() {
    return new Iterable<Integer>() {

      @Override
      public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
          int index = -1;

          public void remove() {
            removeIndex(index);
          }

          public Integer next() {
            return value(++index);
          }

          public boolean hasNext() {
            return index+1 < size();
          }
        };
      }
    };
  }


  /**
   * Create a new array and copy each of the values into it.
   */
  public int[] valueArray() {
    return valueArray(null);
  }


  /**
   * Fill an already-allocated array with the values (more efficient than
   * creating a new array each time). If 'array' is null, or not the same
   * size as the number of values, a new array will be allocated and returned.
   */
  public int[] valueArray(int[] array) {
    if (array == null || array.length != size()) {
      array = new int[count];
    }
    System.arraycopy(values, 0, array, 0, count);
    return array;
  }


  /**
   * Return a value for the specified key.
   */
  public int get(String key) {
    int index = index(key);
    if (index == -1) return 0;
    return values[index];
  }


  public void set(String key, int amount) {
    int index = index(key);
    if (index == -1) {
      create(key, amount);
    } else {
      values[index] = amount;
    }
  }


  public boolean hasKey(String key) {
    return index(key) != -1;
  }


  /** Increase the value of a specific key by 1. */
  public void increment(String key) {
    add(key, 1);
  }


  public void add(String key, int amount) {
    int index = index(key);
    if (index == -1) {
      create(key, amount);
    } else {
      values[index] += amount;
    }
  }


  public void sub(String key, int amount) {
    add(key, -amount);
  }


  public void mult(String key, int amount) {
    int index = index(key);
    if (index != -1) {
      values[index] *= amount;
    }
  }


  public void div(String key, int amount) {
    int index = index(key);
    if (index != -1) {
      values[index] /= amount;
    }
  }


  public int index(String what) {
    Integer found = indices.get(what);
    return (found == null) ? -1 : found.intValue();
  }


  protected void create(String what, int much) {
    if (count == keys.length) {
      keys = PApplet.expand(keys);
      values = PApplet.expand(values);
    }
    indices.put(what, new Integer(count));
    keys[count] = what;
    values[count] = much;
    count++;
  }


  public void remove(String key) {
    removeIndex(index(key));
  }


  public void removeIndex(int index) {
    //System.out.println("index is " + which + " and " + keys[which]);
    indices.remove(keys[index]);
    for (int i = index; i < count-1; i++) {
      keys[i] = keys[i+1];
      values[i] = values[i+1];
      indices.put(keys[i], i);
    }
    count--;
    keys[count] = null;
    values[count] = 0;
  }


  protected void swap(int a, int b) {
    String tkey = keys[a];
    int tvalue = values[a];
    keys[a] = keys[b];
    values[a] = values[b];
    keys[b] = tkey;
    values[b] = tvalue;

    indices.put(keys[a], new Integer(a));
    indices.put(keys[b], new Integer(b));
  }


  /**
   * Sort the keys alphabetically (ignoring case). Uses the value as a
   * tie-breaker (only really possible with a key that has a case change).
   */
  public void sortKeys() {
    sortImpl(true, false);
  }


  public void sortKeysReverse() {
    sortImpl(true, true);
  }


  /**
   * Sort by values in descending order (largest value will be at [0]).
   */
  public void sortValues() {
    sortImpl(false, false);
  }


  public void sortValuesReverse() {
    sortImpl(false, true);
  }


  protected void sortImpl(final boolean useKeys, final boolean reverse) {
    Sort s = new Sort() {
      @Override
      public int size() {
        return count;
      }

      @Override
      public float compare(int a, int b) {
        int diff = 0;
        if (useKeys) {
          diff = keys[a].compareToIgnoreCase(keys[b]);
          if (diff == 0) {
            return values[a] - values[b];
          }
        } else {  // sort values
          diff = values[a] - values[b];
          if (diff == 0) {
            diff = keys[a].compareToIgnoreCase(keys[b]);
          }
        }
        return reverse ? -diff : diff;
      }

      @Override
      public void swap(int a, int b) {
        IntDict.this.swap(a, b);
      }
    };
    s.run();
  }


  /** Returns a duplicate copy of this object. */
  public IntDict copy() {
    IntDict outgoing = new IntDict(count);
    System.arraycopy(keys, 0, outgoing.keys, 0, count);
    System.arraycopy(values, 0, outgoing.values, 0, count);
    for (int i = 0; i < count; i++) {
      outgoing.indices.put(keys[i], i);
    }
    outgoing.count = count;
    return outgoing;
  }


  /**
   * Write tab-delimited entries out to
   * @param writer
   */
  public void write(PrintWriter writer) {
    for (int i = 0; i < count; i++) {
      writer.println(keys[i] + "\t" + values[i]);
    }
    writer.flush();
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName() + " size=" + size() + " { ");
    for (int i = 0; i < size(); i++) {
      if (i != 0) {
        sb.append(", ");
      }
      sb.append("\"" + keys[i] + "\": " + values[i]);
    }
    sb.append(" }");
    return sb.toString();
  }
}
