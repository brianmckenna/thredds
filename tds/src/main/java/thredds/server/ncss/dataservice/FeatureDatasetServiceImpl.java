/*
 * Copyright 1998-2014 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.server.ncss.dataservice;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;

import thredds.catalog.InvDatasetFeatureCollection;
import thredds.servlet.DatasetHandler;
import ucar.nc2.NetcdfFile;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.NetcdfDataset.Enhance;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

/**
 * @author mhermida
 */
@Service
public class FeatureDatasetServiceImpl implements FeatureDatasetService {


  @Override
  public FeatureDataset findDatasetByPath(HttpServletRequest req, HttpServletResponse res) throws IOException {
    return findDatasetByPath(req, res, req.getPathInfo());
  }

  @Override
  public FeatureDataset findDatasetByPath(HttpServletRequest req, HttpServletResponse res, String datasetPath) throws IOException {

    FeatureType type;
    FeatureDataset fd = null;
    InvDatasetFeatureCollection ftCollection = DatasetHandler.getFeatureCollection(req, res, datasetPath);

    if (ftCollection != null) {
      type = ftCollection.getDataType();
      if (type == FeatureType.GRID) {
        fd = DatasetHandler.openGridDataset(req, res, datasetPath);
        //builds a FeatureDataset from an TypedDataset
        // fd = new ucar.nc2.dt.grid.GridDataset(new NetcdfDataset(gds.getNetcdfFile()));
      }

      if (type.isPointFeatureType()) {
        fd = ftCollection.getFeatureDataset();
      }

    } else {

      //Try as file?
      NetcdfFile ncfile = DatasetHandler.getNetcdfFile(req, res, datasetPath);
      Set<Enhance> enhance = Collections.unmodifiableSet(EnumSet.of(Enhance.CoordSystems, Enhance.ConvertEnums));
      if (ncfile != null) {
        //Wrap it into a FeatureDataset
        fd = FeatureDatasetFactoryManager.wrap(
                FeatureType.ANY,                  // will check FeatureType below if needed...
                NetcdfDataset.wrap(ncfile, enhance),
                null,
                new Formatter(System.err));       // better way to do this?
      }
    }

    return fd;
  }

}
