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
/**
 * User: rkambic
 * Date: Oct 13, 2010
 * Time: 11:19:08 AM
 */

package thredds.server.radarServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import org.springframework.web.servlet.mvc.AbstractController;
import thredds.catalog.InvCatalogFactory;
import thredds.catalog.InvCatalogImpl;
import thredds.catalog.InvDatasetImpl;
import thredds.catalog.InvDatasetScan;
import thredds.catalog.ThreddsMetadata;
import thredds.server.config.TdsContext;
import thredds.servlet.HtmlWriter;
import thredds.util.TdsPathUtils;
import ucar.nc2.time.CalendarDateRange;
import ucar.unidata.geoloc.LatLonRect;

/**
 * Returns catalogs/datasets for the RadarServer data
 */
@Controller
public class CatalogRadarServerController extends AbstractController {
  private org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(getClass());

  @Autowired
  private TdsContext tdsContext;

  /**
   * The view to forward to in case an dataset needs to be created.
   */
  //private static final String CREATE_VIEW = "forward:createdataset.htm";

  /**
   * The model key used to retrieve the message from the model.
   */
  private static final String MODEL_KEY = "message";

  /**
   * The unique key for retrieving the text associated with this message.
   */
  private static final String MSG_CODE = "message.create.dataset";


  public CatalogRadarServerController() {
  }

  protected String getControllerPath() {
    return "/radarServer/";
  }

  /**
   * Spring Framework controller
   *
   * @param request  HttpServletRequest
   * @param response HttpServletResponse
   * @return ModelAndView
   * @throws Exception
   */
  @RequestMapping(value = {"/radarServer/**/catalog.xml", "/radarServer/**/catalog.html", "/radarServer/**/dataset.xml", "/radarServer/**/dataset.html"},
          method = {RequestMethod.GET, RequestMethod.HEAD})
  protected ModelAndView handleRequestInternal(HttpServletRequest request,
                                               HttpServletResponse response) throws Exception {
    DatasetRepository.init(tdsContext);

    try {

      String path = TdsPathUtils.extractPath(request, getControllerPath());
      if (path == null) path = "";

          // return main  catalog xml or html
    if (path.equals("catalog.xml") || path.equals("dataset.xml")) {
      // InvCatalogFactory factory = DataRootHandler.getInstance().getCatalogFactory(false);
      InvCatalogFactory factory = InvCatalogFactory.getDefaultFactory(false); // no validation
      String catAsString = factory.writeXML(DatasetRepository.cat);
      PrintWriter pw = response.getWriter();
      pw.println(catAsString);
      pw.flush();
      return null;
    }

    if (path.equals("catalog.html") || path.equals("dataset.html")) {
      try {
        HtmlWriter.getInstance().writeCatalog(request, response, DatasetRepository.cat, true); // show catalog as HTML
      } catch (Exception e) {
        log.error("Radar HtmlWriter failed ", e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "radarServer HtmlWriter error " + path);
      }
      return null;
    }

    InvCatalogImpl catalog = DatasetRepository.cat;  // default is the complete radarCollection catalog
    if (path.contains("level2/catalog.") || path.contains("level3/catalog.") || path.contains("level2/dataset.") || path.contains("level3/dataset.")) {
      catalog = level2level3catalog(DatasetRepository.cat, path);
      if (catalog == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return null;
      }

      // returns specific dataset information, ie level2/IDD  used by IDV
    } else if (path.endsWith("dataset.xml") || path.endsWith("catalog.xml")) {
      Map<String, Object> model = datasetInfoXml(DatasetRepository.cat, path);
      if (model == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return null;
      }

      return new ModelAndView("datasetXml", model);
    }

      if (catalog == null) {
        return null;
        // ModelAndView mav = new ModelAndView(CREATE_VIEW); // WTF ??
        // mav.addObject(MODEL_KEY, MSG_CODE);
        // return mav;

       } else {
         if (path.endsWith(".html")) {
           HtmlWriter.getInstance().writeCatalog(request, response, catalog, true);
           return null;
         } else
           return new ModelAndView("threddsInvCatXmlView", "catalog", catalog);
       }


    } catch (RadarServerException e) {
      throw e; // pass it onto Spring exceptionResolver
    } catch (Throwable e) {
      log.error("handleRequestInternal(): Problem handling request.", e);
      throw new RadarServerException("handleRequestInternal(): Problem handling request.", e);
    }
  }

  /*
   * handles level2 and level3 type of catalog requests
   */
  private InvCatalogImpl level2level3catalog(InvCatalogImpl cat, String pathInfo)
          throws RadarServerException, IOException {

    InvCatalogImpl tCat = null;
    try {
      // extract dataset path
      String dsPath;
      if (pathInfo.indexOf("/dataset") > 0) {
        dsPath = pathInfo.substring(0, pathInfo.indexOf("/dataset"));
      } else if (pathInfo.indexOf("/catalog") > 0) {
        dsPath = pathInfo.substring(0, pathInfo.indexOf("/catalog"));
      } else {
        log.error("RadarServer.datasetInfoXml", "Invalid url request");
        throw new RadarServerException("Invalid url request");
      }

      // clone catalog
      ByteArrayOutputStream os = new ByteArrayOutputStream(10000);
      InvCatalogFactory factory = InvCatalogFactory.getDefaultFactory(false);
      factory.writeXML(cat, os, true);
      tCat = factory.readXML(
              new ByteArrayInputStream(os.toByteArray()), DatasetRepository.catURI);
      // modify clone for only requested datasets
      Iterator parents = tCat.getDatasets().iterator();
      while (parents.hasNext()) {
        ArrayList<InvDatasetImpl> delete = new ArrayList<InvDatasetImpl>();
        InvDatasetImpl top = (InvDatasetImpl) parents.next();
        Iterator tDatasets = top.getDatasets().iterator();
        while (tDatasets.hasNext()) {
          InvDatasetImpl ds = (InvDatasetImpl) tDatasets.next();
          if (ds instanceof InvDatasetScan) {
            InvDatasetScan ids = (InvDatasetScan) ds;
            if (ids.getPath() == null)
              continue;
            if (ids.getPath().contains(dsPath)) {
              ids.setXlinkHref(ids.getPath() + "/dataset.xml");
            } else {
              delete.add(ds);
            }
          }
        }
        // remove datasets
        for (InvDatasetImpl idi : delete) {
          top.removeDataset(idi);
        }
      }
    } catch (Throwable e) {
      log.error("RadarServer.level2level3catalog", "Invalid url request");
      throw new RadarServerException("Invalid catalog request", e);
    }
    return tCat;
  }

  /**
   * @param cat      radarCollections catalog
   * @param pathInfo requested dataset by path
   * @return model dataset object to be used by datasetXml.jsjp
   * @throws RadarServerException controlled exception with information about failure
   * @throws IOException          major
   */
  private Map<String, Object> datasetInfoXml(InvCatalogImpl cat, String pathInfo)
          throws RadarServerException, IOException {

    // dataset results are stored in model
    Map<String, Object> model = new HashMap<String, Object>();

    InvDatasetScan ds = null;
    boolean found = false;
    try {
      // extract dataset path
      String dsPath;
      if (pathInfo.indexOf("/dataset") > 0) {
        dsPath = pathInfo.substring(0, pathInfo.indexOf("/dataset"));
      } else if (pathInfo.indexOf("/catalog") > 0) {
        dsPath = pathInfo.substring(0, pathInfo.indexOf("/catalog"));
      } else {
        log.error("RadarServer.datasetInfoXml", "Invalid url request");
        throw new RadarServerException("Invalid url request");
      }
      // search for dataset by dsPath
      Iterator parents = cat.getDatasets().iterator();
      InvDatasetImpl top = (InvDatasetImpl) parents.next();
      Iterator tDatasets = top.getDatasets().iterator();
      while (tDatasets.hasNext()) {
        InvDatasetImpl idsi = (InvDatasetImpl) tDatasets.next();
        if (idsi instanceof InvDatasetScan) {
          ds = (InvDatasetScan) idsi;
          if (ds.getPath() == null)
            continue;
          if (ds.getPath().contains(dsPath)) {
            found = true;
            break;
          } else {
            continue;
          }
        }
      }
    } catch (Throwable e) {
      log.error("RadarServer.datasetInfoXml", e);
      throw new RadarServerException("CatalogRadarServerController.datasetInfoXml", e);
    }
    if (!found) {
      log.error("RadarServer.datasetInfoXml", "Invalid url request");
      throw new RadarServerException("Invalid url request");
    }

    // create dataset by storing necessary information in model object that will
    // be displayed by datasetXml.jsp
    // add ID
    model.put("ID", ds.getID());
    model.put("urlPath", ds.getPath());
    model.put("dataFormat", ds.getDataFormatType());
    model.put("documentation", ds.getSummary());
    CalendarDateRange dr = ds.getCalendarDateCoverage();
    /*
    if (pathInfo.contains("IDD")) {
      pw.print(rm.getStartDateTime(ds.getPath()));
    } else {
      pw.print(dr.getStart().toDateTimeStringISO());
    }
    */    //TODO: check
    //pw.print(dr.getStart().toDateTimeStringISO());
    model.put("tstart", dr.getStart().toString());
    model.put("tend", dr.getEnd().toString());
    ThreddsMetadata.GeospatialCoverage gc = ds.getGeospatialCoverage();
    LatLonRect bb = new LatLonRect();
    gc.setBoundingBox(bb);
    model.put("north", gc.getLatNorth());
    model.put("south", gc.getLatSouth());
    model.put("east", gc.getLonEast());
    model.put("west", gc.getLonWest());

    if (ds.getVariables().size() > 0) {
      ThreddsMetadata.Variables cvs = ds.getVariables().get(0);
      List vl = cvs.getVariableList();
      ArrayList<RsVar> variables = new ArrayList<>();
      for (int j = 0; j < vl.size(); j++) {
        ThreddsMetadata.Variable v = (ThreddsMetadata.Variable) vl.get(j);
        RsVar rsv = new RsVar();
        rsv.setName(v.getName());
        rsv.setVname(v.getVocabularyName());
        rsv.setUnits(v.getUnits());
        variables.add(rsv);
      }
      model.put("variables", variables);
    }
    // not necessary to get stations, IDV does separate request for stations
    //String[] stations = rm.stationsDS(radarType, dataLocation.get(ds.getPath()));
    //rm.printStations(stations, pw, radarType );

    return model;
  }

  /*
   * Used to store the information about a Radar variable from catalog
   */
  public class RsVar {

    private String name;

    private String vname;

    private String units;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getVname() {
      return vname;
    }

    public void setVname(String vname) {
      this.vname = vname;
    }

    public String getUnits() {
      return units;
    }

    public void setUnits(String units) {
      this.units = units;
    }
  }
}
