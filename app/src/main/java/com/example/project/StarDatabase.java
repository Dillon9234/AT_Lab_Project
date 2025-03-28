package com.example.project;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class StarDatabase {
    private static final List<Star> stars = new ArrayList<>();

    // Static initialization block to populate the database
    static {
        // Add some well-known bright stars
        // Format: name, RA (hours), Dec (degrees), magnitude
        stars.add(new Star("Sirius", 6.7525, -16.7161, -1.46));
        stars.add(new Star("Canopus", 6.3992, -52.6956, -0.74));
        stars.add(new Star("Alpha Centauri", 14.6577, -60.8332, -0.27));
        stars.add(new Star("Arcturus", 14.2612, 19.1824, -0.05));
        stars.add(new Star("Vega", 18.6156, 38.7836, 0.03));
        stars.add(new Star("Capella", 5.2775, 45.9994, 0.08));
        stars.add(new Star("Rigel", 5.2422, -8.2017, 0.13));
        stars.add(new Star("Procyon", 7.6551, 5.2250, 0.34));
        stars.add(new Star("Achernar", 1.6285, -57.2367, 0.46));
        stars.add(new Star("Betelgeuse", 5.9195, 7.4071, 0.50));
        stars.add(new Star("Hadar", 14.0637, -60.3726, 0.61));
        stars.add(new Star("Altair", 19.8465, 8.8683, 0.76));
        stars.add(new Star("Aldebaran", 4.5987, 16.5093, 0.87));
        stars.add(new Star("Spica", 13.4199, -11.1613, 1.04));
        stars.add(new Star("Antares", 16.4901, -26.4319, 1.09));
        stars.add(new Star("Pollux", 7.7553, 28.0262, 1.14));
        stars.add(new Star("Fomalhaut", 22.9608, -29.6222, 1.16));
        stars.add(new Star("Deneb", 20.6905, 45.2803, 1.25));
        stars.add(new Star("Regulus", 10.1395, 11.9672, 1.36));
        stars.add(new Star("Castor", 7.5768, 31.8882, 1.58));
    }

    /**
     * Finds stars within a given field of view
     *
     * @param centerRA Right ascension of the center (hours)
     * @param centerDec Declination of the center (degrees)
     * @param fovWidth Field of view width (degrees)
     * @param fovHeight Field of view height (degrees)
     * @param maxMagnitude Maximum magnitude to include (higher number = more stars)
     * @return List of stars in the field of view
     */
    public static List<Star> getStarsInFieldOfView(double centerRA, double centerDec,
                                                   double fovWidth, double fovHeight,
                                                   double maxMagnitude) {
        List<Star> visibleStars = new ArrayList<>();

        // Convert FOV from degrees to hours for RA
        double fovWidthHours = fovWidth / 15.0;

        // Define search boundaries
        double minRA = centerRA - (fovWidthHours / 2.0);
        double maxRA = centerRA + (fovWidthHours / 2.0);
        double minDec = centerDec - (fovHeight / 2.0);
        double maxDec = centerDec + (fovHeight / 2.0);

        // Handle RA wrap around
        boolean raWraps = minRA < 0 || maxRA >= 24;

        for (Star star : stars) {
            // Check magnitude first (brightness filter)
            if (star.magnitude > maxMagnitude) {
                continue;
            }

            // Check if star is in declination range
            if (star.declination < minDec || star.declination > maxDec) {
                continue;
            }

            // Check if star is in right ascension range
            boolean inRARange;
            if (raWraps) {
                // Handle case where field of view crosses 0/24h boundary
                minRA = (minRA + 24) % 24;
                maxRA = (maxRA + 24) % 24;

                if (minRA > maxRA) {
                    inRARange = (star.rightAscension >= minRA || star.rightAscension <= maxRA);
                } else {
                    inRARange = (star.rightAscension >= minRA && star.rightAscension <= maxRA);
                }
            } else {
                inRARange = (star.rightAscension >= minRA && star.rightAscension <= maxRA);
            }

            if (inRARange) {
                visibleStars.add(star);
            }
        }

        return visibleStars;
    }

    /**
     * Star class to hold celestial object data
     */
    public static class Star {
        public final String name;
        public final double rightAscension; // in hours
        public final double declination;    // in degrees
        public final double magnitude;      // apparent magnitude

        public Star(String name, double rightAscension, double declination, double magnitude) {
            this.name = name;
            this.rightAscension = rightAscension;
            this.declination = declination;
            this.magnitude = magnitude;
        }
    }
}
