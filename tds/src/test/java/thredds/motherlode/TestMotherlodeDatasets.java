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
package thredds.motherlode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.thredds.ThreddsDataFactory;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.*;
import java.util.*;

import thredds.catalog.crawl.CatalogCrawler;
import thredds.catalog.*;
import ucar.nc2.util.CompareNetcdf2;
import ucar.httpservices.HTTPSession;

/**
 * Run through the named catalogs, open a random dataset from each collection
 */
@RunWith(Parameterized.class)
public class TestMotherlodeDatasets implements CatalogCrawler.Listener {


  @Parameterized.Parameters
 	public static Collection<Object[]> getTestParameters(){
 		return Arrays.asList(new Object[][]{
            {"/idd/modelsNcep.xml", CatalogCrawler.Type.random_direct, false},
            {"/idd/modelsFnmoc.xml", CatalogCrawler.Type.random_direct, false},
            {"/idd/modelsOther.xml", CatalogCrawler.Type.random_direct, false},
            {"/idd/idd/rtmodel.xml", CatalogCrawler.Type.random_direct, false},
    });
 	}

  private String catUrl;
  private CatalogCrawler.Type type;
  private boolean skipDatasetScan = false;

  private InvCatalogFactory catFactory = InvCatalogFactory.getDefaultFactory(true);
  private ThreddsDataFactory tdataFactory = new ThreddsDataFactory();

  private PrintStream out;
  private int countDatasets, countNoAccess, countNoOpen;
  private boolean verbose = true;
  private boolean compareCdm = false;
  private boolean checkUnknown = false;
  private boolean checkGroups = true;

  @Before
  public void init() throws IOException {
      ThreddsDataFactory.setPreferCdm(true);
      HTTPSession.setGlobalUserAgent("TestMotherlodeDatasets");
  }

  public TestMotherlodeDatasets(String catURL, CatalogCrawler.Type type, boolean skipDatasetScan) throws IOException {
    this.catUrl = TestMotherlodePing.server + catURL;
    this.type = type;
    this.skipDatasetScan = skipDatasetScan;
    this.out = System.out; // new PrintStream( new BufferedOutputStream( fout));
  }

  private class FilterDataset implements CatalogCrawler.Filter {

    @Override
    public boolean skipAll(InvDataset ds) {
      if (skipDatasetScan && (ds instanceof InvCatalogRef) && ds.findProperty("DatasetScan") != null) return true;
      return false;
    }
  }

  @Test
  public void crawl() throws IOException {
    out.println("Read " + catUrl);

    InvCatalogImpl cat = catFactory.readXML(catUrl);
    StringBuilder buff = new StringBuilder();
    boolean isValid = cat.check(buff, false);
    if (!isValid) {
      System.out.println("***Catalog invalid= " + catUrl + " validation output=\n" + buff);
      out.println("***Catalog invalid= " + catUrl + " validation output=\n" + buff);
      return;
    }
    out.println("catalog <" + cat.getName() + "> is valid");
    out.println(" validation output=\n" + buff);

    countDatasets = 0;
    countNoAccess = 0;
    countNoOpen = 0;
    int countCatRefs = 0;
    CatalogCrawler crawler = new CatalogCrawler(type, new FilterDataset(), this);
    long start = System.currentTimeMillis();
    try {
      countCatRefs = crawler.crawl(cat, null, verbose ? out : null, null);

    } finally {
      int took = (int) (System.currentTimeMillis() - start) / 1000;

      out.println("***Done " + catUrl + " took = " + took + " secs\n" +
              "   datasets=" + countDatasets + " no access=" + countNoAccess + " open failed=" + countNoOpen + " total catalogs=" + countCatRefs);

    }

    assert countNoAccess == 0;
    assert countNoOpen == 0;
  }

  @Override
  public void getDataset(InvDataset ds, Object context) {
    countDatasets++;

    ThreddsMetadata.GeospatialCoverage gc = ds.getGeospatialCoverage();
    if (gc == null)
      out.printf("   GeospatialCoverage NULL id = %s%n", ds.getID());

    NetcdfDataset ncd = null;
    try {
      Formatter log = new Formatter();
      ncd = tdataFactory.openDataset(ds, false, null, log);

      if (ncd == null) {
        out.printf("**** failed= %s err=%s%n", ds.getName(), log);
        countNoAccess++;

      } else {
        GridDataset gds = new GridDataset(ncd);
        java.util.List<GridDatatype> grids =  gds.getGrids();
        int n = grids.size();
        if (n == 0)
          out.printf("  # Grids == 0 id = %s%n", ds.getID());
        else if (verbose)
          out.printf("   %d %s OK%n", n, gds.getLocationURI());

        if (compareCdm)
          compareCdm(ds, ncd);

        if (checkUnknown) {
          for (GridDatatype vs : grids) {
            if (vs.getDescription().contains("Unknown"))
              out.printf("  %s == %s%n", vs.getFullName(), vs.getDescription());
          }
        }

        if (checkGroups) {
          NetcdfFile nc = gds.getNetcdfFile();
          Group root = nc.getRootGroup();
          if (root.getGroups().size() > 0) {
             out.printf("  GROUPS in %s%n", gds.getLocation());
            for (Group g : root.getGroups()) System.out.printf("%s%n", g.getShortName());
          }
        }
      }

    } catch (Exception e) {
      out.println("**** failed= " + ds.getName());
      e.printStackTrace();
      
      countNoOpen++;
    } finally {
      if (ncd != null) try {
        ncd.close();
      } catch (IOException e) {
      }
    }

  }

  private void compareCdm(InvDataset ds, NetcdfDataset dods) {
    NetcdfDataset cdm = null;
    try {
      ThreddsDataFactory.setPreferCdm(true);
      Formatter log = new Formatter();
      cdm = tdataFactory.openDataset(ds, false, null, log);

      if (cdm == null) {
        out.println("**** failed= " + ds.getName() + " err=" + log);
        countNoAccess++;

      } else {
         // compareFiles(NetcdfFile org, NetcdfFile copy, boolean _compareData, boolean _showCompare, boolean _showEach)
        cdm.enhance();
        CompareNetcdf2.compareFiles(dods, cdm,  new Formatter(System.out), false, false, false);
      }

    } catch (Exception e) {
      out.println("**** failed to open cdm dataset = " + ds.getName());
      e.printStackTrace();

      countNoOpen++;
    } finally {
      ThreddsDataFactory.setPreferCdm(false);
      if (cdm != null) try {
        cdm.close();
      } catch (IOException e) {
      }
    }
  }

  public boolean getCatalogRef(InvCatalogRef dd, Object context) {
    return true;
  }

}
