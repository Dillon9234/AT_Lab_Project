package com.example.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StarDatabase {
    private static final List<Star> stars = new ArrayList<>();
    private static boolean isDataLoaded = false;
    private static int totalLineCount = 0;

    // Load stars from the JSON file
    static {
        loadStarsFromFile("stars.json");
    }

    /**
     * Loads star data from the JSON file
     *
     * @param filename The name of the file to read
     */
    private static void loadStarsFromFile(String filename) {
        try {
            // Read the JSON file from assets
            StringBuilder jsonString = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    MyApplication.getContext().getAssets().open(filename)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    totalLineCount++;
                    jsonString.append(line);
                }
            }

            // Parse JSON using Gson
            Gson gson = new Gson();
            Type starListType = new TypeToken<List<StarJson>>(){}.getType();
            List<StarJson> starJsonList = gson.fromJson(jsonString.toString(), starListType);

            // Convert StarJson objects to Star objects
            for (StarJson starJson : starJsonList) {
                Star star = new Star(
                        starJson.Name,
                        starJson.Ra,  // RA is already in hours in the JSON
                        starJson.Dec,
                        0  // Magnitude not provided in the file
                );
                stars.add(star);
            }

            isDataLoaded = true;
            System.out.println("Read " + totalLineCount + " lines, loaded " + stars.size() + " stars.");
        } catch (IOException e) {
            System.err.println("Error reading star data file: " + e.getMessage());
            e.printStackTrace();
            isDataLoaded = false;
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            e.printStackTrace();
            isDataLoaded = false;
        }
    }

    /**
     * Helper class for JSON deserialization
     */
    private static class StarJson {
        public double Ra;
        public double Dec;
        public String Name;
    }

    public static int getLoadedStarCount() {
        return stars.size();
    }

    public static boolean isStarDataLoaded() {
        return isDataLoaded;
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
        // Existing implementation remains the same
        List<Star> visibleStars = new ArrayList<>();

        double fovWidthHours = fovWidth / 15.0;
        double minRA = centerRA - (fovWidthHours / 2.0);
        double maxRA = centerRA + (fovWidthHours / 2.0);
        double minDec = centerDec - (fovHeight / 2.0);
        double maxDec = centerDec + (fovHeight / 2.0);

        boolean raWraps = minRA < 0 || maxRA >= 24;

        for (Star star : stars) {
            if (star.declination < minDec || star.declination > maxDec) {
                continue;
            }

            boolean inRARange;
            if (raWraps) {
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
