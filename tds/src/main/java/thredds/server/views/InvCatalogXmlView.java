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
package thredds.server.views;

import org.springframework.web.servlet.view.AbstractView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.io.OutputStream;

import thredds.catalog.InvCatalogFactory;
import thredds.catalog.InvCatalogImpl;
import thredds.util.ContentType;

/**
 * _more_
 *
 * @author edavis
 * @since 4.0
 */
public class InvCatalogXmlView extends AbstractView {
  // private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InvCatalogXmlView.class);

  protected void renderMergedOutputModel(Map model, HttpServletRequest req, HttpServletResponse res)
          throws Exception {
    if (model == null || model.isEmpty())
      throw new IllegalArgumentException("Model must not be null or empty.");
    if (!model.containsKey("catalog"))
      throw new IllegalArgumentException("Model must contain \"catalog\" key.");
    Object o = model.get("catalog");
    if (!(o instanceof InvCatalogImpl))
      throw new IllegalArgumentException("Model must contain an InvCatalogImpl object.");
    InvCatalogImpl cat = (InvCatalogImpl) o;

    res.setContentType(ContentType.xml.getContentHeader());
    OutputStream os = null;
    if (!req.getMethod().equals("HEAD")) {
      try {
        os = res.getOutputStream();
        // Return catalog as XML response.
        InvCatalogFactory catFactory = InvCatalogFactory.getDefaultFactory(false);
        catFactory.writeXML(cat, os);
      } finally {
        if (os != null)
          os.close();
      }
    }
  }
}