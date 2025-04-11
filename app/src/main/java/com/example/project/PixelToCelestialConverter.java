package com.example.project;

import java.lang.Math;

public class PixelToCelestialConverter {
    // WCS transformation parameters
    private final double crval1; // RA at reference pixel (degrees)
    private final double crval2; // Dec at reference pixel (degrees)
    private final double crpix1; // X reference pixel
    private final double crpix2; // Y reference pixel
    private final double cd1_1;  // Transformation matrix elements
    private final double cd1_2;
    private final double cd2_1;
    private final double cd2_2;
    private final double aspectRatio; // Image aspect ratio
    private final double fovDegrees; // Field of view in degrees

    /**
     * Creates a WCS-based converter for mapping pixels to celestial coordinates
     * Optimized for a camera with 66-degree FOV
     *
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @param centerCoordinates Celestial coordinates at the center of the image
     */
    public PixelToCelestialConverter(int imageWidth, int imageHeight,
                                     AstronomicalCalculator.CelestialCoordinates centerCoordinates,
                                     double fovDegrees) {
        this.fovDegrees = fovDegrees;

        // Set reference pixel to center of image
        this.crpix1 = imageWidth / 2.0;
        this.crpix2 = imageHeight / 2.0;

        // Calculate aspect ratio
        this.aspectRatio = (double) imageWidth / imageHeight;

        // Set reference values to the center coordinates (convert RA from hours to degrees)
        this.crval1 = centerCoordinates.rightAscension * 15.0; // Convert hours to degrees
        this.crval2 = centerCoordinates.declination;

        // Calculate pixel scale (degrees per pixel)
        // For a 66-degree FOV, we need to adjust the scale calculation
        // Use the smaller dimension to ensure the FOV fits within the image
        double scale = this.fovDegrees / Math.min(imageWidth, imageHeight);

        // Account for device orientation from azimuth
        double deviceRotation = Math.toRadians(centerCoordinates.azimuth);

        // Set up the CD matrix with rotation to account for device orientation
        this.cd1_1 = -scale * Math.cos(deviceRotation);
        this.cd1_2 = scale * Math.sin(deviceRotation);
        this.cd2_1 = -scale * Math.sin(deviceRotation);
        this.cd2_2 = -scale * Math.cos(deviceRotation);
    }

    /**
     * Converts celestial coordinates to pixel position
     * Optimized for wider FOV (66 degrees)
     *
     * @param ra Right Ascension in hours
     * @param dec Declination in degrees
     * @return double[] containing [x, y] pixel coordinates
     */
    public double[] celestialToPixel(double ra, double dec) {
        // Convert RA from hours to degrees
        double ra_deg = ra * 15.0;

        // Convert to radians
        double ra_rad = Math.toRadians(ra_deg);
        double dec_rad = Math.toRadians(dec);
        double ra0_rad = Math.toRadians(crval1);
        double dec0_rad = Math.toRadians(crval2);

        // Calculate angular separation between the points
        double cos_c = Math.sin(dec_rad) * Math.sin(dec0_rad) +
                Math.cos(dec_rad) * Math.cos(dec0_rad) * Math.cos(ra_rad - ra0_rad);

        // If the point is on the opposite side of the celestial sphere or too far away
        if (cos_c <= 0) {
            return new double[] {Double.NaN, Double.NaN};
        }

        // For wider FOV (66 degrees), stereographic projection works better than gnomonic
        // Stereographic projection formula
        double x_standard = 2 * Math.cos(dec_rad) * Math.sin(ra_rad - ra0_rad) /
                (1 + Math.sin(dec_rad) * Math.sin(dec0_rad) + Math.cos(dec_rad) * Math.cos(dec0_rad) * Math.cos(ra_rad - ra0_rad));
        double y_standard = 2 * (Math.sin(dec_rad) * Math.cos(dec0_rad) -
                Math.cos(dec_rad) * Math.sin(dec0_rad) * Math.cos(ra_rad - ra0_rad)) /
                (1 + Math.sin(dec_rad) * Math.sin(dec0_rad) + Math.cos(dec_rad) * Math.cos(dec0_rad) * Math.cos(ra_rad - ra0_rad));

        // Convert to degrees
        double x_intermediate = Math.toDegrees(x_standard);
        double y_intermediate = Math.toDegrees(y_standard);

        // Apply the inverse of the CD matrix to get pixel coordinates
        double det = cd1_1 * cd2_2 - cd1_2 * cd2_1;

        // Handle case where matrix is singular
        if (Math.abs(det) < 1e-10) {
            return new double[] {Double.NaN, Double.NaN};
        }

        // Calculate final pixel coordinates
        double x = crpix1 + (cd2_2 * x_intermediate - cd1_2 * y_intermediate) / det;
        double y = crpix2 + (-cd2_1 * x_intermediate + cd1_1 * y_intermediate) / det;

        // Apply FOV correction factor for wide-angle cameras
        // This helps compensate for lens distortion in wide FOV cameras
        double distanceFromCenter = Math.sqrt(Math.pow(x - crpix1, 2) + Math.pow(y - crpix2, 2));
        double maxDistance = Math.min(crpix1, crpix2);

        // Only apply correction if point is not at center
        if (distanceFromCenter > 0) {
            // Correction increases with distance from center
            double correctionFactor = 1.0 + 0.1 * Math.pow(distanceFromCenter / maxDistance, 2);
            double angle = Math.atan2(y - crpix2, x - crpix1);

            // Apply correction
            x = crpix1 + (distanceFromCenter * correctionFactor) * Math.cos(angle);
            y = crpix2 + (distanceFromCenter * correctionFactor) * Math.sin(angle);
        }

        return new double[] {x, y};
    }

    /**
     * Converts pixel position to celestial coordinates
     * Added for completeness and testing
     *
     * @param x X pixel coordinate
     * @param y Y pixel coordinate
     * @return double[] containing [ra (hours), dec (degrees)]
     */
    public double[] pixelToCelestial(double x, double y) {
        // Calculate intermediate coordinates using the CD matrix
        double det = cd1_1 * cd2_2 - cd1_2 * cd2_1;

        // Handle case where matrix is singular
        if (Math.abs(det) < 1e-10) {
            return new double[] {Double.NaN, Double.NaN};
        }

        double dx = x - crpix1;
        double dy = y - crpix2;

        double x_intermediate = (cd1_1 * dx + cd1_2 * dy) / det;
        double y_intermediate = (cd2_1 * dx + cd2_2 * dy) / det;

        // Convert to radians
        double x_rad = Math.toRadians(x_intermediate);
        double y_rad = Math.toRadians(y_intermediate);

        // Convert from stereographic projection to celestial coordinates
        double ra0_rad = Math.toRadians(crval1);
        double dec0_rad = Math.toRadians(crval2);

        double rho = Math.sqrt(x_rad * x_rad + y_rad * y_rad);
        double c = 2 * Math.atan(rho / 2);

        // Handle case where rho is zero
        if (Math.abs(rho) < 1e-10) {
            return new double[] {crval1 / 15.0, crval2}; // Return center coordinates
        }

        double sin_c = Math.sin(c);
        double cos_c = Math.cos(c);

        double dec_rad = Math.asin(cos_c * Math.sin(dec0_rad) +
                (y_rad * sin_c * Math.cos(dec0_rad) / rho));

        double ra_rad = ra0_rad + Math.atan2(x_rad * sin_c,
                rho * Math.cos(dec0_rad) * cos_c - y_rad * sin_c * Math.sin(dec0_rad));

        // Convert to hours and degrees
        double ra_hours = Math.toDegrees(ra_rad) / 15.0;
        double dec_degrees = Math.toDegrees(dec_rad);

        // Normalize RA to [0, 24) hours
        ra_hours = (ra_hours % 24 + 24) % 24;

        return new double[] {ra_hours, dec_degrees};
    }
}
