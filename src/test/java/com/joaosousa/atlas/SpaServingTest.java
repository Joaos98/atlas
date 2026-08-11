package com.joaosousa.atlas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SpaServingTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("spa-serving.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/spa-serving.db");
    }

    @Test
    void spaRouteIsForwardedToIndexHtml() throws Exception {
        mockMvc.perform(get("/workouts"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void staticAssetIsServedAsFileNotForwarded() throws Exception {
        mockMvc.perform(get("/spa-test-asset.js"))
                .andExpect(status().isOk())
                .andExpect(content().string("/* spa test asset */\n"));
    }

    @Test
    void unknownApiPathIsNotFoundNotIndexHtml() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound());
    }
}
