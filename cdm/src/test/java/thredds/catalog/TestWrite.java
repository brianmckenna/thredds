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
package thredds.catalog;

import junit.framework.*;
import java.io.*;
import java.util.*;

/** sanity check: does it read? */

public class TestWrite extends TestCase {
  static boolean debugCompare = true, debugCompareList = true;

  public void testWrite() {
    testWrite1( "test1.xml");
  }

  private void testWrite1(String filename) {
    InvCatalogImpl cat = TestCatalogAll.open( filename, true);

    // create a file and write it out
    File tmpDir = new File(TestCatalogAll.tmpDir);
    if (!tmpDir.exists()) {
        boolean ret = tmpDir.mkdirs();
        if (!ret) System.out.println("Error creating directory");
    }

    String fileOutName = TestCatalogAll.tmpDir + filename + ".tmp";
    System.out.println(" output filename= "+fileOutName);

    File tmpFile = new File(fileOutName);

    try {
      OutputStream out = new BufferedOutputStream(
              new FileOutputStream(tmpFile));
      cat.writeXML( out);
      out.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      assert false;
    }

    // read it back in
    String urlR = "file:"+fileOutName;
    InvCatalogImpl catR = TestCatalogAll.openAbsolute( urlR, true);

    if (!cat.equals( catR)) {
      System.out.println("cat = "+cat.hashCode()+" catR= "+catR.hashCode()+" not equals");
      compare( cat, catR);
    }
    compare( cat, catR);
    assert cat.equals( catR);
  }

  private void compare(InvCatalogImpl cat, InvCatalogImpl catR) {
    List datasets = cat.getDatasets();
    List datasetsR = catR.getDatasets();

    if (datasets.size() != datasetsR.size()) {
      System.out.printf("%s%n", "different number of datasets");
    }

    int n = Math.min(datasets.size(), datasetsR.size());
    for (int i=0; i<n; i++) {
      InvDatasetImpl dd = (InvDatasetImpl) datasets.get(i);
      compareDatasets( dd, (InvDatasetImpl) datasetsR.get(i));
    }
  }

  private void compareDatasets(InvDatasetImpl d, InvDatasetImpl dR) {
    if (debugCompare) System.out.println(" compare datasets ("+d+") and ("+dR+")");
    /* compareList( d.getDocumentation(), dR.getDocumentation());
    compareList( d.getAccess(), dR.getAccess()); */
    compareList( d.getMetadata(), dR.getMetadata());
    // compareListVariables( d.getVariables(), dR.getVariables()); */

    List datasets = d.getDatasets();
    List datasetsR = dR.getDatasets();

    for (int i=0; i<datasets.size(); i++) {
      InvDatasetImpl dd = (InvDatasetImpl) datasets.get(i);
      InvDatasetImpl ddR = (InvDatasetImpl) datasetsR.get(i);
      compareDatasets( dd, ddR);
    }
    assert d.equals(dR) : "**("+d.getID()+") not equal ("+dR.getID()+")";
  }


  private void compareList(List d, List dR) {
    Iterator iter = d.iterator();
    while (iter.hasNext()) {
      Object item = iter.next();
      int index = dR.indexOf( item);
      if( index < 0) System.out.println("   cant find "+item.getClass().getName()+" "+item +" in output ");
      else if (debugCompareList) System.out.println("   item ok = ("+item+")");
    }

    iter = dR.iterator();
    while (iter.hasNext()) {
      Object item = iter.next();
      int index = d.indexOf( item);
      if( index < 0) System.out.println("   cant find "+item.getClass().getName()+" "+item +" in input ");
      else if (debugCompareList) System.out.println("   itemR ok = ("+item+")");
    }
  }

  private void compareListVariables(List d, List dR) {
    Iterator iter = d.iterator();
    while (iter.hasNext()) {
      ThreddsMetadata.Variables item = (ThreddsMetadata.Variables) iter.next();
      int index = dR.indexOf( item);
      if( index < 0) System.out.println("   cant find "+item.getClass().getName()+" "+item +" in output ");
      else if (debugCompareList) {
        ThreddsMetadata.Variables item2 = (ThreddsMetadata.Variables) dR.get( index);
        System.out.println("   Variables ok = ("+item+") == ("+item2+")");
      }
    }

    iter = dR.iterator();
    while (iter.hasNext()) {
      ThreddsMetadata.Variables item = (ThreddsMetadata.Variables) iter.next();
      int index = d.indexOf( item);
      if( index < 0) System.out.println("   cant find "+item.getClass().getName()+" "+item +" in input ");
      //else if (debugCompareList) System.out.println("   itemR ok = ("+item+")");
    }
  }


  private void check(String url) {
    InvCatalogImpl cat = (InvCatalogImpl) TestCatalogAll.open( url, true);

    // read it back in
    String urlR = url + ".out.xml";
    InvCatalogImpl catR = (InvCatalogImpl) TestCatalogAll.open( urlR, true);

    boolean e = cat.equals( catR);
    System.out.println("cat = "+cat.hashCode()+" catR= "+catR.hashCode()+" equals = "+e);
    assert cat.equals( catR);
  }

}
