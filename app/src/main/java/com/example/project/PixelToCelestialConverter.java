package com.example.project;

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

    /**
     * Creates a WCS-based converter for mapping pixels to celestial coordinates
     *
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @param centerCoordinates Celestial coordinates at the center of the image
     * @param fovDegrees Field of view in degrees
     */
    public PixelToCelestialConverter(int imageWidth, int imageHeight,
                                     AstronomicalCalculator.CelestialCoordinates centerCoordinates,
                                     double fovDegrees) {
        // Set reference pixel to center of image
        this.crpix1 = imageWidth / 2.0;
        this.crpix2 = imageHeight / 2.0;

        // Calculate aspect ratio
        this.aspectRatio = (double) imageWidth / imageHeight;

        // Set reference values to the center coordinates (convert RA from hours to degrees)
        this.crval1 = centerCoordinates.rightAscension * 15.0; // Convert hours to degrees
        this.crval2 = centerCoordinates.declination;

        // Calculate pixel scale (degrees per pixel)
        // Use the smaller dimension to ensure the FOV fits within the image
        double scale = fovDegrees / Math.min(imageWidth, imageHeight);

        // Account for device orientation from azimuth
        double deviceRotation = Math.toRadians(centerCoordinates.azimuth);

        // Set up the CD matrix with rotation to account for device orientation
        this.cd1_1 = -scale * Math.cos(deviceRotation);
        this.cd1_2 = scale * Math.sin(deviceRotation);
        this.cd2_1 = -scale * Math.sin(deviceRotation);
        this.cd2_2 = -scale * Math.cos(deviceRotation);
    }

    /**
     * Converts a pixel position to celestial coordinates (RA/Dec)
     *
     * @param x X-coordinate of the pixel
     * @param y Y-coordinate of the pixel
     * @return CelestialCoordinates containing RA and Dec
     */
    public AstronomicalCalculator.CelestialCoordinates pixelToCelestial(double x, double y) {
        // Calculate intermediate world coordinates
        double x_intermediate = cd1_1 * (x - crpix1) + cd1_2 * (y - crpix2);
        double y_intermediate = cd2_1 * (x - crpix1) + cd2_2 * (y - crpix2);

        // For tangent plane projection (TAN)
        double ra_rad = Math.toRadians(crval1);
        double dec_rad = Math.toRadians(crval2);

        // Convert intermediate coordinates to radians
        double x_rad = Math.toRadians(x_intermediate);
        double y_rad = Math.toRadians(y_intermediate);

        // Calculate the projection onto the celestial sphere
        double rho = Math.sqrt(x_rad * x_rad + y_rad * y_rad);

        // Handle the case where the pixel is at the reference point
        if (rho == 0) {
            return new AstronomicalCalculator.CelestialCoordinates(
                    crval1 / 15.0, // Convert back to hours
                    crval2,
                    0, 0); // We don't need Az/Alt here
        }

        double c = Math.atan(rho);
        double sinc = Math.sin(c);
        double cosc = Math.cos(c);

        // Calculate declination
        double dec = Math.asin(cosc * Math.sin(dec_rad) +
                (y_rad * sinc * Math.cos(dec_rad) / rho));

        // Calculate right ascension
        double ra;
        double sdec = Math.sin(dec);
        if (Math.abs(sdec) > 0.9999) {
            // Near the poles
            ra = ra_rad + Math.atan2(x_rad, -y_rad);
        } else {
            // General case
            ra = ra_rad + Math.atan2(x_rad * sinc,
                    rho * Math.cos(dec_rad) * cosc -
                            y_rad * Math.sin(dec_rad) * sinc);
        }

        // Convert to degrees and normalize RA to [0, 360)
        double ra_deg = Math.toDegrees(ra);
        ra_deg = (ra_deg + 360.0) % 360.0;

        // Convert RA from degrees to hours (24 hours = 360 degrees)
        double ra_hours = ra_deg / 15.0;

        return new AstronomicalCalculator.CelestialCoordinates(
                ra_hours,
                Math.toDegrees(dec),
                0, 0); // We don't need Az/Alt here
    }

    /**
     * Converts celestial coordinates to pixel position
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

        // Calculate the standard coordinates (gnomonic projection)
        double sin_c = Math.sqrt(1.0 - cos_c * cos_c);
        double x_standard = Math.cos(dec_rad) * Math.sin(ra_rad - ra0_rad) / cos_c;
        double y_standard = (Math.sin(dec_rad) * Math.cos(dec0_rad) -
                Math.cos(dec_rad) * Math.sin(dec0_rad) * Math.cos(ra_rad - ra0_rad)) / cos_c;

        // Convert to degrees
        double x_intermediate = Math.toDegrees(x_standard);
        double y_intermediate = Math.toDegrees(y_standard);

        // Apply the inverse of the CD matrix to get pixel coordinates
        double det = cd1_1 * cd2_2 - cd1_2 * cd2_1;

        // Handle case where matrix is singular
        if (Math.abs(det) < 1e-10) {
            return new double[] {Double.NaN, Double.NaN};
        }

        double x = crpix1 + (cd2_2 * x_intermediate - cd1_2 * y_intermediate) / det;
        double y = crpix2 + (-cd2_1 * x_intermediate + cd1_1 * y_intermediate) / det;

        return new double[] {x, y};
    }
}
