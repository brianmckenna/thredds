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

import thredds.featurecollection.FeatureCollectionConfig;
import ucar.unidata.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Describe
 *
 * @author caron
 * @since 2/21/14
 */
public class Grib1PartitionBuilderFromIndex extends Grib1CollectionBuilderFromIndex {

  // read in the index, index raf already open; return null on failure
  static public PartitionCollection createTimePartitionFromIndex(String name, RandomAccessFile raf,
           FeatureCollectionConfig config, boolean dataOnly, org.slf4j.Logger logger) throws IOException {

    Grib1PartitionBuilderFromIndex builder = new Grib1PartitionBuilderFromIndex(name, config, dataOnly, logger);
    if (builder.readIndex(raf))
      return builder.pc;

    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////

  //private final PartitionManager tpc; // defines the partition
  private PartitionCollection pc;  // build this object

  private Grib1PartitionBuilderFromIndex(String name, FeatureCollectionConfig config, boolean dataOnly, org.slf4j.Logger logger) {
    super(name, config, dataOnly, logger);
    this.pc = new Grib1Partition(name, null, config, logger);
    this.gc = pc;
  }

  @Override
  public String getMagicStart() {
    return Grib1PartitionBuilder.MAGIC_START;
  }

  ///////////////////////////////////////////////////////////////////////////
  // reading ncx

  /*
  extend GribCollection {
    repeated Partition partitions = 100;
    required bool isPartitionOfPartitions = 101;
    repeated uint32 run2part = 102;       // masterRuntime index to partition index
  }
   */
  @Override
  protected boolean readExtensions(GribCollectionProto.GribCollection proto) {
    pc.isPartitionOfPartitions = proto.getExtension(PartitionCollectionProto.isPartitionOfPartitions);

    List<Integer> list = proto.getExtension(PartitionCollectionProto.run2Part);
    pc.run2part = new int[list.size()];
    int count = 0;
    for (int partno : list)
      pc.run2part[count++] = partno;

    List<ucar.nc2.grib.collection.PartitionCollectionProto.Partition> partList = proto.getExtension(PartitionCollectionProto.partitions);
    for (ucar.nc2.grib.collection.PartitionCollectionProto.Partition partProto : partList)
      makePartition(partProto);

    return partList.size() > 0;
  }

  /*
  extend Variable {
    repeated PartitionVariable partition = 100;
    repeated Parameter vparams = 101;    // not used yet
  }
   */
  @Override
  protected GribCollection.VariableIndex readVariableExtensions(GribCollection.GroupGC group, GribCollectionProto.Variable proto, GribCollection.VariableIndex vi) {
    List<PartitionCollectionProto.PartitionVariable> pvList = proto.getExtension(PartitionCollectionProto.partition);

    PartitionCollection.VariableIndexPartitioned vip = pc.makeVariableIndexPartitioned(group, vi, pvList.size());
    /* vip.density = vi.density;   // ??
    vip.missing = vi.missing;
    vip.ndups = vi.ndups;
    vip.nrecords = vi.nrecords;  */

    for (PartitionCollectionProto.PartitionVariable pv : pvList) {
      vip.addPartition(pv.getPartno(), pv.getGroupno(), pv.getVarno(), pv.getFlag(), pv.getNdups(),
              pv.getNrecords(), pv.getMissing(), pv.getDensity());
    }

    return vip;
  }

  /*
message Partition {
  required string name = 1;       // name is used in TDS - eg the subdirectory when generated by TimePartitionCollections
  required string filename = 2;   // the gribCollection.ncx2 file
  required string directory = 3;   // top directory
  optional uint64 lastModified = 4;
}
   */
  private PartitionCollection.Partition makePartition(PartitionCollectionProto.Partition proto) {

    return pc.addPartition(proto.getName(), proto.getFilename(), proto.getLastModified(), proto.getDirectory());
  }
}
