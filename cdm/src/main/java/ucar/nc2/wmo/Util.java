package ucar.nc2.wmo;

import ucar.unidata.util.StringUtil2;

/**
 * Uitlities common to WMO table parsing
 *
 * @author caron
 * @since 3/1/13
 */
public class Util {

  /**
   * Clean up strings to be used for unit string
   * @param unit original
   * @return cleaned up
   */
  static public String cleanUnit(String unit) {
    if (unit == null) return null;
    if (unit.equalsIgnoreCase("-")) unit = "";
    else {
      if (unit.startsWith("/")) unit = "1" + unit;
      unit = unit.trim();
      unit = StringUtil2.remove(unit, "**");
      StringBuilder sb = new StringBuilder(unit);
      StringUtil2.remove(sb, "^[]");
      StringUtil2.replace(sb, ' ', ".");
      StringUtil2.replace(sb, '*', ".");
      unit = sb.toString();
    }
    return unit;
  }

  /**
   * Clean up strings to be used in Netcdf Object names
   * @param name original name
   * @return cleaned up name
   */
  static public String cleanName(String name) {
    if (name == null) return null;
    int pos = name.indexOf("(see");
    if (pos < 0) pos = name.indexOf("(See");
    if (pos > 0) name = name.substring(0,pos);

    name = StringUtil2.replace(name, '/', "-");
    StringBuilder sb = new StringBuilder(name);
    StringUtil2.replace(sb, '+', "plus");
    StringUtil2.remove(sb, ".;,=[]()/*\"");
    return StringUtil2.collapseWhitespace(sb.toString().trim());
  }




}
