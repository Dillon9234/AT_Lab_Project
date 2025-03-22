package com.example.project;

/**
 * Utility class for astronomical calculations and formatting
 */
public class AstronomyUtils {

    /**
     * Format Right Ascension from decimal hours to HH:MM:SS format
     * @param raHours Right Ascension in decimal hours (0-24)
     * @return Formatted string in HH:MM:SS format
     */
    public static String formatRA(double raHours) {
        // Ensure RA is in range 0-24 hours
        raHours = ((raHours % 24) + 24) % 24;

        int hours = (int) raHours;
        double decimalMinutes = (raHours - hours) * 60;
        int minutes = (int) decimalMinutes;
        double decimalSeconds = (decimalMinutes - minutes) * 60;
        int seconds = (int) Math.round(decimalSeconds);

        // Handle case where seconds round up to 60
        if (seconds == 60) {
            seconds = 0;
            minutes++;
            if (minutes == 60) {
                minutes = 0;
                hours++;
                if (hours == 24) {
                    hours = 0;
                }
            }
        }

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    /**
     * Format Declination from decimal degrees to DD:MM:SS format
     * @param decDegrees Declination in decimal degrees (-90 to +90)
     * @return Formatted string in DD:MM:SS format
     */
    public static String formatDec(double decDegrees) {
        // Ensure Dec is in range -90 to +90 degrees
        decDegrees = Math.max(-90, Math.min(90, decDegrees));

        String sign = decDegrees < 0 ? "-" : "+";
        double absDec = Math.abs(decDegrees);

        int degrees = (int) absDec;
        double decimalMinutes = (absDec - degrees) * 60;
        int minutes = (int) decimalMinutes;
        double decimalSeconds = (decimalMinutes - minutes) * 60;
        int seconds = (int) Math.round(decimalSeconds);

        // Handle case where seconds round up to 60
        if (seconds == 60) {
            seconds = 0;
            minutes++;
            if (minutes == 60) {
                minutes = 0;
                degrees++;
                // Check if we hit 90 degrees
                if (degrees > 90) {
                    degrees = 90;
                    minutes = 0;
                    seconds = 0;
                }
            }
        }

        return String.format("%s%02d° %02d' %02d\"", sign, degrees, minutes, seconds);
    }

    /**
     * Convert decimal degrees to radians
     * @param degrees Angle in degrees
     * @return Angle in radians
     */
    public static double degreesToRadians(double degrees) {
        return degrees * (Math.PI / 180.0);
    }

    /**
     * Convert radians to decimal degrees
     * @param radians Angle in radians
     * @return Angle in degrees
     */
    public static double radiansToDegrees(double radians) {
        return radians * (180.0 / Math.PI);
    }
}