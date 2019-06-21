package br3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

/**
 * Read Makibes BR3 GPS and HR files and combine them prior to upload to Strava etc
 *
 * Bugs: TimeZone isn't right
 *
 * @author D George
 */
public class ReadGPS {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        int offset  = ZonedDateTime.now().getOffset().getTotalSeconds();

        String dirName = "gps"; // current directory
        // your latitude / longitude for security zone
        double latSecu = 0.0;
        double lonSecu = 0.0;
        double securityRadius = 0.001; // around 100 meters

        File dir = new File(dirName);
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return (name.matches("[0-9]*_gps_.*txt"));
            }
        });

        for (File gpxFile : files) {
            System.out.println(gpxFile);

            String contents = null;
            int[] heartRate = null;
            try {
                // one reading every minute !!!
                String hrFileName = gpxFile.getName().replace("_gps_", "_hr_");
                contents = new String(Files.readAllBytes(Paths.get(dirName + "/" + hrFileName)));
                JsonNode hr = objectMapper.readTree(contents).get("h3");
                if (hr != null) {
                    heartRate = new int[hr.size()];
                    for (int i = 0; i < hr.size(); i++) {
                        heartRate[i] = hr.get(i).asInt();
                    }// for
                }

                contents = new String(Files.readAllBytes(Paths.get(gpxFile.getPath())));
                JsonNode coords = objectMapper.readTree(contents);
                StringBuilder buffer = new StringBuilder();
                long startTime = 0;

                if (coords != null) {
                    int currentHeartRate = 0;
                    for (int i = 0; i < coords.size(); i++) {
                        JsonNode coord = coords.get(i);
                        double lon = coord.get("X").asDouble();
                        double lat = coord.get("Y").asDouble();
                        if (lon > (lonSecu - securityRadius) && lon < (lonSecu + securityRadius)) {
                            if (lat > (latSecu - securityRadius) && lat < (latSecu + securityRadius)) {
                                System.out.println("Within security zone lat " + lat + " lon " + lon);
                                continue; // within security zone
                            }
                        }

                        buffer.append("\n<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">\n");

                        long timeInSecs = coord.get("T").asLong() - offset;
                        Date d = new Date(timeInSecs * 1000);
                        if (i == 0) {
                            startTime = timeInSecs;
                        }
                        buffer.append("  <time>" + format.format(d) + "</time>\n");

                        int timeInMins = (int) (timeInSecs - startTime) / 60;
                        if (heartRate != null) {
                            if (heartRate[timeInMins] > 0) {
                                currentHeartRate = heartRate[timeInMins];
                            }
                            buffer.append("  <extensions>\n    <gpxtpx:TrackPointExtension><gpxtpx:hr>" + currentHeartRate + "</gpxtpx:hr></gpxtpx:TrackPointExtension>\n  </extensions>");
                            buffer.append("\n</trkpt>");

                        }
                    }// for

                    SimpleDateFormat fileFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
                    String startDate = fileFormat.format(new Date(startTime * 1000));

                    try (PrintWriter out = new PrintWriter(dirName + "/" + startDate + ".gpx")) {
                        out.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                        out.print(
                                "<gpx creator=\"Makibes BR3\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\" version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\"  xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\">\n");
                        out.print("<trk>\n  <name>");
                        out.print("Activity " + startDate);
                        out.print("</name>\n<trkseg>");
                        out.println(buffer);
                        out.println("</trkseg></trk></gpx>");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

