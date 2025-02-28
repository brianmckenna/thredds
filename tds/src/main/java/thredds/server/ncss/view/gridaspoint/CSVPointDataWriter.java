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

package thredds.server.ncss.view.gridaspoint;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import thredds.server.ncss.util.NcssRequestUtils;
import thredds.util.ContentType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.constants.CDM;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridAsPointDataset;
import ucar.nc2.time.CalendarDate;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.vertical.VerticalTransform;

class CSVPointDataWriter implements PointDataWriter  {
	static private Logger log = LoggerFactory.getLogger(CSVPointDataWriter.class);

  public static CSVPointDataWriter factory(OutputStream os){
 		return new CSVPointDataWriter(os);
 	}

	private PrintStream printWriter;
	private Map<String,List<String>> allVars;
	private Map<String, GridAsPointDataset> gridAsPointDatasets = new HashMap<>(); // LOOK WTF ??
	private boolean headersSet = false;
	private HttpHeaders httpHeaders;

	private CSVPointDataWriter(OutputStream os) {
    try {
      printWriter= new PrintStream(os, false, CDM.UTF8);
    } catch (UnsupportedEncodingException e) {
      log.error("CSVPointDataWriter", e);
    }
  }

	public boolean header(Map<String,List<String>> groupedVars, GridDataset gridDataset, List<CalendarDate> wDates, List<Attribute> timeDimAtts, LatLonPoint point, Double vertCoord) {
		allVars = groupedVars; 
		for(Entry<String, List<String>> entry : groupedVars.entrySet()){
			gridAsPointDatasets.put( entry.getKey() , NcssRequestUtils.buildGridAsPointDataset(gridDataset, entry.getValue()));
		}
		return true;
	}

	/*
	 * 
	 *  We write one header for each variables group so in this case iterate 
	 *  over variable group and over time for each group 
	 *  
	 * 	(non-Javadoc)
	 * @see thredds.server.ncSubset.view.PointDataWriter#write(java.util.Map, ucar.nc2.dt.GridDataset, java.util.List, ucar.unidata.geoloc.LatLonPoint, java.lang.Double)
	 */
	public boolean write(Map<String, List<String>> groupedVars, GridDataset gds, List<CalendarDate> wDates, LatLonPoint point, Double vertCoord) throws InvalidRangeException{

		boolean allDone = true;

		List<String> keys =new ArrayList<>(groupedVars.keySet());
		//loop over variable groups
		int contKeys = 1;
		for(String key : keys){

			List<String> varsGroup = groupedVars.get(key);
			boolean hasEnsembleDim =gds.findGridByShortName(varsGroup.get(0)).getEnsembleDimension() !=null;			
			writeGroupHeader(varsGroup, gds, hasEnsembleDim, !wDates.isEmpty());
			boolean pointRead =true;
			if(wDates.isEmpty()){
				//pointRead = write(varsGroup, gds, point, vertCoord);
				pointRead = write(key, gds, point, vertCoord);
			}else{
				//Loop over time
				CalendarDate date;
				Iterator<CalendarDate> it = wDates.iterator();				
				while( pointRead && it.hasNext() ){
					date = it.next();
					//pointRead = write(varsGroup, gds, date, point, vertCoord);
					pointRead = write(key, gds, date, point, vertCoord);
				}
			}

			if( contKeys != keys.size() )
				printWriter.println();

			contKeys++;
		}

		return allDone;
	}	


	/*
	 * Write method for datasets without time dimension
	 */
	private boolean write(String keyVarsGroup, GridDataset gridDataset, LatLonPoint point, Double targetLevel) {

		boolean allDone = true;

		List<String> varsGroup = allVars.get(keyVarsGroup);
		GridAsPointDataset gap = gridAsPointDatasets.get(keyVarsGroup);

		CoordinateAxis1D verticalAxisForGroup = gridDataset.findGridDatatype(varsGroup.get(0)).getCoordinateSystem().getVerticalAxis();
		if(verticalAxisForGroup ==null){
			//Read and write vars--> point
			allDone = allDone && write(varsGroup, gridDataset, gap, point);
		}else{
			//read and write time, verCoord for each variable in group
			if(targetLevel != null){
				Double vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, targetLevel);
				allDone = write(varsGroup, gridDataset, gap,  point, vertCoord, verticalAxisForGroup.getUnitsString() );
			}else{//All levels
				for(Double vertCoord : verticalAxisForGroup.getCoordValues() ){
					/////Fix axis!!!!
					if(verticalAxisForGroup.getCoordValues().length ==1  )
						vertCoord =NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, vertCoord);

					allDone = allDone && write(varsGroup, gridDataset, gap, point, vertCoord, verticalAxisForGroup.getUnitsString() );

				}
			}				

		}			
		//printWriter.println();
		//}

		return allDone;
	}	

	/*
	 * write method for grids with time dimension
	 */
	private boolean write(String keyVarsGroup,	GridDataset gridDataset, CalendarDate date, LatLonPoint point, Double targetLevel) throws InvalidRangeException {
		boolean allDone = true;

		List<String> varsGroup = allVars.get(keyVarsGroup);
		GridAsPointDataset gap = gridAsPointDatasets.get(keyVarsGroup) ;

		CoordinateAxis1D verticalAxisForGroup = gridDataset.findGridDatatype( varsGroup.get(0)).getCoordinateSystem().getVerticalAxis();

		//Ensemble handling...
		GridDatatype refGrid = gridDataset.findGridDatatype( varsGroup.get(0));
		CoordinateAxis1D ensembleAxisForGroup = refGrid.getCoordinateSystem().getEnsembleAxis();
		double[] ensCoords = new double[]{-1};
		if(ensembleAxisForGroup != null){
			ensCoords = ensembleAxisForGroup.getCoordValues(); 
		}

		for(double ensCoord : ensCoords){		

			if(verticalAxisForGroup ==null){
				//Read and write vars--> time, point
				allDone = allDone && write(varsGroup, gridDataset, gap, date, point, ensCoord);
			}else{


				//read and write [ensCoord], time, verCoord for each variable in group			
				if(targetLevel != null){
					Double vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, targetLevel);
					allDone = write(varsGroup, gridDataset, gap, date, point, ensCoord, vertCoord, verticalAxisForGroup.getUnitsString() );
				}else{//All levels
					for(Double vertCoord : verticalAxisForGroup.getCoordValues() ){
						/////Fix axis!!!!
						if(verticalAxisForGroup.getCoordValues().length ==1  )
							vertCoord =NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, vertCoord);

						allDone = allDone && write(varsGroup, gridDataset, gap, date, point, ensCoord, vertCoord, verticalAxisForGroup.getUnitsString() );

					}
				}				
			}
		}

		return allDone;
	}	

	private void writeGroupHeader(List<String> varGroup, GridDataset gridDataset, boolean hasEnsAxis, boolean hasTimeAxis){

		StringBuilder sb = new StringBuilder();		
		if(hasTimeAxis)
			sb.append("date,");
		sb.append("lat[unit=\"degrees_north\"],");
		sb.append("lon[unit=\"degrees_east\"],");

		if(hasEnsAxis)
			sb.append("ensMember,");

		GridCoordSystem coordSystem = gridDataset.findGridDatatype(varGroup.get(0)).getCoordinateSystem();
		CoordinateAxis1D zAxis = coordSystem.getVerticalAxis();

		if(zAxis != null)
			sb.append("vertCoord[unit=\"").append(zAxis.getUnitsString()).append("\"],");

		VerticalTransform vt = coordSystem.getVerticalTransform();
		if(vt != null){
			sb.append("vertCoord[unit=\"").append(vt.getUnitString()).append("\"],");
		}

		Iterator<String> it = varGroup.iterator();
		while(it.hasNext()){
			GridDatatype grid = gridDataset.findGridDatatype(it.next());
			sb.append(grid.getName());			
			if( grid.getUnitsString()!=null ) sb.append("[unit=\"").append(grid.getUnitsString()).append("\"]");
			if(it.hasNext()) sb.append(",");
		}

		printWriter.print(sb.toString());
		printWriter.println();					
	}


	private boolean write(List<String> vars, GridDataset gridDataset, GridAsPointDataset gap,  LatLonPoint point,	Double targetLevel, String zUnits) {

		boolean allDone = false;

		int contVars= 0;					 
		Iterator<String> itVars = vars.iterator();
		try{
			while (itVars.hasNext()) {
				GridDatatype grid = gridDataset.findGridDatatype(itVars.next());
				if ( gap.hasVert(grid, targetLevel) ) {
					GridAsPointDataset.Point p = gap.readData(grid, null, targetLevel, point.getLatitude(), point.getLongitude());
					if(contVars == 0){
						printWriter.print(point.getLatitude());
						printWriter.print(",");
						printWriter.print(point.getLongitude());
            printWriter.print("," );
						printWriter.print(p.z);
            printWriter.print(",");
					}
					printWriter.print(p.dataValue);
					if(itVars.hasNext()) printWriter.print(",");

				} else {
					// write missingvalues!!!
					if(contVars==0){
						printWriter.print( point.getLatitude());
            printWriter.print(",");
						printWriter.print( point.getLongitude());
            printWriter.print(",");
						printWriter.print( targetLevel);
            printWriter.print(",");
					}
					printWriter.print(gap.getMissingValue(grid));
					if(itVars.hasNext()) printWriter.print(",");
				}					
				contVars++;
			}
			allDone = true;
		}catch(IOException ioe){
			Log.error("Error reading data", ioe);
		}	
		printWriter.println();	
		return allDone;

	}	

	private boolean write(List<String> vars, GridDataset gridDataset, GridAsPointDataset gap, CalendarDate date, LatLonPoint point,	Double ensCoord,Double targetLevel, String zUnits) throws InvalidRangeException {

		boolean allDone = false;
		printWriter.print(date.toString());
    printWriter.print(",");
		int contVars= 0;
		Iterator<String> itVars = vars.iterator();
		try{
			while (itVars.hasNext()) {
				GridDatatype grid = gridDataset.findGridDatatype(itVars.next());

				double actualLevel = NcssRequestUtils.getActualVertLevel(grid, date, point, targetLevel);

				if ( gap.hasTime(grid, date) && gap.hasVert(grid, targetLevel) ) {
					GridAsPointDataset.Point p = gap.readData(grid, date, ensCoord, targetLevel, point.getLatitude(), point.getLongitude());
					if(contVars == 0){							
						printWriter.print(point.getLatitude());
            printWriter.print("," );
						printWriter.print(point.getLongitude());
            printWriter.print("," );
						if( ensCoord >= 0 ) {
              printWriter.print(p.ens);
              printWriter.print(",");
            }

						printWriter.print(p.z);
            printWriter.print("," );

						if(actualLevel != -9999.9) {//Print the actual level LOOK WTF ??
              printWriter.print(actualLevel);
              printWriter.print(",");
            }

					}							
					printWriter.print(p.dataValue);
					if (itVars.hasNext()) printWriter.print(",");

				} else {
					// write missingvalues!!!
					if(contVars==0){
						printWriter.print(point.getLatitude());
            printWriter.print(",");
						printWriter.print(point.getLongitude());
            printWriter.print(",");
						printWriter.print(targetLevel);
					}
					printWriter.print(gap.getMissingValue(grid));
					if (itVars.hasNext()) printWriter.print(",");
				}					
				contVars++;

			}
			allDone = true;
		}catch(IOException ioe){
			Log.error("Error reading data", ioe);
		}	
		printWriter.println();	
		return allDone;

	}

	/*
	 * Write method for grids without time and vertical dimensions
	 */
	private boolean write(List<String> vars, GridDataset gridDataset, GridAsPointDataset gap, LatLonPoint point) {

		boolean allDone = false;
		int contVars= 0;					 
		Iterator<String> itVars = vars.iterator();
		try{
			while (itVars.hasNext()) {
				GridDatatype grid = gridDataset.findGridDatatype(itVars.next());
				//if (gap.hasTime(grid, date) ) {
				GridAsPointDataset.Point p = gap.readData(grid, null, point.getLatitude(), point.getLongitude());
				if(contVars == 0){
					printWriter.print(point.getLatitude());
          printWriter.print(",");
					printWriter.print(point.getLongitude());
          printWriter.print("," );
				}
				printWriter.print(p.dataValue);
				if (itVars.hasNext()) printWriter.print(",");

				//} else {
				// write missingvalues!!!
				//	if(contVars==0){
				//		printWriter.write( point.getLatitude()+"," );
				//		printWriter.write( point.getLongitude() +"," );
				//	}
				//	printWriter.write( Double.valueOf(gap.getMissingValue(grid)).toString() );
				//}					
				contVars++;
			}
			allDone = true;
		}catch(IOException ioe){
			Log.error("Error reading data", ioe);
		}	
		printWriter.println();	
		return allDone;

	}	


	private boolean write(List<String> vars, GridDataset gridDataset, GridAsPointDataset gap, CalendarDate date, LatLonPoint point, Double ensCoord) {

		boolean allDone = false;
		printWriter.print(date.toString());
    printWriter.print("," );
		int contVars= 0;
		Iterator<String> itVars = vars.iterator();
		try{
			while (itVars.hasNext()) {
				GridDatatype grid = gridDataset.findGridDatatype(itVars.next());
				if (gap.hasTime(grid, date) ) {
					GridAsPointDataset.Point p = gap.readData(grid, date, ensCoord, -1, point.getLatitude(), point.getLongitude());
					if(contVars == 0){
						printWriter.print( point.getLatitude());
            printWriter.print("," );
						printWriter.print( point.getLongitude());
            printWriter.print("," );
						if (ensCoord >= 0) {
              printWriter.print(p.ens);
              printWriter.print("," );
            }
					}							
					printWriter.print(p.dataValue);
					if(itVars.hasNext()) printWriter.print(",");

				} else {
					// write missingvalues!!!
					if (contVars==0){
						printWriter.print(point.getLatitude());
            printWriter.print("," );
						printWriter.print(point.getLongitude());
            printWriter.print("," );
					}
					printWriter.print(grid);
					if(itVars.hasNext()) printWriter.print(",");
				}					
				contVars++;
			}
			allDone = true;
		}catch(IOException ioe){
			Log.error("Error reading data", ioe);
		}	
		printWriter.println();	
		return allDone;

	}

	@Override
	public boolean trailer() {

		printWriter.flush();
		return true;
	}

	@Override
	public HttpHeaders getResponseHeaders(){

		return httpHeaders;
	}

	@Override
	public void setHTTPHeaders(GridDataset gridDataset, String pathInfo, boolean isStream){

		if(!headersSet){
			httpHeaders = new HttpHeaders();
			if(!isStream){
				httpHeaders.set("Content-Location", pathInfo );
				httpHeaders.set("Content-Disposition", "attachment; filename=\"" + NcssRequestUtils.nameFromPathInfo(pathInfo) + ".csv\"");
			}			
			
      httpHeaders.set(ContentType.HEADER, ContentType.csv.getContentHeader());
			//httpHeaders.setContentType( MediaType.TEXT_PLAIN );
		}
		headersSet = true;
	}	

}
