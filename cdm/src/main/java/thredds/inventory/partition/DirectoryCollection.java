/*
 *
 *  * Copyright 1998-2013 University Corporation for Atmospheric Research/Unidata
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

package thredds.inventory.partition;

import thredds.filesystem.MFileOS7;
import thredds.inventory.CollectionAbstract;
import thredds.inventory.MFile;
import ucar.nc2.util.CloseableIterator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Manage MFiles from one directory.
 * Use getFileIterator() for best performance on large directories
 *
 * @author caron
 * @since 11/16/13
 */
public class DirectoryCollection extends CollectionAbstract {

  /**
   * Create standard name = topCollectionName + last directory
   * @param topCollectionName from config, name of the collection
   * @param dir directory for this
   * @return  standard collection name, to name the index file
   */
  public static String makeCollectionName(String topCollectionName, Path dir) {
    int last = dir.getNameCount()-1;
    Path lastDir = dir.getName(last);
    String lastDirName = lastDir.toString();
    return topCollectionName +"-" + lastDirName;
  }

  /**
   * Create standard name = topCollectionName + last directory
   * @param topCollectionName from config, name of the collection
   * @param dir directory for this
   * @return  standard collection name, to name the index file
   */
  public static Path makeCollectionIndexPath(String topCollectionName, Path dir) {
    String collectionName = makeCollectionName(topCollectionName, dir);
    return Paths.get(dir.toString(), collectionName + NCX_SUFFIX);
  }

  ///////////////////////////////////////////////////////////////////////////////////

  final String topCollection;
  final Path topDir;

  public DirectoryCollection(String topCollectionName, Path topDir, org.slf4j.Logger logger) {
    super(topCollectionName, logger);
    this.topCollection = cleanName(topCollectionName);
    this.topDir = topDir;
    this.collectionName = makeCollectionName(collectionName, topDir);
  }

  public DirectoryCollection(String topCollectionName, String topDirS, org.slf4j.Logger logger) {
    this(topCollectionName, Paths.get(topDirS), logger);
  }

  public Path getIndexPath() {
    return DirectoryCollection.makeCollectionIndexPath(topCollection, topDir);
  }

  public boolean isLeafDirectory() throws IOException {
    int countDir=0, countFile=0, count =0;
    try (CloseableIterator<MFile> iter = getFileIterator()) {
      while (iter.hasNext() && count++ < 100) {
        MFile file = iter.next();
        if (file.isDirectory()) countDir++; else countFile++;
      }
    }
    return countFile > countDir;
  }

  @Override
  public String getRoot() {
    return topDir.toString();
  }

  @Override
  public String getIndexFilename() {
    return getIndexPath().toString();
  }

  @Override
  public Iterable<MFile> getFilesSorted() throws IOException {
    return makeFileListSorted();
  }

  @Override
  public CloseableIterator<MFile> getFileIterator() throws IOException {
    return new MyFileIterator(topDir);
  }

  @Override
  public void close() { }

  // returns everything in the current directory
  private class MyFileIterator implements CloseableIterator<MFile> {
    DirectoryStream<Path> dirStream;
    Iterator<Path> dirStreamIterator;
    MFile nextMFile;
    int count = 0;

    MyFileIterator(Path dir) throws IOException {
      if (debug) System.out.printf(" DirectoryCollection.MFileIterator %s ", topDir);
      dirStream = Files.newDirectoryStream(dir, new MyGribFilter());
      dirStreamIterator = dirStream.iterator();
    }

    public boolean hasNext() {
      while (true) {
        if (debug && count % 100 == 0) System.out.printf("%d ", count);
        count++;
        if (!dirStreamIterator.hasNext()) return false;

        try {
          Path nextPath = dirStreamIterator.next();
          BasicFileAttributes attr =  Files.readAttributes(nextPath, BasicFileAttributes.class);
          if (attr.isDirectory()) continue;
          nextMFile = new MFileOS7(nextPath, attr);

       } catch (IOException e) {
         throw new RuntimeException(e);
       }
       if (filter == null || filter.accept(nextMFile)) return true;
      }
    }

    public MFile next() {
      return nextMFile;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    // better alternative is for caller to send in callback (Visitor pattern)
    // then we could use the try-with-resource
    public void close() throws IOException {
      if (debug) System.out.printf("%n");
      dirStream.close();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  private static final boolean debug = false;
  // this idiom keeps the iterator from escaping, so that we can use try-with-resource, and ensure it closes. like++
  public void iterateOverMFileCollection(Visitor visit) throws IOException {
    if (debug) System.out.printf("DirectoryCollection.iterateOverMFileCollection %s ", topDir);
    int count = 0;
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(topDir, new MyGribFilter())) {
      for (Path p : ds) {
        BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
        if (!attr.isDirectory())
          visit.consume(new MFileOS7(p));
        if (debug) System.out.printf("%d ", count++);
      }
    }
    if (debug) System.out.printf("%d%n ", count);
  }

  public interface Visitor {
     public void consume(MFile mfile);
  }

}
