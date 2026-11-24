package dev.skillsieve.core.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class JobApiIT extends IntegrationTest {

  private Cookie owner;

  @BeforeEach
  void signIn() throws Exception {
    owner = loginOwner();
  }

  private JsonNode create(String text) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/jobs"), owner)
                    .content(json.writeValueAsString(Map.of("rawText", text))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void shouldCreateJobWithSavedApplicationAndHistory() throws Exception {
    JsonNode job = create(jobText("Acme-" + UUID.randomUUID()));

    assertThat(job.get("extractionStatus").asText()).isEqualTo("PENDING");
    assertThat(job.at("/application/status").asText()).isEqualTo("SAVED");
    assertThat(job.at("/application/version").asInt()).isZero();

    mvc.perform(
            get("/api/applications/" + job.at("/application/id").asText() + "/history")
                .cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].toStatus").value("SAVED"));
  }

  @Test
  void shouldReturn409WithExistingIdWhenTextHashExists() throws Exception {
    String text = jobText("Dup-" + UUID.randomUUID());
    JsonNode first = create(text);
    String reformatted = "  " + text.toUpperCase().replace(" ", "\n  ") + "  ";

    mvc.perform(
            ajax(post("/api/jobs"), owner)
                .content(json.writeValueAsString(Map.of("rawText", reformatted))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.existingId").value(first.get("id").asText()));
  }

  @Test
  void shouldReturn422WhenTextIsTooShort() throws Exception {
    mvc.perform(ajax(post("/api/jobs"), owner).content("{\"rawText\":\"too short\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("rawText"));
  }

  @Test
  void shouldMoveCardAndRejectStaleVersion() throws Exception {
    JsonNode job = create(jobText("Move-" + UUID.randomUUID()));
    String appUrl = "/api/applications/" + job.at("/application/id").asText();

    mvc.perform(ajax(patch(appUrl), owner).content("{\"status\":\"APPLIED\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPLIED"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.appliedAt").isNotEmpty());

    mvc.perform(ajax(patch(appUrl), owner).content("{\"status\":\"INTERVIEW\",\"version\":0}"))
        .andExpect(status().isConflict());

    mvc.perform(get(appUrl + "/history").cookie(owner))
        .andExpect(jsonPath("$[0].fromStatus").value("SAVED"))
        .andExpect(jsonPath("$[0].toStatus").value("APPLIED"));
  }

  @Test
  void shouldClearNextActionWhenNullIsSent() throws Exception {
    JsonNode job = create(jobText("Date-" + UUID.randomUUID()));
    String appUrl = "/api/applications/" + job.at("/application/id").asText();

    mvc.perform(
            ajax(patch(appUrl), owner).content("{\"nextActionAt\":\"2026-12-01\",\"version\":0}"))
        .andExpect(jsonPath("$.nextActionAt").value("2026-12-01"));
    mvc.perform(ajax(patch(appUrl), owner).content("{\"notes\":\"call back\",\"version\":1}"))
        .andExpect(jsonPath("$.nextActionAt").value("2026-12-01"));
    mvc.perform(ajax(patch(appUrl), owner).content("{\"nextActionAt\":null,\"version\":2}"))
        .andExpect(jsonPath("$.nextActionAt").isEmpty())
        .andExpect(jsonPath("$.notes").value("call back"));
  }

  @Test
  void shouldListEditFilterAndDeleteJobs() throws Exception {
    String company = "Filterco" + UUID.randomUUID().toString().substring(0, 8);
    JsonNode job = create(jobText(company));
    String id = job.get("id").asText();

    mvc.perform(
            ajax(patch("/api/jobs/" + id), owner)
                .content(json.writeValueAsString(Map.of("company", company, "title", "Engineer"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").value(company));

    mvc.perform(get("/api/jobs").param("q", company.toLowerCase()).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id))
        .andExpect(jsonPath("$.content[0].status").value("SAVED"));

    mvc.perform(get("/api/jobs").param("status", "OFFER").param("q", company).cookie(owner))
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(ajax(post("/api/jobs/" + id + "/extract"), owner)).andExpect(status().isAccepted());

    mvc.perform(ajax(delete("/api/jobs/" + id), owner)).andExpect(status().isNoContent());
    mvc.perform(get("/api/jobs/" + id).cookie(owner)).andExpect(status().isNotFound());
  }

  @Test
  void shouldRequireLoginAndAjaxHeader() throws Exception {
    mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/jobs")
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rawText", jobText("NoHeader")))))
        .andExpect(status().isForbidden());
  }
}
