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

package ucar.nc2.grib.grib2.table;

import net.jcip.annotations.Immutable;
import thredds.featurecollection.TimeUnitConverter;
import ucar.nc2.grib.*;
import ucar.nc2.grib.grib2.*;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarPeriod;
import ucar.nc2.wmo.CommonCodeTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * Grib 2 Tables - allows local overrides and augmentation
 * This class implements the standard WMO tables, local tables are subclasses
 *
 * @author caron
 * @since 4/3/11
 */
@Immutable
public class Grib2Customizer implements ucar.nc2.grib.GribTables, TimeUnitConverter {
  static private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Grib2Pds.class);
  static private Grib2Customizer wmoTables, ncepTables, ndfdTables, kmaTables, fslTables, fslTables2;  // major lame

  static public Grib2Customizer factory(Grib2Record gr) throws IOException {
    Grib2SectionIdentification ids = gr.getId();
    Grib2SectionProductDefinition pdss = gr.getPDSsection();
    Grib2Pds pds = pdss.getPDS();
    return factory(ids.getCenter_id(), ids.getSubcenter_id(), ids.getMaster_table_version(), ids.getLocal_table_version(), pds.getGenProcessId());
  }

  static public Grib2Customizer factory(int center, int subCenter, int masterVersion, int localVersion, int genProcessId) {
    /* if ((center == 7) && (masterVersion == 2)&& (localVersion == 1)) { // FAKE
      if (dssTables == null ) dssTables = new DssLocalTables(center, subCenter, masterVersion, localVersion);
      return dssTables;
    } else */

    if ((center == 7) || (center == 9) || (center == 54)) { // canadian met
        if (ncepTables == null ) ncepTables = new NcepLocalTables(center, subCenter, masterVersion, localVersion, genProcessId);
        return ncepTables;

    } else if (center == 59 && localVersion == 0) { // FSL-2
        return FslLocalTables.localFactory(subCenter, masterVersion, localVersion, genProcessId);

    } else if (center == 59) { // FSL
        if (fslTables == null ) fslTables = FslLocalTables.localFactory(subCenter, masterVersion, localVersion, genProcessId);
        return fslTables;

    } else if ((center == 8) && ((subCenter == 0) || (subCenter == -9999))){ // NDFD
      if (ndfdTables == null ) ndfdTables = new NdfdLocalTables(center, subCenter, masterVersion, localVersion);
      return ndfdTables;

    } else if (center == 40) {  // KMA
      if (kmaTables == null ) kmaTables = new KmaLocalTables(center, subCenter, masterVersion, localVersion);
      return kmaTables;

    } else {
      if (wmoTables == null ) wmoTables = new Grib2Customizer(center, subCenter, masterVersion, localVersion);
      return wmoTables;
    }
  }

  static public class GribTableId {
    public final String name;
    public final int center, subCenter, masterVersion, localVersion, genProcessId;

    GribTableId(String name, int center, int subCenter, int masterVersion, int localVersion, int genProcessId) {
      this.name = name;
      this.center = center;
      this.subCenter = subCenter;
      this.masterVersion = masterVersion;
      this.localVersion = localVersion;
      this.genProcessId = genProcessId;
    }
  }

  // debugging
  static public List<GribTableId> getTableIds() {
    List<GribTableId> result = new ArrayList<>();
    result.add(new GribTableId("WMO",0,-1,-1,-1,-1));
    result.add(new GribTableId("NCEP",7,-1,-1,-1,-1));
    result.add(new GribTableId("NDFD",8,0,-1,-1,-1));
    result.add(new GribTableId("KMA",40,-1,-1,-1,-1));
    // result.add(new GribTableId("DSS",7,-1,2,1)); // ??
    // result.add(new GribTableId("FSL2",59,-1,-1,0)); // fake
    result.add(new GribTableId("GSD_HRRR",59,0,-1,-1, 125));
    result.add(new GribTableId("GSD_FIM",59, 1,-1,-1, 116));
    return result;
  }

  static public int makeHash(int discipline, int category, int number) {
    return (discipline << 16) + (category << 8) + number;
  }

  static public boolean isLocal(Parameter p) {
    return ((p.getCategory() > 191) || (p.getNumber() > 191));
  }

  ///////////////////////////////////////////////////////////////
  protected final int center, subCenter, masterVersion, localVersion;

  protected Grib2Customizer(int center, int subCenter, int masterVersion, int localVersion) {
    this.center = center;
    this.subCenter = subCenter;
    this.masterVersion = masterVersion;
    this.localVersion = localVersion;
  }

  public String getVariableName(Grib2Record gr) {
    return getVariableName(gr.getDiscipline(), gr.getPDS().getParameterCategory(), gr.getPDS().getParameterNumber());
  }

  public String getVariableName(int discipline, int category, int parameter) {
    String s = WmoCodeTable.getParameterName(discipline, category, parameter);
    if (s == null)
      s = "U" + discipline + "-" + category + "-" + parameter;
    return s;
  }

  public String getTableValue(String tableName, int code) {
    return WmoCodeTable.getTableValue(tableName, code);
  }

  public GribTables.Parameter getParameter(int discipline, int category, int number) {
    return WmoCodeTable.getParameterEntry(discipline, category, number);
  }

  /////////////////////////////////////////////////////
  // debugging
  public GribTables.Parameter getParameterRaw(int discipline, int category, int number) {
    return WmoCodeTable.getParameterEntry(discipline, category, number);
  }

  // debugging
  public String getTablePath(int discipline, int category, int number) {
    return WmoCodeTable.standard.getResourceName();
  }

  // debugging
  public List<GribTables.Parameter> getParameters() {
    List<GribTables.Parameter> allParams = new ArrayList<>(3000);
    try {
      WmoCodeTable.WmoTables wmo = WmoCodeTable.getWmoStandard();
      for (String key : wmo.map.keySet()) {
        if (key.startsWith("4.2.")) {
          WmoCodeTable params = wmo.map.get(key);
          allParams.addAll(params.entries);
        }
      }
    } catch (IOException e) {
      System.out.printf("Error reading wmo tables = %s%n", e.getMessage());
    }
    return allParams;
  }

  // debugging
  public void lookForProblems(Formatter f) {
  }

  ////////////////////////////////////////////////////////////////////////////////////////////

  public CalendarDate getForecastDate(Grib2Record gr) {
    Grib2Pds pds = gr.getPDS();
    if (pds.isTimeInterval()) {
      TimeCoord.TinvDate intv = getForecastTimeInterval(gr);
      return intv == null ? null : intv.getEnd();

    } else {
      int val = pds.getForecastTime();
      CalendarPeriod period = Grib2Utils.getCalendarPeriod( convertTimeUnit(pds.getTimeUnit()));
      if (period == null) return null;
      return gr.getReferenceDate().add( period.multiply(val));
    }
  }

  /*
  Code Table Code table 4.11 - Type of time intervals (4.11)
    0: Reserved
    1: Successive times processed have same forecast time, start time of forecast is incremented
    2: Successive times processed have same start time of forecast, forecast time is incremented
    3: Successive times processed have start time of forecast incremented and forecast time decremented so that valid time remains constant
    4: Successive times processed have start time of forecast decremented and forecast time incremented so that valid time remains constant
    5: Floating subinterval of time between forecast time and end of overall time interval

  static public class TimeInterval {
    public int statProcessType; // (code table 4.10) Statistical process used to calculate the processed field from the field at each time increment during the time range
    public int timeIncrementType;  // (code table 4.11) Type of time increment between successive fields used in the statistical processing
    public int timeRangeUnit;  // (code table 4.4) Indicator of unit of time for time range over which statistical processing is done
    public int timeRangeLength; // Length of the time range over which statistical processing is done, in units defined by the previous octet
    public int timeIncrementUnit; // (code table 4.4) Indicator of unit of time for the increment between the successive fields used
    public int timeIncrement; // Time increment between successive fields, in units defined by the previous octet

    from NDFD site:
    timeRangeUnit:    8-14 Day Outlooks = 2 (days); Monthly and Seasonal Outlooks = 3 (months)
    timeRangeLength:  8-14 Day Outlooks = 6 (days); Monthly Outlooks = 1 (month); Seasonal Outlooks = 3 (months)
   */

  /**
   * Get the time interval in units of gr.getPDS().getTimeUnit()
   * @param gr Grib record
   * @return time interval in units of gr.getPDS().getTimeUnit()
   */
  public TimeCoord.TinvDate getForecastTimeInterval(Grib2Record gr) {
    // note  from Arthur Taylor (degrib):
    /* If there was a range I used:

    End of interval (EI) = (bytes 36-42 show an "end of overall time interval")
    C1) End of Interval = EI;
    Begin of Interval = EI - range

    and if there was no interval then I used:
    C2) End of Interval = Begin of Interval = Ref + ForeT.
    */
    if (!gr.getPDS().isTimeInterval()) return null;
    Grib2Pds.PdsInterval pdsIntv = (Grib2Pds.PdsInterval) gr.getPDS();
    int timeUnitOrg = gr.getPDS().getTimeUnit();

    // calculate total "range"
    int range = 0;
    for (Grib2Pds.TimeInterval ti : pdsIntv.getTimeIntervals()) {
      if ((ti.timeRangeUnit != timeUnitOrg) || (ti.timeIncrementUnit != timeUnitOrg && ti.timeIncrementUnit != 255 && ti.timeIncrement != 0)) {
        log.warn("TimeInterval has different units timeUnit= " + timeUnitOrg + " TimeInterval=" + ti);
      }

      range += ti.timeRangeLength;
      if (ti.timeIncrementUnit != 255) range += ti.timeIncrement;
    }

    CalendarPeriod unitPeriod = Grib2Utils.getCalendarPeriod(convertTimeUnit(timeUnitOrg));
    if (unitPeriod == null) return null;
    CalendarPeriod period = unitPeriod.multiply(range);

    // End of Interval as date
    CalendarDate EI = pdsIntv.getIntervalTimeEnd();
    if (EI == null) {  // all values were set to zero   LOOK guessing!
      return new TimeCoord.TinvDate(gr.getReferenceDate(), period);
    } else {
      return new TimeCoord.TinvDate(period, EI);
    }
  }

  /**
   * Get interval size in units of hours
   * @param pds must be a Grib2Pds.PdsInterval
   * @return  interval size in units of hours
   */
  public double getForecastTimeIntervalSizeInHours(Grib2Pds pds) {
    Grib2Pds.PdsInterval pdsIntv = (Grib2Pds.PdsInterval) pds;
    int timeUnitOrg = pds.getTimeUnit();

    // calculate total "range" in units of timeUnit
    int range = 0;
    for (Grib2Pds.TimeInterval ti : pdsIntv.getTimeIntervals()) {
      if ((ti.timeRangeUnit != timeUnitOrg) || (ti.timeIncrementUnit != timeUnitOrg && ti.timeIncrementUnit != 255 && ti.timeIncrement != 0)) {
        log.warn("TimeInterval has different units timeUnit= " + timeUnitOrg + " TimeInterval=" + ti);
      }

      range += ti.timeRangeLength;
      if (ti.timeIncrementUnit != 255) range += ti.timeIncrement;
    }

    // now convert that range to units of the requested period.
    CalendarPeriod timeUnitPeriod = Grib2Utils.getCalendarPeriod(convertTimeUnit(timeUnitOrg));
    if (timeUnitPeriod == null) return GribNumbers.UNDEFINEDD;
    if (timeUnitPeriod.equals(CalendarPeriod.Hour)) return range;

    double fac;
    if (timeUnitPeriod.getField() == CalendarPeriod.Field.Month) {
       fac = 30.0 * 24.0;  // nominal hours in a month
    } else if (timeUnitPeriod.getField() == CalendarPeriod.Field.Year) {
       fac = 365.0 * 24.0; // nominal hours in a year
    } else {
      fac = CalendarPeriod.Hour.getConvertFactor(timeUnitPeriod);
    }
    return fac * range;
  }

  /**
   * If this has a time interval coordinate, get time interval
   * @param gr from this record
   * @return time interval in units of pds.getTimeUnit(), or null if not a time interval
   */
  public int[] getForecastTimeIntervalOffset(Grib2Record gr) {
    TimeCoord.TinvDate tinvd = getForecastTimeInterval(gr);
    if (tinvd == null) return null;

    Grib2Pds pds = gr.getPDS();
    int unit = convertTimeUnit(pds.getTimeUnit());
    TimeCoord.Tinv tinv = tinvd.convertReferenceDate(gr.getReferenceDate(), Grib2Utils.getCalendarPeriod(unit));
    int[] result = new int[2];
    result[0] = tinv.getBounds1();
    result[1] = tinv.getBounds2();
    return result;
  }

  public String getLevelName(int id) {
    return getTableValue("4.5", id);
  }

  // Table 4.5
  @Override
  public String getLevelNameShort(int id) {

    switch (id) {
      case 1:
        return "surface";
      case 2:
        return "cloud_base";
      case 3:
        return "cloud_tops";
      case 4:
        return "zeroDegC_isotherm";
      case 5:
        return "adiabatic_condensation_lifted";
      case 6:
        return "maximum_wind";
      case 7:
        return "tropopause";
      case 8:
        return "atmosphere_top";
      case 9:
        return "sea_bottom";
      case 10:
        return "entire_atmosphere";
      case 11:
        return "cumulonimbus_base";
      case 12:
        return "cumulonimbus_top";
      case 20:
        return "isotherm";
      case 100:
        return "isobaric";
      case 101:
        return "msl";
      case 102:
        return "altitude_above_msl";
      case 103:
        return "height_above_ground";
      case 104:
        return "sigma";
      case 105:
        return "hybrid";
      case 106:
        return "depth_below_surface";
      case 107:
        return "isentrope";
      case 108:
        return "pressure_difference";
      case 109:
        return "potential_vorticity_surface";
      case 111:
        return "eta";
      case 113:
        return "log_hybrid";
      case 117:
        return "mixed_layer_depth";
      case 118:
        return "hybrid_height";
      case 119:
        return "hybrid_pressure";
      case 120:
        return "pressure_thickness";
      case 160:
        return "depth_below_sea";
      case GribNumbers.UNDEFINED:
        return "none";
      default:
        return "UnknownLevelType-" + id;
    }
  }

  /*
Code Table Code table 4.7 - Derived forecast (4.7)
    0: Unweighted mean of all members
    1: Weighted mean of all members
    2: Standard deviation with respect to cluster mean
    3: Standard deviation with respect to cluster mean, normalized
    4: Spread of all members
    5: Large anomaly index of all members
    6: Unweighted mean of the cluster members
    7: Interquartile range (range between the 25th and 75th quantile)
    8: Minimum of all ensemble members
    9: Maximum of all ensemble members
   -1: Reserved
   -1: Reserved for local use
  255: Missing

  */
  public String getProbabilityNameShort(int id) {
    switch (id) {
      case 0:
        return "unweightedMean";
      case 1:
        return "weightedMean";
      case 2:
        return "stdDev";
      case 3:
        return "stdDevNormalized";
      case 4:
        return "spread";
      case 5:
        return "largeAnomalyIndex";
      case 6:
        return "unweightedMeanCluster";
      case 7:
        return "interquartileRange";
      case 8:
        return "minimumEnsemble";
      case 9:
        return "maximumEnsemble";
      default:
        return "UnknownProbType" + id;
     }
  }

  public String getStatisticName(int id) {
    return getTableValue("4.10", id); // WMO
  }

  public String getStatisticNameShort(int id) {
    GribStatType stat = GribStatType.getStatTypeFromGrib2(id);
    return (stat == null) ?"UnknownStatType-" + id : stat.toString();
  }

  @Override
  public GribStatType getStatType(int grib2StatCode) {
    return GribStatType.getStatTypeFromGrib2(grib2StatCode);
  }

  @Override
  public VertCoord.VertUnit getVertUnit(int code) {
    return Grib2Utils.getLevelUnit(code);
  }

  @Override
  public int addVariableHash(Object gribRecord) {
    return 0;
  }

  /////////////////////////////////////////////
  private TimeUnitConverter timeUnitConverter;  // LOOK not really immutable

  public void setTimeUnitConverter(TimeUnitConverter timeUnitConverter) {
    this.timeUnitConverter = timeUnitConverter;
  }

  @Override
  public int convertTimeUnit(int timeUnit) {
    if (timeUnitConverter == null) return timeUnit;
    return timeUnitConverter.convertTimeUnit(timeUnit);
  }

  //////////////

  @Override
  public String getSubCenterName(int center_id, int subcenter_id) {
    return CommonCodeTable.getSubCenterName(center_id, subcenter_id);
  }

  public String getGeneratingProcessName(int genProcess) {
    return null;
  }

  public String getGeneratingProcessTypeName(int genProcess) {
    return getTableValue("4.3", genProcess);
  }

  public String getCategory(int discipline, int category) {
    return getTableValue("4.1." + discipline, category);
  }

  public static void main(String[] args) {
    Grib2Customizer cust = factory(0, 0, 0, 0, 0);
    String c = cust.getCategory(0, 14);
    System.out.printf("%s%n", c);
  }
}
