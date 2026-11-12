package com.example.cameraroute.geo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Converts supported route input coordinates to the WGS84 datum used by camera.geom.
 */
public final class CoordinateConverter {

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordinateConverter() {
    }

    public static Point toWgs84(double longitude, double latitude, CoordinateType type) {
        if (CoordinateType.WGS84 == type) {
            return new Point(longitude, latitude);
        }

        if (CoordinateType.GCJ_02 != type) {
            throw new IllegalArgumentException("type 仅支持 WGS84 或 GCJ-02");
        }

        if (outsideChina(longitude, latitude)) {
            return new Point(longitude, latitude);
        }

        double lon = longitude;
        double lat = latitude;

        for (int i = 0; i < 8; i++) {
            Point shifted = wgs84ToGcj02(lon, lat);
            double dLon = shifted.longitude - longitude;
            double dLat = shifted.latitude - latitude;

            lon -= dLon;
            lat -= dLat;

            if (Math.abs(dLon) < 1e-7 && Math.abs(dLat) < 1e-7) {
                break;
            }
        }

        return new Point(lon, lat);
    }

    private static Point wgs84ToGcj02(double lon, double lat) {
        if (outsideChina(lon, lat)) {
            return new Point(lon, lat);
        }

        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);

        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);

        return new Point(lon + dLon, lat + dLat);
    }

    private static boolean outsideChina(double lon, double lat) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100 + 2 * x + 3 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));

        ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3;
        ret += (20 * Math.sin(y * PI) + 40 * Math.sin(y / 3 * PI)) * 2 / 3;
        ret += (160 * Math.sin(y / 12 * PI) + 320 * Math.sin(y * PI / 30)) * 2 / 3;

        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300 + x + 2 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));

        ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3;
        ret += (20 * Math.sin(x * PI) + 40 * Math.sin(x / 3 * PI)) * 2 / 3;
        ret += (150 * Math.sin(x / 12 * PI) + 300 * Math.sin(x / 30 * PI)) * 2 / 3;

        return ret;
    }

    @Getter
    @AllArgsConstructor
    public static final class Point {

        public final double longitude;
        public final double latitude;
    }
}
