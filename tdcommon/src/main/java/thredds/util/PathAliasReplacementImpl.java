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
package thredds.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * PathAliasReplacement where alias must be at the start of the path.
 *
 * @author edavis
 * @since 4.0
 */
public class PathAliasReplacementImpl implements PathAliasReplacement {
  private static boolean debug = false;

  public static List<PathAliasReplacement> makePathAliasReplacements(Map<String, String> aliases) {
    List<PathAliasReplacement> result = new ArrayList<PathAliasReplacement>();

    for (Map.Entry<String, String> entry : aliases.entrySet()) {
      String value = entry.getValue();
      if (value == null || value.isEmpty()) continue;
      PathAliasReplacementImpl alias = new PathAliasReplacementImpl("${" + entry.getKey() + "}", value);
      result.add(alias);
      if (debug) System.out.printf("DataRootHandler alias= %s%n", alias);
    }
    return result;
  }

  private final String alias;
  private final String replacementPath;

  public PathAliasReplacementImpl(String alias, String replacementPath) {
    if (alias == null) throw new IllegalArgumentException("Alias must not be null.");
    if (replacementPath == null) throw new IllegalArgumentException("Replacment path must not be null.");

    alias = StringUtils.cleanPath(alias);
    replacementPath = StringUtils.cleanPath(replacementPath);

    // Make sure neither alias nor replacementPath ends with a slash ("/").
    this.alias = alias.endsWith("/") ? alias.substring(0, alias.length() - 1) : alias;
    this.replacementPath = replacementPath.endsWith("/") ? replacementPath.substring(0, replacementPath.length() - 1) : replacementPath;
  }

  public String getAlias() {
    return this.alias;
  }

  public String getReplacementPath() {
    return this.replacementPath;
  }

  @Override
  public boolean containsPathAlias(String path) {
    if (path == null) throw new IllegalArgumentException("Path must not be null.");
    path = StringUtils.cleanPath(path);
    return path.startsWith(alias + "/");
  }

  @Override
  public String replacePathAlias(String path) {
    if (path == null) throw new IllegalArgumentException("Path must not be null.");
    path = StringUtils.cleanPath(path);
    if (!path.startsWith(alias + "/"))
      throw new IllegalArgumentException("Path [" + path + "] does not contain alias [startWith( \"" + alias + "/\" )].");
    return replacementPath + path.substring(alias.length());
  }

  @Override
  public String replaceIfMatch(String path) {
    if (!path.startsWith(alias)) return null;
    return replacementPath + path.substring(alias.length());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("StartsWithPathAliasReplacement{");
    sb.append("alias='").append(alias).append('\'');
    sb.append(", replacementPath='").append(replacementPath).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
