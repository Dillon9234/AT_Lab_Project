
package com.example.project;

import java.util.Calendar;
import java.util.TimeZone;

public class AstronomicalCalculator {
    private static final double EARTH_RADIUS = 6371000; // in meters

    public static class CelestialCoordinates {
        public final double rightAscension;
        public final double declination;
        public final double azimuth;
        public final double altitude;

        public CelestialCoordinates(double rightAscension, double declination,
                                    double azimuth, double altitude) {
            this.rightAscension = rightAscension;
            this.declination = declination;
            this.azimuth = azimuth;
            this.altitude = altitude;
        }
    }

    public static CelestialCoordinates calculateCoordinatesFromQuaternion(
            double latitude, double longitude, double gpsAltitude,
            float qx, float qy, float qz, float qw) {

        // Convert quaternion to direction vector
        double[] directionVector = quaternionToVector(qx, qy, qz, qw);

        // Calculate azimuth and altitude from direction vector
        double azimuth = Math.atan2(-directionVector[1], directionVector[0]);
        double altitudeRad = -Math.asin(directionVector[2]);

        // Normalize azimuth to [0, 2π]
        azimuth = (azimuth + 2 * Math.PI) % (2 * Math.PI);
        azimuth = (azimuth -  Math.PI/2) % (2 * Math.PI);
        azimuth = (azimuth + 2 * Math.PI) % (2 * Math.PI);
        // Convert to degrees
        double azimuthDeg = Math.toDegrees(azimuth);
        double altitudeDeg = Math.toDegrees(altitudeRad);

        // Convert to radians for astronomical calculations
        double latRad = Math.toRadians(latitude);

        // Calculate declination (Dec)
        double dec = Math.asin(Math.sin(latRad) * Math.sin(altitudeRad) +
                Math.cos(latRad) * Math.cos(altitudeRad) * Math.cos(azimuth));

        // Calculate hour angle (HA)
        double ha = Math.atan2(-Math.sin(azimuth) * Math.cos(altitudeRad),
                Math.cos(latRad) * Math.sin(altitudeRad) -
                        Math.sin(latRad) * Math.cos(altitudeRad) * Math.cos(azimuth));

        // Calculate Local Sidereal Time (LST)
        double lst = calculateLST(longitude);

        // Calculate RA
        double ra = (lst - Math.toDegrees(ha)) % 360;
        if (ra < 0) {
            ra += 360;
        }

        // Convert RA to hours (24 hours = 360 degrees)
        ra = ra / 15.0;

        // Convert Dec to degrees
        dec = Math.toDegrees(dec);

        return new CelestialCoordinates(ra, dec, azimuthDeg, altitudeDeg);
    }


    private static double[] quaternionToVector(float qx, float qy, float qz, float qw) {
        double[] vector = new double[3];

        vector[0] = 2 * (qx * qz + qw * qy);
        vector[1] = 2 * (qy * qz - qw * qx);
        vector[2] = 1 - 2 * (qx * qx + qy * qy);

        return vector;
    }

    private static double calculateLST(double longitude) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);

        if (month <= 2) {
            year -= 1;
            month += 12;
        }

        int a = year / 100;
        int b = 2 - a + (a / 4);
        double jd = Math.floor(365.25 * (year + 4716)) +
                Math.floor(30.6001 * (month + 1)) +
                day + b - 1524.5 +
                hour/24.0 + minute/1440.0 + second/86400.0;

        double t = (jd - 2451545.0) / 36525.0;
        double gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t - t * t * t / 38710000.0;

        gmst = (gmst % 360 + 360) % 360;
        double lst = gmst + longitude;
        lst = (lst % 360 + 360) % 360;

        return lst;
    }
}
