package com.joaosousa.atlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The local profile's properties file held a
 * database password and two API keys, and Maven and Docker both copy the working tree
 * rather than the git index, so gitignoring it never kept it out of the jar or the image.
 * The file is gone; this fails if it ever returns to the build output.
 */
class SecretsNotBundledTest {

    @Test
    void localPropertiesAreNotOnTheClasspath() {
        assertNull(getClass().getResource("/application-local.properties"));
    }
}
