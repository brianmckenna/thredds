// $Id: TestBasic.java,v 1.1.1.1 2002/12/20 16:40:27 john Exp $
/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.util.prefs;

import org.junit.Before;
import org.junit.Test;
import ucar.nc2.util.Misc;

import java.io.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class TestJavaUtilPreferences {

  // only works if System.setProperty called before anything else
  @Before
  public void testWho() {
    System.setProperty("java.util.prefs.PreferencesFactory", "ucar.util.prefs.PreferencesExtFactory");
    System.out.printf("TestJavaUtilPreferences: java.util.prefs.PreferencesFactory='%s'%n", System.getProperty("java.util.prefs.PreferencesFactory"));

    // this makes PreferencesExt the SPI
    Preferences userRoot = Preferences.userRoot();
    assert userRoot instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();
    //assert false : "assert is on";
  }

  @Test
  public void testPutGet() {
    try {

      Preferences userRoot = Preferences.userRoot();
      assert userRoot instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();

      userRoot.putDouble("testD", 3.14157);
      double d = userRoot.getDouble("testD", 0.0);
      assert Misc.closeEnough(d, 3.14157) : "double failed";

      userRoot.putFloat("testF", 1.23456F);
      float f = userRoot.getFloat("testF", 0.0F);
      assert Misc.closeEnough(f, 1.23456F) : "float failed";

      userRoot.putLong("testL", 12345678900L);
      long ll = userRoot.getLong("testL", 0);
      assert ll == 12345678900L : "long failed";

      userRoot.putInt("testI", 123456789);
      int ii = userRoot.getInt("testI", 0);
      assert ii == 123456789 : "int failed";

      userRoot.put("testS", "youdBeDeadbyNow");
      String s = userRoot.get("testS", "");
      assert s.equals("youdBeDeadbyNow") : "String failed";

      userRoot.putBoolean("testB", true);
      boolean b = userRoot.getBoolean("testB", false);
      assert b : "boolean failed";

      byte[] barr = new byte[3];
      byte[] barr2 = new byte[3];
      barr[0] = 1;
      barr[1] = 2;
      barr[2] = 3;
      userRoot.putByteArray("testBA", barr);
      byte[] ba = userRoot.getByteArray("testBA", barr2);
      for (int i = 0; i < 3; i++)
        assert ba[i] == barr[i] : "BA failed";

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testSubnode() {
    try {

      Preferences userRoot = Preferences.userRoot();
      Preferences subNode = userRoot.node("SemperUbi");
      assert subNode instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();

      String[] keys = userRoot.keys();
      for (String key : keys) {
        String value = userRoot.get(key, "failed");
        subNode.put(key, value);
      }

      float f = subNode.getFloat("testF", 0.0F);
      assert Misc.closeEnough(f, 1.23456F) : "float failed";

      long ll = subNode.getLong("testL", 0);
      assert ll == 12345678900L : "long failed";

      int ii = subNode.getInt("testI", 0);
      assert ii == 123456789 : "int failed";

      String s = subNode.get("testS", "");
      assert s.equals("youdBeDeadbyNow") : "String failed";

      boolean b = subNode.getBoolean("testB", false);
      assert b : "boolean failed";

      byte[] barr = new byte[3];
      byte[] barr2 = new byte[3];
      barr[0] = 1;
      barr[1] = 2;
      barr[2] = 3;
      byte[] ba = subNode.getByteArray("testBA", barr2);
      for (int i = 0; i < 3; i++)
        assert ba[i] == barr[i] : "BA failed";

    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  // @Test
  public void testExport() {
    Preferences userRoot = Preferences.userRoot();
    Preferences fromNode = userRoot.node("SemperUbi");
    assert fromNode instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();

    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream(10000);
      fromNode.exportNode(os);
      String xml = new String(os.toByteArray());
      xml = substitute(xml, "SemperUbi", "SubUbi");
      ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes());
      Preferences.importPreferences(is);
      userRoot.exportSubtree(System.out);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testRemove() {
    Preferences userRoot = Preferences.userRoot();
    Preferences subNode = userRoot.node("SemperUbi");
    assert subNode instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();

    long ll;
    subNode.putLong("testL", 12345678900L);
    ll = subNode.getLong("testL", 0);
    assert ll == 12345678900L : "long failed 2";

    subNode.remove("testL");
    ll = subNode.getLong("testL", 0);
    assert ll == 0 : "remove failed";
  }

  @Test
  public void testRemoveNode() throws BackingStoreException {
      Preferences userRoot = Preferences.userRoot();
      Preferences subNode = userRoot.node("SemperUbi");
      Preferences newNode = subNode.node("SubSemperUbi");
      assert newNode instanceof PreferencesExt : "Factory not set = " + userRoot.getClass().getName();

      newNode.putLong("testL", 12345678900L);
      long ll = subNode.getLong("testL", 0);
      assert ll == 0 : "testRemoveNode failed";

      newNode.removeNode();
      String[] kidName = subNode.childrenNames();
      //System.out.println(" # children= "+kidName.length);
      for (String aKidName : kidName) {
        //System.out.println("  "+kidName[i]);
        assert !aKidName.equals("SubSemperUbi") : "testRemoveNode failed 1";
      }


      newNode = subNode.node("SubSemperUbi");
      ll = newNode.getLong("testL", 123L);
      assert ll == 123 : "testRemoveNode failed 2 " + ll;
  }

  /**
   * Find all occurences of the "match" in original, and substitute the "subst" string
   *
   * @param original: starting string
   * @param match:    string to match
   * @param subst:    string to substitute
   * @return a new string with substitutions
   */
  String substitute(String original, String match, String subst) {
    String s = original;
    int pos;
    while (0 <= (pos = s.indexOf(match))) {
      StringBuilder sb = new StringBuilder(s);
      s = sb.replace(pos, pos + match.length(), subst).toString();
    }

    return s;
  }

}
