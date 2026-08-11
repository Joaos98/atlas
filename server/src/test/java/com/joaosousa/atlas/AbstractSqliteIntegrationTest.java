package com.joaosousa.atlas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaosousa.atlas.seed.FixedClockConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "app.sync.api-key=spike-key",
        "app.insight.api-key=",
        "app.insight.model=gemini-3.5-flash"
})
@Import(FixedClockConfig.class)
public abstract class AbstractSqliteIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${atlas.seed.dir:../ui/src/demo}")
    protected String seedDir;

    protected static void resetDb(String fileName) {
        try {
            Files.createDirectories(Path.of("target", "spike"));
            Files.deleteIfExists(Path.of("target", "spike", fileName));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
