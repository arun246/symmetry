package io.symmetry.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration coverage for identity, scoped settings, the config-request review
 * flow, analytics and the admin surface. Uses the real Postgres so role checks,
 * JPA mappings and SQL aggregates are exercised together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class IdentityAdminApiIntegrationTest {

    private static final String USER = "X-Symmetry-User";
    private static final String PUBLISHER = "publisher@symmetry.local";
    private static final String WORKER = "worker@symmetry.local";
    private static final String ADMIN = "admin@symmetry.local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void meDescribesThePublisherAndTheirOrganization() throws Exception {
        mockMvc.perform(get("/me").header(USER, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PUBLISHER"))
                .andExpect(jsonPath("$.orgSlug").value("acme"))
                .andExpect(jsonPath("$.settings").isMap());
    }

    @Test
    void workersCannotReachPublisherOrAdminSurfaces() throws Exception {
        mockMvc.perform(get("/org/summary").header(USER, WORKER))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/summary").header(USER, WORKER))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGlobalSettingIsVisibleToAWorker() throws Exception {
        mockMvc.perform(put("/admin/config/global/worker.maxConcurrent")
                        .header(USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(7));

        mockMvc.perform(get("/me/settings").header(USER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['worker.maxConcurrent']").value(7));
    }

    @Test
    void orgRequestOnlyAppliesAfterAdminApproval() throws Exception {
        String body = """
                {
                  "key": "evaluation.timeLimitSeconds",
                  "value": 120,
                  "reason": "The resize holdout needs longer runs"
                }
                """;

        String created = mockMvc.perform(post("/org/config-requests")
                        .header(USER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/org/settings/effective").header(USER, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['evaluation.timeLimitSeconds']").doesNotExist());

        mockMvc.perform(post("/admin/config/requests/" + id + "/approve")
                        .header(USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Approved for this org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/org/settings/effective").header(USER, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['evaluation.timeLimitSeconds']").value(120));
    }

    @Test
    void adminCanReadUsersAnalyticsAndTheAuditTrail() throws Exception {
        mockMvc.perform(get("/admin/users").header(USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/admin/analytics/overview").header(USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.workers").value(1))
                .andExpect(jsonPath("$.funnel").isArray());

        mockMvc.perform(put("/admin/config/global/retention.submissionDays")
                        .header(USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": 90}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit").header(USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("config.update"));
    }

    @Test
    void workerSurfaceIsScopedToTheCaller() throws Exception {
        mockMvc.perform(get("/worker/summary").header(USER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptances").value(0));

        mockMvc.perform(get("/worker/acceptances").header(USER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
