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
package thredds.util.filesource;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * _more_
 *
 * @author edavis
 * @since 4.0
 */
public class BasicWithExclusionsDescendantFileSource implements DescendantFileSource {
  //private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BasicWithExclusionsDescendantFileSource.class);

  private final BasicDescendantFileSource root;
  private final List<BasicDescendantFileSource> exclusions;

  public BasicWithExclusionsDescendantFileSource(String rootDirectoryPath,
                                                 List<String> exclusions) {
    if (exclusions == null)
      throw new IllegalArgumentException("Exclusion list must not be null.");
    if (exclusions.isEmpty())
      throw new IllegalArgumentException("Exclusion list must not be empty.");

    this.root = new BasicDescendantFileSource(rootDirectoryPath);
    this.exclusions = initExclusions(exclusions);
  }

  public BasicWithExclusionsDescendantFileSource(File rootDirectory,
                                                 List<String> exclusions) {
    if (exclusions == null)
      throw new IllegalArgumentException("Exclusion list must not be null.");
    if (exclusions.isEmpty())
      throw new IllegalArgumentException("Exclusion list must not be empty.");

    this.root = new BasicDescendantFileSource(rootDirectory);
    this.exclusions = initExclusions(exclusions);
  }

  private List<BasicDescendantFileSource> initExclusions(List<String> exclusions) {
    List<BasicDescendantFileSource> list = new ArrayList<>();
    BasicDescendantFileSource bdfs;
    for (String curDfsRdp : exclusions) {
      if (curDfsRdp == null)
        throw new IllegalArgumentException("Exclusion list may not contain null items.");
      bdfs = (BasicDescendantFileSource) this.root.getDescendant(curDfsRdp);
      if (bdfs == null)
        throw new IllegalArgumentException("Exclusion [" + curDfsRdp + "] was null, not relative, or not descendant of root.");

      list.add(bdfs);
    }
    return list;
  }

  public File getFile(String path) {
    File file = this.root.getFile(path);
    for (BasicDescendantFileSource curBdfs : this.exclusions) {
      if (curBdfs.getRootDirectory().equals(file)
              || curBdfs.isDescendant(file))
        return null;
    }
    return file;
  }

  public DescendantFileSource getDescendant(String relativePath) {
    DescendantFileSource dfs = this.root.getDescendant(relativePath);
    for (BasicDescendantFileSource curBdfs : this.exclusions) {
      if (curBdfs.getRootDirectory().equals(dfs.getRootDirectory())
              || curBdfs.isDescendant(dfs.getRootDirectory()))
        return null;
    }
    return dfs;
  }

  public File getRootDirectory() {
    return this.root.getRootDirectory();
  }

  public String getRootDirectoryPath() {
    return this.root.getRootDirectoryPath();
  }

  public boolean isDescendant(File file) {
    if (!this.root.isDescendant(file))
      return false;

    for (BasicDescendantFileSource curBdfs : this.exclusions) {
      if (curBdfs.getRootDirectory().equals(getCleanAbsoluteFile(file))
              || curBdfs.isDescendant(file))
        return false;
    }
    return true;
  }

  public boolean isDescendant(String filePath) {
    return filePath != null && isDescendant(new File(filePath));
  }

  public String getRelativePath(File file) {
    for (BasicDescendantFileSource curBdfs : this.exclusions) {
      if (curBdfs.getRootDirectory().equals(getCleanAbsoluteFile(file))
              || curBdfs.isDescendant(file))
        return null;
    }
    return this.root.getRelativePath(file);
  }

  public String getRelativePath(String filePath) {
    if (filePath == null)
      return null;
    return getRelativePath(new File(filePath));
  }

  private File getCleanAbsoluteFile(File file) {
    return new File(StringUtils.cleanPath(file.getAbsolutePath()).trim());
  }
}
