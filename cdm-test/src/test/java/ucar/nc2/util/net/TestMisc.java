/*
 * Copyright (c) 1998 - 2012. University Corporation for Atmospheric Research/Unidata
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

package ucar.nc2.util.net;

import ucar.httpservices.*;

import org.junit.Test;
import ucar.nc2.util.EscapeStrings;
import ucar.nc2.util.UnitTestCommon;

import java.net.URI;

import static junit.framework.Assert.assertTrue;

public class TestMisc extends UnitTestCommon
{

  static {HTTPSession.TESTING = true;}

  //////////////////////////////////////////////////

  // Define the test sets

  int passcount = 0;
  int xfailcount = 0;
  int failcount = 0;
  boolean verbose = true;
  boolean pass = false;

  String datadir = null;
  String threddsroot = null;

  public TestMisc() {
    setTitle("HTTP Session tests");
  }

  static final String[] esinputs = {
          "http://localhost:8081/dts/test.01",
          "http://localhost:8081///xx/",
          "http://localhost:8081/<>^/`/",
  };
  static final String[] esoutputs = {
          "http://localhost:8081/dts/test.01",
          "http://localhost:8081///xx/",
          "http://localhost:8081/%3c%3e%5e/%60/",
  };

  @Test
  public void
  testEscapeStrings() throws Exception {
    pass = true;
    assert (esinputs.length == esoutputs.length);
    for (int i = 0; i < esinputs.length && pass; i++) {
      String result = EscapeStrings.escapeURL(esinputs[i]);
        System.err.printf("input= |%s|\n",esinputs[i]);
        System.err.printf("result=|%s|\n",result);
        System.err.printf("output=|%s|\n",esoutputs[i]);
      if (!result.equals(esoutputs[i])) pass = false;
      System.out.printf("input=%s output=%s pass=%s\n", esinputs[i], result, pass);
    }
    assertTrue("TestMisc.testEscapeStrings", pass);
  }


  @Test
  public void
  testUTF8Stream()
    throws Exception
  {
     pass = true;

     String catalogName = "http://thredds.ucar.edu/thredds/catalog.xml";
     URI catalogURI = new URI(catalogName);

     HTTPSession client = null;
     HTTPMethod m = null;
     try {
       client = HTTPFactory.newSession(catalogName);
       m = HTTPFactory.Get(client);

       int statusCode = m.execute();
       System.out.printf("status = %d%n", statusCode);

       try {
           String content = m.getResponseAsString("ASC");
           System.out.printf("cat = %s%n", content);
       }  catch (Throwable t) {
           t.printStackTrace();
       }
     } finally {
       if (client != null) client.close();
     }

   }
}

