package com.example.cameraroute.dto;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResultTest {

    @Test
    void normalizesNullDataToEmptyList() {
        PageResult<String> result = PageResult.of(0L, 1, 20, null);

        assertNotNull(result.getData());
        assertEquals(Collections.emptyList(), result.getData());
        assertEquals(0, result.getCurrentCount());
        assertEquals(0L, result.getTotalPages());
    }

    @Test
    void calculatesTotalPagesWithoutAdditionOverflow() {
        PageResult<String> result = PageResult.of(Long.MAX_VALUE, 1, 2, Collections.emptyList());

        assertEquals(Long.MAX_VALUE / 2 + 1, result.getTotalPages());
    }

    @Test
    void rejectsInvalidPageMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> PageResult.of(-1L, 1, 20, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> PageResult.of(0L, 0, 20, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> PageResult.of(0L, 1, 0, Collections.emptyList()));
    }
}
