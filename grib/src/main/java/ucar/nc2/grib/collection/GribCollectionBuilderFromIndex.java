/*
 *
 *  * Copyright 1998-2014 University Corporation for Atmospheric Research/Unidata
 *  *
 *  *  Portions of this software were developed by the Unidata Program at the
 *  *  University Corporation for Atmospheric Research.
 *  *
 *  *  Access and use of this software shall impose the following obligations
 *  *  and understandings on the user. The user is granted the right, without
 *  *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  *  this software, and any derivative works thereof, and its supporting
 *  *  documentation for any purpose whatsoever, provided that this entire
 *  *  notice appears in all copies of the software, derivative works and
 *  *  supporting documentation.  Further, UCAR requests that the user credit
 *  *  UCAR/Unidata in any publications that result from the use of this
 *  *  software or in any product that includes this software. The names UCAR
 *  *  and/or Unidata, however, may not be used in any advertising or publicity
 *  *  to endorse or promote any products or commercial entity unless specific
 *  *  written permission is obtained from UCAR/Unidata. The user also
 *  *  understands that UCAR/Unidata is not obligated to provide the user with
 *  *  any support, consulting, training or assistance of any kind with regard
 *  *  to the use, operation and performance of this software nor to provide
 *  *  the user with any updates, revisions, new versions or "bug fixes."
 *  *
 *  *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 */

package ucar.nc2.grib.collection;

import com.google.protobuf.ExtensionRegistry;
import thredds.inventory.MFile;
import ucar.coord.*;
import ucar.nc2.grib.*;
import ucar.nc2.stream.NcStream;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateUnit;
import ucar.nc2.time.CalendarPeriod;
import ucar.unidata.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Superclass to read GribCollection From ncx2 Index file
 *
 * @author caron
 * @since 2/20/14
 */
public abstract class GribCollectionBuilderFromIndex {

  protected final boolean dataOnly; // dont need the extra metadata like twot
  protected GribCollection gc;
  protected final org.slf4j.Logger logger;
  protected boolean debug = false;
  protected GribTables tables;

  protected abstract void readGds(GribCollectionProto.Gds p);
  protected abstract GribTables makeCustomizer() throws IOException;
  protected abstract String getLevelNameShort(int levelCode);

  protected GribCollectionBuilderFromIndex(GribCollection gc, boolean dataOnly, org.slf4j.Logger logger) {
    this.logger = logger;
    this.dataOnly = dataOnly;
    this.gc = gc;
  }

  protected abstract String getMagicStart();

  protected boolean readIndex(RandomAccessFile raf) throws IOException {

    gc.setIndexRaf(raf); // LOOK leaving the raf open in the GribCollection
    try {
      raf.order(RandomAccessFile.BIG_ENDIAN);
      raf.seek(0);

      //// header message
      if (!NcStream.readAndTest(raf, getMagicStart().getBytes())) {
        raf.seek(0);
        NcStream.readAndTest(raf, getMagicStart().getBytes()); // debug
        logger.warn("GribCollectionBuilderFromIndex {}: invalid index raf={}", gc.getName(), raf.getLocation());
        throw new IllegalStateException();   // temp debug
        // return false;
      }

      gc.version = raf.readInt();
      boolean versionOk = gc.version >= Grib2CollectionWriter.version;
      if (!versionOk) {
        logger.warn("GribCollectionBuilderFromIndex {}: index found version={}, want version= {} on file {}", gc.getName(), gc.version, Grib2CollectionWriter.version, raf.getLocation());
        throw new IllegalStateException();   // temp debug
        // return false;
      }

      // these are the variable records
      long skip = raf.readLong();
      raf.skipBytes(skip);
      if (debug) System.out.printf("GribCollectionBuilderFromIndex %s (%s) records len = %d%n", raf.getLocation(), getMagicStart(), skip);

      int size = NcStream.readVInt(raf);
      if ((size < 0) || (size > 200 * 1000 * 1000)) {
        logger.warn("GribCollectionBuilderFromIndex {}: invalid index size", gc.getName(), raf.getLocation());
        throw new IllegalStateException();   // temp debug
        //return false;
      }
      if (debug) System.out.printf("GribCollectionBuilderFromIndex proto len = %d%n", size);

      byte[] m = new byte[size];
      raf.readFully(m);

      /*
      message GribCollection {
        required string name = 1;         // must be unique - index filename is name.ncx
        required string topDir = 2;       // filenames are reletive to this
        repeated MFile mfiles = 3;        // list of grib MFiles
        repeated Dataset dataset = 4;
        repeated Gds gds = 5;             // unique Gds, shared amongst datasets
        required Coord masterRuntime = 21;  // list of runtimes in this GC

        required int32 center = 6;      // these 4 fields are to get a GribTable object
        required int32 subcenter = 7;
        required int32 master = 8;
        required int32 local = 9;       // grib1 table Version

        optional int32 genProcessType = 10;
        optional int32 genProcessId = 11;
        optional int32 backProcessId = 12;

        repeated Parameter params = 20;      // not used yet

        extensions 100 to 199;
      }
       */

      // see https://developers.google.com/protocol-buffers/docs/reference/java-generated#extension */
      ExtensionRegistry registry = ExtensionRegistry.newInstance();
      PartitionCollectionProto.registerAllExtensions(registry);
      GribCollectionProto.GribCollection proto = GribCollectionProto.GribCollection.parseFrom(m, registry);

      // need to read this first to get this.tables initialized
      gc.center = proto.getCenter();
      gc.subcenter = proto.getSubcenter();
      gc.master = proto.getMaster();
      gc.local = proto.getLocal();
      gc.genProcessType = proto.getGenProcessType();
      gc.genProcessId = proto.getGenProcessId();
      gc.backProcessId = proto.getBackProcessId();
      gc.local = proto.getLocal();
      this.tables = makeCustomizer();
      gc.cust = this.tables;

      if (!gc.name.equals(proto.getName())) {
        logger.info("GribCollectionBuilderFromIndex raf {}: has different name= '{}' than stored in ncx= '{}' ", raf.getLocation(), gc.getName(), proto.getName());
      }

      // directory always taken from proto, since ncx2 file may be moved, or in cache, etc  LOOK
      File protoDir = new File(proto.getTopDir());
      gc.setDirectory(protoDir);

      int fsize = 0;
      int n = proto.getMfilesCount();
      Map<Integer, MFile> fileMap = new HashMap<>(2 * n);
      for (int i = 0; i < n; i++) {
        ucar.nc2.grib.collection.GribCollectionProto.MFile mf = proto.getMfiles(i);
        fileMap.put(mf.getIndex(), new GcMFile(protoDir, mf.getFilename(), mf.getLastModified(), mf.getIndex()));
        fsize += mf.getFilename().length();
      }
      gc.setFileMap(fileMap);
      if (debug) System.out.printf("GribCollectionBuilderFromIndex files len = %d%n", fsize);

      gc.masterRuntime = (CoordinateRuntime) readCoord(proto.getMasterRuntime());

      gc.horizCS = new ArrayList<>(proto.getGdsCount());
      for (int i = 0; i < proto.getGdsCount(); i++)
        readGds(proto.getGds(i));
      gc.horizCS = Collections.unmodifiableList(gc.horizCS); // must be in order

      gc.datasets = new ArrayList<>(proto.getDatasetCount());
      for (int i = 0; i < proto.getDatasetCount(); i++)
        readDataset(proto.getDataset(i));

      return readExtensions(proto);

    } catch (Throwable t) {
      logger.warn("Error reading index " + raf.getLocation(), t);
      if (debug) t.printStackTrace();
      return false;
    }
  }

  protected boolean readExtensions(GribCollectionProto.GribCollection proto) {
    return true;
  }

  protected GribCollection.VariableIndex readVariableExtensions(GribCollection.GroupGC group, GribCollectionProto.Variable pv, GribCollection.VariableIndex vi) {
    group.addVariable(vi);
    return vi;
  }

  /*
message Dataset {
    enum Type {
      TwoD = 0;
      Best = 1;
      Analysis = 2;
    }

  required Type type = 1;
  repeated Group groups = 2;      // separate group for each GDS
}
 */
  private PartitionCollection.Dataset readDataset(GribCollectionProto.Dataset p) {
    GribCollection.Type type = GribCollection.Type.valueOf(p.getType().toString());
    GribCollection.Dataset ds = gc.makeDataset(type);

    List<GribCollection.GroupGC> groups = new ArrayList<>(p.getGroupsCount());
    for (int i = 0; i < p.getGroupsCount(); i++)
      groups.add(readGroup(p.getGroups(i)));
    ds.groups = Collections.unmodifiableList(groups);

    return ds;
  }

  /*
message Group {
  required uint32 gdsIndex = 1;       // index into GribCollection.gds array
  repeated Variable variables = 2;    // list of variables
  repeated Coord coords = 3;          // list of coordinates
  repeated int32 fileno = 4;          // the component files that are in this group, index into gc.mfiles

  repeated Parameter params = 20;      // not used yet
  extensions 100 to 199;
}
 */
  protected GribCollection.GroupGC readGroup(GribCollectionProto.Group p) {
    GribCollection.GroupGC group = gc.makeGroup();

    int gdsIndex = p.getGdsIndex();
    group.horizCoordSys = gc.getHorizCS(gdsIndex);
    group.isTwod = p.getIsTwod();

    // read coords before variables
    group.coords = new ArrayList<>();
    for (int i = 0; i < p.getCoordsCount(); i++)
      group.coords.add(readCoord(p.getCoords(i)));

    group.filenose = new int[p.getFilenoCount()];
    for (int i = 0; i < p.getFilenoCount(); i++)
      group.filenose[i] = p.getFileno(i);

    for (int i = 0; i < p.getVariablesCount(); i++)
      readVariable(group, p.getVariables(i));

    // assign names, units to coordinates
    //CalendarDate firstRef = null;
    int reftimeCoord = 0;
    int timeCoord = 0;
    int ensCoord = 0;
    List<CoordinateVert> vertCoords = new ArrayList<>();
    List<CoordinateTime2D> time2DCoords = new ArrayList<>();
    Map<CoordinateRuntime, CoordinateRuntime> runtimes = new HashMap<>();
    for (Coordinate coord : group.coords) {
      Coordinate.Type type = coord.getType();
      switch (type) {
        case runtime:
          CoordinateRuntime reftime = (CoordinateRuntime) coord;
          if (reftimeCoord > 0) reftime.setName("reftime" + reftimeCoord);
          reftimeCoord++;
          runtimes.put(reftime, reftime);
          break;

        case time:
          CoordinateTime tc = (CoordinateTime) coord;
          if (timeCoord > 0) tc.setName("time" + timeCoord);
          timeCoord++;
          break;

        case timeIntv:
          CoordinateTimeIntv tci = (CoordinateTimeIntv) coord;
          if (timeCoord > 0) tci.setName("time" + timeCoord);
          timeCoord++;
          break;

        case time2D:
          CoordinateTime2D t2d = (CoordinateTime2D) coord;
          if (timeCoord > 0) t2d.setName("time" + timeCoord);
          timeCoord++;
          time2DCoords.add(t2d);
          break;

        case vert:
          vertCoords.add((CoordinateVert) coord);
          break;

        case ens:
          CoordinateEns ce = (CoordinateEns) coord;
          if (ensCoord > 0) ce.setName("ens" + ensCoord);
          ensCoord++;
          break;
      }
    }
    assignVertNames(vertCoords);
    assignRuntimeNames(runtimes, time2DCoords, group.getId() + "-" + (group.isTwod ? "TwoD" : "Best"));

    return group;
  }

  public void assignVertNames(List<CoordinateVert> vertCoords) {
    Map<String, Integer> map = new HashMap<>(2 * vertCoords.size());

    // assign name
    for (CoordinateVert vc : vertCoords) {
      String shortName = getLevelNameShort(vc.getCode()).toLowerCase();
      if (vc.isLayer()) shortName = shortName + "_layer";

      Integer countName = map.get(shortName);
      if (countName == null) {
        map.put(shortName, 0);
      } else {
        countName++;
        map.put(shortName, countName);
        shortName = shortName + countName;
      }

      vc.setName(shortName);
    }
  }

  public void assignRuntimeNames(Map<CoordinateRuntime, CoordinateRuntime> runtimes, List<CoordinateTime2D> time2DCoords, String groupId) {

    // assign same name to internal time2D runtime as matched the external runtime
    for (CoordinateTime2D t2d : time2DCoords) {
      CoordinateRuntime runtime2D = t2d.getRuntimeCoordinate();
      CoordinateRuntime runtime = runtimes.get(runtime2D);
      if (runtime == null)
        System.out.printf("HEY assignRuntimeNames failed on %s group %s%n", t2d.getName(), groupId);
      else
        runtime2D.setName(runtime.getName());
    }

  }

  /*
message Coord {
  required int32 type = 1;   // Coordinate.Type.oridinal
  required int32 code = 2;   // time unit; level type
  required string unit = 3;
  repeated float values = 4;
  repeated float bound = 5; // only used if interval, then = (value, bound)
  repeated int64 msecs = 6; // calendar date
}
 */
  private Coordinate readCoord(GribCollectionProto.Coord pc) {
    int typei = pc.getType();
    int code = pc.getCode();
    String unit = pc.hasUnit() ? pc.getUnit() : null;  // LOOK may be null
    Coordinate.Type type = Coordinate.Type.values()[typei];

    switch (type) {
      case runtime:
        List<CalendarDate> dates = new ArrayList<>(pc.getMsecsCount());
        for (Long msec : pc.getMsecsList())
          dates.add(CalendarDate.of(msec));
        CalendarDateUnit cdUnit = CalendarDateUnit.of(null, unit);
        return new CoordinateRuntime(dates, cdUnit.getTimeUnit());

      case time:
        List<Integer> offs = new ArrayList<>(pc.getValuesCount());
        for (float val : pc.getValuesList())
          offs.add((int) val);
        CalendarDate refDate = CalendarDate.of(pc.getMsecs(0));
        CalendarPeriod timeUnit = (unit == null) ? null : CalendarPeriod.of(unit);
        return new CoordinateTime(code, timeUnit, refDate, offs);

      case timeIntv:
        List<TimeCoord.Tinv> tinvs = new ArrayList<>(pc.getValuesCount());
        for (int i = 0; i < pc.getValuesCount(); i++) {
          int val1 = (int) pc.getValues(i);
          int val2 = (int) pc.getBound(i);
          tinvs.add(new TimeCoord.Tinv(val1, val2));
        }
        refDate = CalendarDate.of(pc.getMsecs(0));
        CalendarPeriod timeUnit2 = (unit == null) ? null : CalendarPeriod.of(unit);
        return new CoordinateTimeIntv(code, timeUnit2, refDate, tinvs);

      case time2D:
        dates = new ArrayList<>(pc.getMsecsCount());
        for (Long msec : pc.getMsecsList())
          dates.add(CalendarDate.of(msec));
        CalendarPeriod timeUnit3 = (unit == null) ? null : CalendarPeriod.of(unit);
        CoordinateRuntime runtime = new CoordinateRuntime(dates, timeUnit3);

        List<Coordinate> times = new ArrayList<>(pc.getTimesCount());
        for (GribCollectionProto.Coord coordp : pc.getTimesList())
          times.add(readCoord(coordp));
        boolean isOrthogonal = pc.hasIsOrthogonal() && pc.getIsOrthogonal();
        boolean isRegular = pc.hasIsRegular() && pc.getIsRegular();
        if (isOrthogonal)
          return new CoordinateTime2D(code, timeUnit3, runtime, (CoordinateTimeAbstract) times.get(0), null);
        else if (isRegular)
          return new CoordinateTime2D(code, timeUnit3, runtime, times, null);
        else
          return new CoordinateTime2D(code, timeUnit3, null, runtime, times);

      case vert:
        boolean isLayer = pc.getValuesCount() == pc.getBoundCount();
        List<VertCoord.Level> levels = new ArrayList<>(pc.getValuesCount());
        for (int i = 0; i < pc.getValuesCount(); i++) {
          double val1 = pc.getValues(i);
          double val2 = isLayer ? pc.getBound(i) : GribNumbers.UNDEFINEDD;
          levels.add(new VertCoord.Level(val1, val2, isLayer));
        }
        return new CoordinateVert(code, tables.getVertUnit(code), levels);

      case ens:
        List<EnsCoord.Coord> ecoords = new ArrayList<>(pc.getValuesCount());
        for (int i = 0; i < pc.getValuesCount(); i++) {
          double val1 = pc.getValues(i);
          double val2 = pc.getBound(i);
          ecoords.add(new EnsCoord.Coord((int)val1, (int)val2));
        }
        return new CoordinateEns(code, ecoords);


    }
    throw new IllegalStateException("Unknown Coordinate type = " + type);
  }

  protected GribCollection.VariableIndex readVariable(GribCollection.GroupGC group, GribCollectionProto.Variable pv) {
    int discipline = pv.getDiscipline();

    byte[] rawPds = pv.getPds().toByteArray();

    int cdmHash = pv.getCdmHash();
    long recordsPos = pv.getRecordsPos();
    int recordsLen = pv.getRecordsLen();
    List<Integer> index = pv.getCoordIdxList();

    GribCollection.VariableIndex result = gc.makeVariableIndex(group, cdmHash, discipline, tables, rawPds, index, recordsPos, recordsLen);
    result.density = pv.getDensity();
    result.ndups = pv.getNdups();
    result.nrecords = pv.getNrecords();
    result.missing = pv.getMissing();

    Coordinate runtime = result.getCoordinate(Coordinate.Type.runtime);
    Coordinate time = result.getCoordinate(Coordinate.Type.time);
    if (time == null) time = result.getCoordinate(Coordinate.Type.timeIntv);
    int ntimes;
    if (time == null) {
      time = result.getCoordinate(Coordinate.Type.time2D);
      ntimes = ((CoordinateTime2D) time).getNtimes();
    } else {
      ntimes = time.getSize();
    }

    // 2d only  LOOK this is the big memory hog - but not needed except when building or for debug. can we make optional ??
    if (!dataOnly) {
      List<Integer> invCountList = pv.getInvCountList();
      if (invCountList.size() > 0) {
        result.twot = new TwoDTimeInventory(invCountList);
        result.twot.setSize(runtime.getSize(), ntimes);
      }
    }

    // 1d only
    List<Integer> time2runList = pv.getTime2RuntimeList();
    if (time2runList.size() > 0) {
      result.time2runtime = new int[time2runList.size()];
      int count = 0;
      for (int idx : time2runList) result.time2runtime[count++] = idx;
    }

    return readVariableExtensions(group, pv, result);
  }
}
