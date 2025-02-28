package thredds.server.ncss.view.dsg.station;

import org.springframework.http.HttpHeaders;
import thredds.server.ncss.exception.NcssException;
import thredds.server.ncss.params.NcssParamsBean;
import thredds.server.ncss.util.NcssRequestUtils;
import thredds.util.ContentType;
import ucar.ma2.Array;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.ft.FeatureDatasetPoint;
import ucar.nc2.ft.point.StationPointFeature;
import ucar.nc2.time.CalendarDateFormatter;
import ucar.unidata.geoloc.Station;
import ucar.unidata.util.Format;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Created by cwardgar on 2014-05-24.
 */
public class StationSubsetWriterCSV extends AbstractStationSubsetWriter {
    private final PrintWriter writer;

    public StationSubsetWriterCSV(FeatureDatasetPoint fdPoint, NcssParamsBean ncssParams, OutputStream out)
            throws NcssException, IOException {
        super(fdPoint, ncssParams);
        this.writer = new PrintWriter(out);
    }

    @Override
    public HttpHeaders getHttpHeaders(String datasetPath, boolean isStream) {
        HttpHeaders httpHeaders = new HttpHeaders();

        if (!isStream) {
            httpHeaders.set("Content-Location", datasetPath);
            httpHeaders.set("Content-Disposition",
                    "attachment; filename=\"" + NcssRequestUtils.nameFromPathInfo(datasetPath) + ".csv\"");
            httpHeaders.add(ContentType.HEADER, ContentType.csv.getContentHeader());
        } else {
            // The problem is that the browser won't display text/csv inline.
            httpHeaders.add(ContentType.HEADER, ContentType.text.getContentHeader());
        }

        return httpHeaders;
    }

    @Override
    protected void writeHeader(StationPointFeature stationPointFeat) throws IOException {
        writer.print("time,station,latitude[unit=\"degrees_north\"],longitude[unit=\"degrees_east\"]");
        for (VariableSimpleIF wantedVar : wantedVariables) {
            writer.print(",");
            writer.print(wantedVar.getShortName());
            if (wantedVar.getUnitsString() != null)
                writer.print("[unit=\"" + wantedVar.getUnitsString() + "\"]");
        }
        writer.println();
    }

    @Override
    protected void writeStationPointFeature(StationPointFeature stationPointFeat) throws IOException {
        Station station = stationPointFeat.getStation();

        writer.print(CalendarDateFormatter.toDateTimeString(stationPointFeat.getObservationTimeAsCalendarDate()));
        writer.print(',');
        writer.print(station.getName());
        writer.print(',');
        writer.print(Format.dfrac(station.getLatitude(), 3));
        writer.print(',');
        writer.print(Format.dfrac(station.getLongitude(), 3));

        for (VariableSimpleIF wantedVar : wantedVariables) {
            writer.print(',');
            Array dataArray = stationPointFeat.getData().getArray(wantedVar.getShortName());
            writer.print(dataArray.toString().trim());
        }
        writer.println();
    }

    @Override
    protected void writeFooter() throws IOException {
        writer.flush();
    }
}
