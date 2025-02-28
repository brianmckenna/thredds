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

package thredds.server.opendap;

import opendap.dap.*;
import opendap.servers.*;
import opendap.dap.parsers.ParseException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.net.URI;

import opendap.servlet.*;
import opendap.servlet.AbstractServlet;
import thredds.server.admin.DebugController;
import thredds.servlet.*;
import thredds.servlet.filter.CookieFilter;
import ucar.ma2.DataType;
import ucar.ma2.Range;
import ucar.ma2.Section;
import ucar.nc2.dods.DODSNetcdfFile;
import ucar.nc2.NetcdfFile;
import ucar.nc2.util.EscapeStrings;

/**
 * THREDDS opendap server.
 *
 * @author jcaron
 * @author Nathan David Potter
 * @since Apr 27, 2009 (branched)
 */
public class OpendapServlet extends AbstractServlet {
  static final String DEFAULTCONTEXTPATH = "/thredds";
  static final String GDATASET = "guarded_dataset";

  static public org.slf4j.Logger log
             = org.slf4j.LoggerFactory.getLogger(OpendapServlet.class);

  private boolean allowSessions = false;
  private boolean allowDeflate = false; // handled by Tomcat

  private String odapVersionString = "opendap/3.7";

  private URI baseURI = null;

  private int ascLimit = 50;
  private int binLimit = 500;

  private boolean debugSession = false;

  public void init() throws javax.servlet.ServletException {
    super.init();

    org.slf4j.Logger logServerStartup = org.slf4j.LoggerFactory.getLogger("serverStartup");
    logServerStartup.info(getClass().getName() + " initialization start");

    this.ascLimit = ThreddsConfig.getInt("Opendap.ascLimit", ascLimit);
    this.binLimit = ThreddsConfig.getInt("Opendap.binLimit", binLimit);

    this.odapVersionString = ThreddsConfig.get("Opendap.serverVersion", odapVersionString);
    logServerStartup.info(getClass().getName() + " version= " + odapVersionString + " ascLimit = " + ascLimit + " binLimit = " + binLimit);

    // debugging actions
    makeDebugActions();

    logServerStartup.info(getClass().getName() + " initialization done");
  }

  public String getServerVersion() {
    return this.odapVersionString;
  }

  // Servlets that support HTTP GET requests and can quickly determine their last modification time should
  // override this method. This makes browser and proxy caches work more effectively, reducing the load on
  // server and network resources.
  protected long getLastModified(HttpServletRequest req) {
    String query = req.getQueryString();
    if (query != null) return -1;

    String path = req.getPathInfo();
    if (path == null) return -1;

    if (path.endsWith(".asc"))
      path = path.substring(0, path.length() - 4);
    else if (path.endsWith(".ascii"))
      path = path.substring(0, path.length() - 6);
    else if (path.endsWith(".das"))
      path = path.substring(0, path.length() - 4);
    else if (path.endsWith(".dds"))
      path = path.substring(0, path.length() - 4);
    else if (path.endsWith(".ddx"))
      path = path.substring(0, path.length() - 4);
    else if (path.endsWith(".dods"))
      path = path.substring(0, path.length() - 5);
    else if (path.endsWith(".html"))
      path = path.substring(0, path.length() - 5);
    else if (path.endsWith(".info"))
      path = path.substring(0, path.length() - 5);
    else if (path.endsWith(".opendap"))
      path = path.substring(0, path.length() - 5);
    else
      return -1;

    // if (null != DatasetHandler.findResourceControl( path)) return -1; // LOOK weird Firefox behviour?

    File file = DataRootHandler.getInstance().getCrawlableDatasetAsFile(path);
    if ((file != null) && file.exists())
      return file.lastModified();

    return -1;
  }

  /////////////////////////////////////////////////////////////////////////////


  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    log.debug("doGet(): User-Agent = " + request.getHeader("User-Agent"));

    String path = null;

    ReqState rs = getRequestState(request, response);

    try {
      path = request.getPathInfo();
      log.debug("doGet path={}", path);

      if (thredds.servlet.Debug.isSet("showRequestDetail"))
        log.debug(ServletUtil.showRequestDetail(this, request));

      if (path == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (baseURI == null) { // first time, set baseURI
        URI reqURI = ServletUtil.getRequestURI(request);
        // Build base URI from request (rather than hard-coding "/thredds/dodsC/").
        String baseUriString = request.getContextPath() + request.getServletPath() + "/";
        baseURI = reqURI.resolve(baseUriString);
        log.debug("doGet(): baseURI was set = {}", baseURI);
      }

      if (path.endsWith("latest.xml")) {   // LOOK: used ??
        DataRootHandler.getInstance().processReqForLatestDataset(this, request, response);
        return;
      }

      // Redirect all catalog requests at the root level.
      if (path.equals("/") || path.equals("/catalog.html") || path.equals("/catalog.xml")) {
        ServletUtil.sendPermanentRedirect(ServletUtil.getContextPath() + path, request, response);
        return;
      }

      // Make sure catalog requests match a dataRoot before trying to handle.  LOOK: used?
      if (path.endsWith("/") || path.endsWith("/catalog.html") || path.endsWith("/catalog.xml")) {
        if (!DataRootHandler.getInstance().hasDataRootMatch(path)) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        if (!DataRootHandler.getInstance().processReqForCatalog(request, response))
          log.error("catalog request failed ");

        return;
      }


      if (rs != null) {
        String dataSet = rs.getDataSet();
        String requestSuffix = rs.getRequestSuffix();

        if ((dataSet == null) || dataSet.equals("/") || dataSet.equals("")) {
          doGetDIR(rs);
        } else if (requestSuffix.equalsIgnoreCase("blob")) {
          doGetBLOB(rs);
        } else if (requestSuffix.equalsIgnoreCase("close")) {
          doClose(rs);
        } else if (requestSuffix.equalsIgnoreCase("dds")) {
          doGetDDS(rs);
        } else if (requestSuffix.equalsIgnoreCase("das")) {
          doGetDAS(rs);
        } else if (requestSuffix.equalsIgnoreCase("ddx")) {
          doGetDDX(rs);
        } else if (requestSuffix.equalsIgnoreCase("dods")) {
          doGetDAP2Data(rs);
        } else if (requestSuffix.equalsIgnoreCase("asc") || requestSuffix.equalsIgnoreCase("ascii")) {
          doGetASC(rs);
        } else if (requestSuffix.equalsIgnoreCase("info")) {
          doGetINFO(rs);
        } else if (requestSuffix.equalsIgnoreCase("html") || requestSuffix.equalsIgnoreCase("htm")) {
          doGetHTML(rs);
        } else if (requestSuffix.equalsIgnoreCase("ver") || requestSuffix.equalsIgnoreCase("version") ||
                dataSet.equalsIgnoreCase("/version") || dataSet.equalsIgnoreCase("/version/")) {
          doGetVER(rs);
        } else if (dataSet.equalsIgnoreCase("/help") || dataSet.equalsIgnoreCase("/help/") ||
                dataSet.equalsIgnoreCase("/" + requestSuffix) || requestSuffix.equalsIgnoreCase("help")) {
          doGetHELP(rs);
        } else {
          sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Unrecognized request");
          return;
        }

      } else {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Unrecognized request");
        return;
      }

      // plain ol' 404
    } catch (FileNotFoundException e) {
      //e.printStackTrace();
      sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());

      // DAP2Exception bad url
    } catch (BadURLException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      dap2ExceptionHandler(e, rs);

      // all other DAP2Exception
    } catch (DAP2Exception de) {
      int status = (de.getErrorCode() == DAP2Exception.NO_SUCH_FILE) ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_BAD_REQUEST;
      if ((de.getErrorCode() == DAP2Exception.UNKNOWN_ERROR) || ((de.getErrorCode() == DAP2Exception.UNDEFINED_ERROR))
              && (de.getErrorMessage() != null))
        log.info(de.getErrorMessage());
      response.setStatus(status);
      dap2ExceptionHandler(de, rs);

      // parsing, usually the CE
    } catch (ParseException pe) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      parseExceptionHandler(pe, response);

      // 403 - request too big
    } catch (UnsupportedOperationException e) {
      sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());

    } catch (java.net.SocketException e) {
      log.info("SocketException: " + e.getMessage(), e);

    } catch (IOException e) {
      String eName = e.getClass().getName(); // dont want compile time dependency on ClientAbortException
      if (eName.equals("org.apache.catalina.connector.ClientAbortException")) {
        log.debug("ClientAbortException: " + e.getMessage());
        return;
      }

      log.error("path= " + path, e);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());

      // everything else
    } catch (Throwable t) {
      log.error("path2= " + path, t);
      t.printStackTrace();     // LOOK, logger not showing stack trace
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.getMessage());
    }

  }

  public void doGetASC(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (ds == null) return;

      response.setHeader("XDODS-Server", getServerVersion());
      response.setContentType("text/plain");
      response.setHeader("Content-Description", "dods-ascii");

      log.debug("Sending OPeNDAP ASCII Data For: " + rs + "  CE: '" + rs.getConstraintExpression() + "'");

      ServerDDS dds = ds.getDDS();
      CEEvaluator ce = new CEEvaluator(dds);
      ce.parseConstraint(rs);
      checkSize(dds, true);

      PrintWriter pw = new PrintWriter(response.getOutputStream());
      dds.printConstrained(pw);
      pw.println("---------------------------------------------");

      AsciiWriter writer = new AsciiWriter(); // could be static
      writer.toASCII(pw, dds, ds);

      // the way that getDAP2Data works
      // DataOutputStream sink = new DataOutputStream(bOut);
      // ce.send(myDDS.getName(), sink, ds);

      pw.flush();

    } finally { // release lock if needed
      if (ds != null) ds.release();
    }

  }

  public void doGetDAS(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (ds == null) return;

      response.setContentType("text/plain");
      response.setHeader("XDODS-Server", getServerVersion());
      response.setHeader("Content-Description", "dods-das");

      OutputStream Out = new BufferedOutputStream(response.getOutputStream());

      DAS myDAS = ds.getDAS();
      myDAS.print(Out);

    } finally { // release lock if needed
      if (ds != null) ds.release();
    }

  }

  public void doGetDDS(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (null == ds) return;

      response.setContentType("text/plain");
      response.setHeader("XDODS-Server", getServerVersion());
      response.setHeader("Content-Description", "dods-dds");

      OutputStream out = new BufferedOutputStream(response.getOutputStream());
      ServerDDS myDDS = ds.getDDS();

      if (rs.getConstraintExpression().equals("")) { // No Constraint Expression?
        // Send the whole DDS
        myDDS.print(out);
        out.flush();

      } else { // Otherwise, send the constrained DDS
        // Instantiate the CEEvaluator and parse the constraint expression
        CEEvaluator ce = new CEEvaluator(myDDS);
        ce.parseConstraint(rs);

        // Send the constrained DDS back to the client
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
        myDDS.printConstrained(pw);
        pw.flush();
      }

    } finally { // release lock if needed
      if (ds != null) ds.release();
    }

  }

  public void doGetDDX(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (null == ds) return;

      response.setContentType("text/plain");
      response.setHeader("XDODS-Server", getServerVersion());
      response.setHeader("Content-Description", "dods-ddx");

      OutputStream out = new BufferedOutputStream(response.getOutputStream());

      ServerDDS myDDS = ds.getDDS();
      myDDS.ingestDAS(ds.getDAS());

      if (rs.getConstraintExpression().equals("")) { // No Constraint Expression?
        // Send the whole DDS
        myDDS.printXML(out);
        out.flush();
      } else { // Otherwise, send the constrained DDS

        // Instantiate the CEEvaluator and parse the constraint expression
        CEEvaluator ce = new CEEvaluator(myDDS);
        ce.parseConstraint(rs);

        // Send the constrained DDS back to the client
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
        myDDS.printConstrainedXML(pw);
        pw.flush();
      }

    } finally { // release lock if needed
      if (ds != null) ds.release();
    }
  }

  public void doGetBLOB(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (null == ds) return;

      response.setContentType("application/octet-stream");
      response.setHeader("XDODS-Server", getServerVersion());
      response.setHeader("Content-Description", "dods-blob");

      ServletOutputStream sOut = response.getOutputStream();
      OutputStream bOut;
      DeflaterOutputStream dOut = null;
      if (rs.getAcceptsCompressed() && allowDeflate) {
        response.setHeader("Content-Encoding", "deflate");
        dOut = new DeflaterOutputStream(sOut);
        bOut = new BufferedOutputStream(dOut);
      } else {
        bOut = new BufferedOutputStream(sOut);
      }

      ServerDDS myDDS = ds.getDDS();
      CEEvaluator ce = new CEEvaluator(myDDS);
      ce.parseConstraint(rs);
      checkSize(myDDS, false);

      // Send the binary data back to the client
      DataOutputStream sink = new DataOutputStream(bOut);
      ce.send(myDDS.getEncodedName(), sink, ds);
      sink.flush();

      // Finish up sending the compressed stuff, but don't
      // close the stream (who knows what the Servlet may expect!)
      if (null != dOut)
        dOut.finish();
      bOut.flush();

    } finally {  // release lock if needed
      if (ds != null) ds.release();
    }

  }

  private void doClose(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();
    HttpServletRequest request = rs.getRequest();
    String reqPath = rs.getDataSet();
    HttpSession session = request.getSession();
    session.removeAttribute(reqPath); // work done in the listener

    response.setHeader("XDODS-Server", getServerVersion()); // needed by client

    /* if (path.endsWith(".close")) {
      closeSession(request, response);
      response.setContentLength(0);
      return;
    }

    // so we need to worry about deleting sessions?
    session.invalidate();  */
  }

  public void doGetDAP2Data(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (null == ds) return;

      response.setContentType("application/octet-stream");
      response.setHeader("XDODS-Server", getServerVersion());
      response.setHeader("Content-Description", "dods-data");

      ServletOutputStream sOut = response.getOutputStream();
      OutputStream bOut;
      DeflaterOutputStream dOut = null;
      if (rs.getAcceptsCompressed() && allowDeflate) {
        response.setHeader("Content-Encoding", "deflate");
        dOut = new DeflaterOutputStream(sOut);
        bOut = new BufferedOutputStream(dOut);

      } else {
        bOut = new BufferedOutputStream(sOut);
      }

      ServerDDS myDDS = ds.getDDS();
      CEEvaluator ce = new CEEvaluator(myDDS);
      ce.parseConstraint(rs);
      checkSize(myDDS, false);

      // Send the constrained DDS back to the client
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(bOut));
      myDDS.printConstrained(pw);

      // Send the Data delimiter back to the client
      pw.flush();
      bOut.write("\nData:\n".getBytes());
      bOut.flush();

      // Send the binary data back to the client
      DataOutputStream sink = new DataOutputStream(bOut);
      ce.send(myDDS.getEncodedName(), sink, ds);
      sink.flush();

      // Finish up sending the compressed stuff, but don't
      // close the stream (who knows what the Servlet may expect!)
      if (null != dOut)
        dOut.finish();
      bOut.flush();

    } finally {  // release lock if needed
      if (ds != null) ds.release();
    }
  }

  public void doGetVER(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    response.setContentType("text/plain");
    response.setHeader("XDODS-Server", getServerVersion());
    response.setHeader("Content-Description", "dods-version");

    PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream()));

    pw.println("Server Version: " + getServerVersion());
    pw.flush();
  }

  public void doGetHELP(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();

    response.setContentType("text/html");
    response.setHeader("XDODS-Server", getServerVersion());
    response.setHeader("Content-Description", "dods-help");

    PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream()));
    printHelpPage(pw);
    pw.flush();
  }

  public void doGetDIR(ReqState rs) throws Exception {
    // rather dangerous here, since you can go into an infinite loop
    // so we're going to insist that there's  no suffix
    HttpServletResponse response = rs.getResponse();
    HttpServletRequest request = rs.getRequest();
    if ((rs.getRequestSuffix() == null) || (rs.getRequestSuffix().length() == 0)) {
      ServletUtil.forwardToCatalogServices(request, response);
      return;
    }

    sendErrorResponse(response, 0, "Unrecognized request");
  }

  public void doGetINFO(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();
    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (null == ds) return;

      PrintStream pw = new PrintStream(response.getOutputStream());
      response.setHeader("XDODS-Server", getServerVersion());
      response.setContentType("text/html");
      response.setHeader("Content-Description", "dods-description");

      GetInfoHandler di = new GetInfoHandler();
      di.sendINFO(pw, ds, rs);

    } finally {  // release lock if needed
      if (ds != null) ds.release();
    }
  }

  public void doGetHTML(ReqState rs) throws Exception {
    HttpServletResponse response = rs.getResponse();
    HttpServletRequest request = rs.getRequest();
    GuardedDataset ds = null;
    try {
      ds = getDataset(rs);
      if (ds == null) return;

      response.setHeader("XDODS-Server", getServerVersion());
      response.setContentType("text/html");
      response.setHeader("Content-Description", "dods-form");

      // Utilize the getDDS() method to get	a parsed and populated DDS
      // for this server.
      ServerDDS myDDS = ds.getDDS();
      DAS das = ds.getDAS();
      GetHTMLInterfaceHandler2 di = new GetHTMLInterfaceHandler2();
      di.sendDataRequestForm(request, response, rs.getDataSet(), myDDS, das);

    } finally {  // release lock if needed
      if (ds != null) ds.release();
    }

  }


  ///////////////////////////////////////////////////////////////////////////////////////////////
  // debugging
  private void makeDebugActions() {
    DebugController.Category debugHandler = DebugController.find("ncdodsServer");
    DebugController.Action act;

    act = new DebugController.Action("help", "Show help page") {
      public void doAction(DebugController.Event e) {
        try {
          doGetHELP(getRequestState(e.req, e.res));
        } catch (Exception ioe) {
          log.error("ShowHelp", ioe);
        }
      }
    };
    debugHandler.addAction(act);

    act = new DebugController.Action("version", "Show server version") {
      public void doAction(DebugController.Event e) {
        e.pw.println("  version= " + getServerVersion());
      }
    };
    debugHandler.addAction(act);

  }

  public String getServerName() {
    return this.getClass().getName();
  }


  /*protected ReqState getRequestState(HttpServletRequest request, HttpServletResponse response) {

    ReqState rs = null;
    // The url and query strings will come to us in encoded form
    // (see HTTPmethod.newMethod())
    String baseurl = request.getRequestURL().toString();
    baseurl = EscapeStrings.escapeURL(baseurl);
    log.debug("doGet baseurl={}", baseurl);

    String query = request.getQueryString();
    query = EscapeStrings.unescapeURLQuery(query);
    log.debug("doGet query={}", query);

    try {
      rs = new ReqState(request, response, getServletConfig(), getServerName(), baseurl, query);
    } catch (BadURLException bue) {
      rs = null;
    }

    return rs;
  }*/

  /**
   * ************************************************************************
   * Prints the OPeNDAP Server help page to the passed PrintWriter
   *
   * @param pw PrintWriter stream to which to dump the help page.
   */
  private void printHelpPage(PrintWriter pw) {

    pw.println("<h3>OPeNDAP Server Help</h3>");
    pw.println("To access most of the features of this OPeNDAP server, append");
    pw.println("one of the following a eight suffixes to a URL: .das, .dds, .dods, .ddx, .blob, .info,");
    pw.println(".ver or .help. Using these suffixes, you can ask this server for:");
    pw.println("<dl>");
    pw.println("<dt> das  </dt> <dd> Dataset Attribute Structure (DAS)</dd>");
    pw.println("<dt> dds  </dt> <dd> Dataset Descriptor Structure (DDS)</dd>");
    pw.println("<dt> dods </dt> <dd> DataDDS object (A constrained DDS populated with data)</dd>");
    pw.println("<dt> ddx  </dt> <dd> XML version of the DDS/DAS</dd>");
    pw.println("<dt> blob </dt> <dd> Serialized binary data content for requested data set, " +
            "with the constraint expression applied.</dd>");
    pw.println("<dt> info </dt> <dd> info object (attributes, types and other information)</dd>");
    pw.println("<dt> html </dt> <dd> html form for this dataset</dd>");
    pw.println("<dt> ver  </dt> <dd> return the version number of the server</dd>");
    pw.println("<dt> help </dt> <dd> help information (this text)</dd>");
    pw.println("</dl>");
    pw.println("For example, to request the DAS object from the FNOC1 dataset at URI/GSO (a");
    pw.println("test dataset) you would appand `.das' to the URL:");
    pw.println("http://opendap.gso.url.edu/cgi-bin/nph-nc/data/fnoc1.nc.das.");

    pw.println("<p><b>Note</b>: Many OPeNDAP clients supply these extensions for you so you don't");
    pw.println("need to append them (for example when using interfaces supplied by us or");
    pw.println("software re-linked with a OPeNDAP client-library). Generally, you only need to");
    pw.println("add these if you are typing a URL directly into a WWW browser.");
    pw.println("<p><b>Note</b>: If you would like version information for this server but");
    pw.println("don't know a specific data file or data set name, use `/version' for the");
    pw.println("filename. For example: http://opendap.gso.url.edu/cgi-bin/nph-nc/version will");
    pw.println("return the version number for the netCDF server used in the first example. ");

    pw.println("<p><b>Suggestion</b>: If you're typing this URL into a WWW browser and");
    pw.println("would like information about the dataset, use the `.info' extension.");

    pw.println("<p>If you'd like to see a data values, use the `.html' extension and submit a");
    pw.println("query using the customized form.");

  }
  //**************************************************************************


  /**
   * ************************************************************************
   * Prints the Bad URL Page page to the passed PrintWriter
   *
   * @param pw PrintWriter stream to which to dump the bad URL page.
   */
  private void printBadURLPage(PrintWriter pw) {

    String serverContactName = ThreddsConfig.get("serverInformation.contact.name", "UNKNOWN");
    String serverContactEmail = ThreddsConfig.get("serverInformation.contact.email", "UNKNOWN");
    pw.println("<h3>Error in URL</h3>");
    pw.println("The URL extension did not match any that are known by this");
    pw.println("server. Below is a list of the five extensions that are be recognized by");
    pw.println("all OPeNDAP servers. If you think that the server is broken (that the URL you");
    pw.println("submitted should have worked), then please contact the");
    pw.println("administrator of this server [" + serverContactName + "] at: ");
    pw.println("<a href='mailto:" + serverContactEmail + "'>" + serverContactEmail + "</a><p>");

  }

  ///////////////////////////////////////////////////////
  // utils

  protected ReqState getRequestState(HttpServletRequest request, HttpServletResponse response) {

    // Assume url was encoded
    String baseurl = request.getRequestURL().toString();
    baseurl = EscapeStrings.unescapeURL(baseurl);

    // Assume query  was encoded
    String query = request.getQueryString();
    query = EscapeStrings.unescapeURLQuery(query);

    log.debug(String.format("OpendapServlet: nominal url: %s?%s", baseurl, query));

    ReqState rs;
    try {
      rs = new ReqState(request, response, this, getServerName(), baseurl, query);
    } catch (Exception bue) {
      rs = null;
    }

    return rs;
  }

  private void
  checkSize(ServerDDS dds, boolean isAscii)
          throws Exception {
    long size = computeSize(dds, isAscii);
    //System.err.printf("total (constrained) size=%s\n", size);
    log.debug("total (constrained) size={}", size);
    double dsize = size / (1000 * 1000);
    double maxSize = isAscii ? ascLimit : binLimit; // Mbytes
    if (dsize > maxSize) {
      log.info("Reject request size = {} Mbytes", dsize);
      throw new UnsupportedOperationException("Request too big=" + dsize + " Mbytes, max=" + maxSize);
    }
  }

  // Recursively compute size of the dds to be returned
  // Note that the dds may be empty (e-support ZTH-269982)
  private long computeSize(DConstructor ctor, boolean isAscii) throws Exception {
    long projectsize = 0; // accumulate size of projected variables
    long othersize = 0;  // accumulate size of non-projected variables
    long fieldsize = 0;
    int projectedcount = 0;
    int fieldcount = 0;
    Enumeration vars = ctor.getVariables();
    while (vars.hasMoreElements()) {
      fieldcount++;
      BaseType field = (BaseType) vars.nextElement();
      fieldsize = computeFieldSize(field, isAscii);
      // accumulate the field sizes
      if (field.isProject()) {
        projectsize += fieldsize;
        projectedcount++;
      } else {
        othersize += fieldsize;
      }
    }
    // Cases to consider:
    // 1. If all of the fields of this ctor are projected,
    //    then return projectsize
    // 2. If none of the fields of this ctor are projected,
    //    then return othersize
    // 3. otherwise, at least one field, but not all, is projected,
    //    => return projectsize;
    if (projectedcount == fieldcount)
      return projectsize;
    else if (projectedcount == 0)
      return othersize;
    else {
      assert (projectedcount > 0 && projectedcount < fieldcount);
      return projectsize;
    }
  }

  long computeFieldSize(BaseType bt, boolean isAscii)
          throws Exception {
    long fieldsize = 0;
    // Figure out what this field is (e.g. primitive or not)
    // Somewhat convoluted.
    if (bt instanceof DConstructor) {
      // simple struct, seq, or grid => recurse
      fieldsize = computeSize((DConstructor) bt, isAscii);
    } else if (bt instanceof DArray) {
      SDArray da = (SDArray) bt;
      // Separate structure arrays from primitive arrays
      if (da.getContainerVar() instanceof DPrimitive) {
        fieldsize = computeArraySize(da);
      } else if (da.getContainerVar() instanceof DStructure) {
        fieldsize = computeSize((DStructure) da.getContainerVar(), isAscii); // recurse
      } else { // Some kind of problem
        throw new NoSuchTypeException("Computesize: unexpected type for " + bt.getLongName());
      }
    } else if (bt instanceof DPrimitive) {
      DPrimitive dp = (DPrimitive) bt;
      if (dp instanceof DString) {
        String v = ((DString) dp).getValue();
        fieldsize = (v == null ? 0 : v.length());
      } else {
        DataType dtype = DODSNetcdfFile.convertToNCType(bt);
        fieldsize = dtype.getSize();
      }
    } else { // Some kind of problem
      throw new NoSuchTypeException("Computesize: unknown type for " + bt.getLongName());
    }
    return fieldsize;
  }

  long
  computeArraySize(SDArray da)
          throws Exception {
    assert (da.getContainerVar() instanceof DPrimitive);
    BaseType base = da.getPrimitiveVector().getTemplate();
    DataType dtype = DODSNetcdfFile.convertToNCType(base);
    int elemSize = dtype.getSize();
    int n = da.numDimensions();
    List<Range> ranges = new ArrayList<>(n);
    long size = 0;
    for (int i = 0; i < n; i++) {
      ranges.add(new Range(da.getStart(i), da.getStop(i), da.getStride(i)));
      Section s = new Section(ranges);
      size += s.computeSize() * elemSize;
    }
    return size;
  }

  /*
   * *********************** dataset caching ***********************************************
   */

  // any time the server needs access to the dataset, it gets a "GuardedDataset" which allows us to add caching
  // optionally, a session may be established, which allows us to reserve the dataset for that session.
  protected GuardedDataset getDataset(ReqState preq) throws Exception {
    HttpServletRequest req = preq.getRequest();
    String reqPath = preq.getDataSet();

    // see if the client wants sessions
    boolean acceptSession = false;
    String s = req.getHeader("X-Accept-Session");
    if (s != null && s.equalsIgnoreCase("true") && allowSessions)
      acceptSession = true;

    HttpSession session = null;
    if (acceptSession) {
      // see if theres already a session established, create one if not
      session = req.getSession();
      if (!session.isNew()) {
        GuardedDataset gdataset = (GuardedDataset) session.getAttribute(reqPath);
        if (null != gdataset) {
          if (debugSession) System.out.printf(" found gdataset %s in session %s %n", reqPath, session.getId());
          if (log.isDebugEnabled()) log.debug(" found gdataset " + gdataset + " in session " + session.getId());
          return gdataset;
        }
      }
    }

    /* test debug
    for (int i = 0; i < 2; i++) {
      NetcdfFile ncfile = DatasetHandler.getNetcdfFile(req, preq.getResponse(), reqPath);
      if (null == ncfile) return null;
      NetcdfDataset ncd = new NetcdfDataset(ncfile);

      Formatter out = new Formatter();
      CompareNetcdf2.compareFiles(ncfile, ncd, out, false, false, false);
      System.out.printf("%s%n", out);

      FeatureDataset featureDataset = ucar.nc2.ft.FeatureDatasetFactoryManager.wrap(ucar.nc2.constants.FeatureType.STATION, ncd, null, new Formatter(System.err));
      System.out.printf("%s%n", featureDataset);
      ncd.close();
    } */

    NetcdfFile ncd = DatasetHandler.getNetcdfFile(req, preq.getResponse(), reqPath);
    if (null == ncd) return null;

    GuardedDataset gdataset = new GuardedDatasetCacheAndClone(reqPath, ncd, acceptSession);
    //GuardedDataset gdataset = new GuardedDatasetImpl(reqPath, ncd, acceptSession);

    if (acceptSession) {
      String cookiePath = req.getRequestURI();
      String suffix = "." + preq.getRequestSuffix();
      if (cookiePath.endsWith(suffix)) // snip off the suffix
        cookiePath = cookiePath.substring(0, cookiePath.length() - suffix.length());
      session.setAttribute(reqPath, gdataset);
      session.setAttribute(CookieFilter.SESSION_PATH, cookiePath);
      //session.setAttribute("dataset", ncd.getLocation());  // for UsageValve
      // session.setMaxInactiveInterval(30); // 30 second timeout !!
      if (debugSession)
        System.out.printf(" added gdataset %s in session %s cookiePath %s %n", reqPath, session.getId(), cookiePath);
      if (log.isDebugEnabled()) log.debug(" added gdataset " + gdataset + " in session " + session.getId());
    } /* else {
      session = req.getSession();
      session.setAttribute("dataset", ncd.getLocation()); // for UsageValve
    } */

    return gdataset;
  }

  //////////////////////////////////////////////////////////////////////////////

  public void parseExceptionHandler(ParseException pe, HttpServletResponse response) {
    try {
      BufferedOutputStream eOut = new BufferedOutputStream(response.getOutputStream());
      response.setHeader("Content-Description", "dods-error");
      response.setContentType("text/plain");

      String msg = pe.getMessage().replace('\"', '\'');

      DAP2Exception de2 = new DAP2Exception(opendap.dap.DAP2Exception.CANNOT_READ_FILE, msg);
      de2.print(eOut);
    } catch (Exception e) {
      System.err.println("parseExceptionHandler: " + e);
    }
  }

  public void dap2ExceptionHandler(DAP2Exception de, ReqState rs) {
    rs.getResponse().setHeader("Content-Description", "dods-error");
    rs.getResponse().setContentType("text/plain");
    try {
      de.print(rs.getResponse().getOutputStream());
    } catch (Exception e) {
      System.err.println("dap2ExceptionHandler: " + e);
    }
  }

  private void sendErrorResponse(HttpServletResponse response, int errorCode, String errorMessage) {
    try {
      response.setStatus(errorCode);
      response.setHeader("Content-Description", "dods-error");
      response.setContentType("text/plain");

      PrintWriter pw = new PrintWriter(response.getOutputStream());
      pw.println("Error {");
      pw.println("    code = " + errorCode + ";");
      pw.println("    message = \"" + errorMessage + "\";");

      pw.println("};");
      pw.flush();
    } catch (Exception e) {
      System.err.println("sendErrorResponse: " + e);
    }
  }

}

/*

netcdf CherylMorse/buoy_agg {
  dimensions:
    name_strlen = 5;
    time = 288;
  variables:
    char station_name(name_strlen=5);
      :long_name = "wqbaw";
      :cf_role = "timeseries_id";

    int qc_flag;
      :long_name = "Quality control status";
      :short_name = "qc_flag";
      :valid_range = 0, 2; // int
      :flag_values = 0, 1, 2; // int
      :flag_meanings = "no_qc_applied realtime_qc_applied delayed_mode_qc_applied";
      :units = "0";

    float temp_raw(time=288);
      :long_name = "Temperature (raw)";
      :standard_name = "sea_water_temperature";
      :short_name = "temp_raw";
      :units = "Celsius";
      :coordinates = "time lat lon alt";
      :valid_range = 10.0, 35.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "measured";

    int temp_qd(time=288);
      :long_name = "Temperature quality descriptor";
      :short_name = "temp_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float temp(time=288);
      :long_name = "Temperature (processed)";
      :standard_name = "sea_water_temperature";
      :short_name = "temp";
      :units = "Celsius";
      :coordinates = "time lat lon alt";
      :valid_range = 10.0, 35.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "measured";

    float cond_raw(time=288);
      :long_name = "Conductivity (raw)";
      :standard_name = "sea_water_electrical_conductivity";
      :short_name = "cond_raw";
      :units = "S m-1";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "measured";

    int cond_qd(time=288);
      :long_name = "Conductivity quality descriptor";
      :short_name = "cond_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float cond(time=288);
      :long_name = "Conductivity (processed)";
      :standard_name = "sea_water_electrical_conductivity";
      :short_name = "cond";
      :units = "S m-1";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "measured";

    float salt_raw(time=288);
      :long_name = "Salinity (raw)";
      :standard_name = "sea_water_salinity";
      :short_name = "salt_raw";
      :units = "1e-3";
      :coordinates = "time lat lon alt";
      :valid_range = 10.0, 40.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "salinity is calculated from measured temp and condc";

    int salt_qd(time=288);
      :long_name = "Salinity quality descriptor";
      :short_name = "salt_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float salt(time=288);
      :long_name = "Salinity (processed)";
      :standard_name = "sea_water_salinity";
      :short_name = "salt";
      :units = "1e-3";
      :coordinates = "time lat lon alt";
      :valid_range = 10.0, 40.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "salinity is calculated from measured temp and condc";

    float oxyg_raw(time=288);
      :long_name = "Dissolved oxygen (raw)";
      :standard_name = "mass_concentration_of_oxygen_in_sea_water";
      :short_name = "doxy_raw";
      :units = "kg m-3";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "oxygen is calculated from measured T, S, P, V (DO thermistor voltage) and U (DO phase delay); see global attrib calib_oxyg; and from mg/L to kg/m3 by oxy*1.4276/1000.0";

    int oxyg_qd(time=288);
      :long_name = "Oxygen quality descriptor";
      :short_name = "doxy_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float oxyg(time=288);
      :long_name = "Dissolved oxygen (processed)";
      :standard_name = "mass_concentration_of_oxygen_in_sea_water";
      :short_name = "doxy";
      :units = "kg m-3";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "oxygen is calculated from measured T, S, P, V (DO thermistor voltage) and U (DO phase delay); see global attrib calib_oxyg; and from mg/L to kg/m3 by oxy*1.4276/1000.0";

    float flor_raw(time=288);
      :long_name = "Chlorophyll (raw)";
      :standard_name = "mass_concentration_of_chlorophyll_in_sea_water";
      :short_name = "flor_raw";
      :units = "kg m-3";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "flor is calculated from voltage using scale factor (FSF) and dark count (FDC); see global attrib calib_flnt; and from ug/L to kg m-3 by flor*1E-6";

    int flor_qd(time=288);
      :long_name = "Chlorophyll quality descriptor";
      :short_name = "flor_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float flor(time=288);
      :long_name = "Chlorophyll (processed)";
      :standard_name = "mass_concentration_of_chlorophyll_in_sea_water";
      :short_name = "flor";
      :units = "kg m-3";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "flor is calculated from voltage using scale factor (FSF) and dark count (FDC); see global attrib calib_flnt; and from ug/L to kg m-3 by flor*1E-6";

    float turb_raw(time=288);
      :long_name = "Turbidity (raw)";
      :standard_name = "sea_water_turbidity";
      :short_name = "turb_raw";
      :units = "ntu";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "turb is calculated from voltage using scale factor (TSF) and dark count (TDC); see global attrib calib_flnt";

    int turb_qd(time=288);
      :long_name = "Turbidity quality descriptor";
      :short_name = "turb_qd";
      :valid_range = -9, 4; // int
      :flag_values = -9, 0, 1, 2, 3, 4; // int
      :flag_meanings = "missing_value quality_not_evaluated failed/bad questionable/suspect passed/good interpolated/adjusted";
      :units = "0";

    float turb(time=288);
      :long_name = "Turbidity (processed)";
      :standard_name = "sea_water_turbidity";
      :short_name = "turb";
      :units = "ntu";
      :coordinates = "time lat lon alt";
      :valid_range = 0.0, 10.0; // double
      :_FillValue = -999.0f; // float
      :observation_type = "calculated";
      :comment = "turb is calculated from voltage using scale factor (TSF) and dark count (TDC); see global attrib calib_flnt";

    float alt;
      :long_name = "depth below mean sea level";
      :standard_name = "depth";
      :short_name = "depth";
      :axis = "z";
      :units = "meters";
      :_CoordinateAxisType = "Height";

    float lat;
      :long_name = "Latitude";
      :standard_name = "latitude";
      :short_name = "lat";
      :axis = "Y";
      :units = "degrees_north";
      :_CoordinateAxisType = "Lat";

    float lon;
      :long_name = "Longitude";
      :standard_name = "longitude";
      :short_name = "lon";
      :axis = "X";
      :units = "degrees_east";
      :_CoordinateAxisType = "Lon";

    float time(time=288);
      :long_name = "Time";
      :standard_name = "time";
      :short_name = "time";
      :axis = "T";
      :units = "minutes since 2008-01-01 00:00:00 -10:00";
      :_CoordinateAxisType = "Time";

  // global attributes:
  :project = "PacIOOS";
  :Conventions = "CF-1.6";
  :featureType = "timeSeries";
  :cdm_data_type = "Station";
  :title = "Water Quality Buoy: Ala Wai";
  :insitution = "UH/SOEST";
  :date_created = "2013-05-08Z00:18:01 ";
  :abstract = "The water quality buoys are part of the Pacific Islands Ocean Observing System (PacIOOS) and are designed to measure a variety of ocean parameters at fixed points.  WQBAW is located at the mouth of the Ala Wai Canal near Magic Island.  The Ala Wai is a main conduit for runoff from the area in-shore Waikiki.  Continuous sampling of this area provides a record of baseline conditions of the chemical and biological environment for comparison when there are pollultion events such as storm runoff or a sewage spill.";
  :calib_oxyg = "SBE63-0317, Calib 30-Nov-12: A0=1.0513E0, A1-1.50E-3, A2=4.0970E-1, B0=-2.-2.3697E-3, B1=1.6307E0, TA0=7.5623 24E-4, TA1=2.379507E-4, TA2=1.872606E-6, TA3=6.04944E-8 (effective date 01-31-2013)    ";
  :calib_flnt = "FLNTUS-1027, Calib 25-Oct-12: FSF=16, FDC=0.78, TSF=39, TDC=0.058 (effective date 01-31-2013)                     ";
  :keywords = "Turbidity, Chlorophyll, Oxygen, Fluorescence, Scattering, Water Temperature, Conductivity, Salinity";
  :references = "http://pacioos.org";
  :platform_code = "WQBAW";
  :naming_authority = "PacIOOS";
  :geospatial_lat_min = "21.2799 ";
  :geospatial_lat_max = "21.2799 ";
  :geospatial_lon_min = "-157.848";
  :geospatial_lon_max = "-157.848";
  :geospatial_vertical_min = "-1.0";
  :geospatial_vertical_max = "-1.0";
  :time_coverage_start = "2013-05-03T00:01:32-10:00";
  :time_coverage_end = "2013-05-03T23:21:32-10:00";
  :local_time_zone = "-10.";
  :data_center = "PacIOOS";
  :data_center_email = "jimp@hawaii.edu";
  :author_email = "jimp@hawaii.edu";
  :author = "Dr. J. T. Potemra";
  :principal_investigator = "Prof. Eric H. De Carlo";
  :principal_investigator_email = "edecarlo@hawaii.edu";
  :technical_contact = "Mike Tomlonson";
  :technical_contact_email = "tomlinson86@q.com";
  :citation = "Citation to be used in publications should follow the form: \"PacIOOS.[year-of-data-download],[Title],[Data access URL], accessed [date-of-access].\"";
  :acknowledgement = "Data provided by PacIOOS (http://pacioos.org) which is part of the U.S. Integrated Ocean Observing System (IOOS) and are funded in part by the National Oceanic and Atmospheric Administration (NOAA) award #NA11NOS0120039.";
  :distribution_statement = "PacIOOS data may be re-used, provided that related metadata explaning the data have been reviewed by the user, and that the data are appropriately acknowledged.  Data, products and services from PacIOOS are provided \"as is\" without and warranty as to fitness for a particular purpose.";
  :_CoordSysBuilder = "ucar.nc2.dataset.conv.CF1Convention";
}

*/