package com.example.cameraroute.geo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Supported input coordinate reference systems. */
public enum CoordinateType {
    WGS84("WGS84"),
    GCJ_02("GCJ-02");

    private final String value;

    CoordinateType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CoordinateType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("WGS84".equals(normalized)) {
            return WGS84;
        }
        if ("GCJ-02".equals(normalized) || "GCJ_02".equals(normalized)
                || "GCJ02".equals(normalized)) {
            return GCJ_02;
        }
        throw new IllegalArgumentException("type 仅支持 WGS84 或 GCJ-02");
    }
}
