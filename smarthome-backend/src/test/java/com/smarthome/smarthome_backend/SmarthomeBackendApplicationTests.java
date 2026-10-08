package com.smarthome.smarthome_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.smarthome_backend.entity.User;
import com.smarthome.smarthome_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smarthome;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-signing-secret-with-more-than-32-characters"
})
@AutoConfigureMockMvc
class SmarthomeBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
	}

	@Test
	void registrationReturnsTokenAndAllowsAccessToProtectedApi() throws Exception {
        String email = uniqueEmail();
	    var response = mockMvc.perform(post("/api/auth/register")
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(registerJson(email)))
	            .andExpect(status().isCreated())
	            .andExpect(jsonPath("$.user.role").value("USER"))
	            .andReturn();
	    var body = objectMapper.readTree(response.getResponse().getContentAsString());
	    String token = body.get("token").asText();

        var savedUser = users.findByEmailIgnoreCase(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("secure-password", savedUser.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("secure-password", savedUser.getPassword()));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));

	    mockMvc.perform(get("/api/devices").header("Authorization", "Bearer " + token))
	            .andExpect(status().isOk());
	    mockMvc.perform(get("/api/devices"))
	            .andExpect(status().isUnauthorized());
	}

    @Test
    void usersCanOnlyAccessTheirOwnDevicesAndCanTriggerAnomalyAlerts() throws Exception {
        String ownerToken = register(uniqueEmail());
        String otherToken = register(uniqueEmail());

        var deviceResponse = mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Réfrigérateur","type":"Électroménager","location":"Cuisine","active":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long deviceId = objectMapper.readTree(deviceResponse.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Réfrigérateur"))
                .andExpect(jsonPath("$[0].type").value("Électroménager"))
                .andExpect(jsonPath("$[0].location").value("Cuisine"))
                .andExpect(jsonPath("$[0].active").value(true));
        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/devices/{id}", deviceId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intrusion","type":"Autre","location":"Cuisine","active":true}
                                """))
                .andExpect(status().isNotFound());

        for (int index = 0; index < 5; index++) {
            postReading(deviceId, ownerToken, 2.0).andExpect(status().isCreated());
        }
        postReading(deviceId, ownerToken, 3.1).andExpect(status().isCreated());

        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].message").value(
                        org.hamcrest.Matchers.containsString("Réfrigérateur")));
        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deviceListShowsItsDetailsAndUpdatedActiveState() throws Exception {
        String token = register(uniqueEmail());
        var created = mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lampe salon","type":"Éclairage","location":"Salon","active":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long deviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/devices/{id}", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lampe salon","type":"Éclairage","location":"Salon","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lampe salon"))
                .andExpect(jsonPath("$.type").value("Éclairage"))
                .andExpect(jsonPath("$.location").value("Salon"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lampe salon"))
                .andExpect(jsonPath("$[0].type").value("Éclairage"))
                .andExpect(jsonPath("$[0].location").value("Salon"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void consumptionHistoryCanBeFilteredByPeriod() throws Exception {
        String token = register(uniqueEmail());
        var deviceResponse = mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Compteur","type":"Autre","location":"Maison","active":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long deviceId = objectMapper.readTree(deviceResponse.getResponse().getContentAsString())
                .get("id").asLong();
        var recent = java.time.LocalDateTime.now().minusDays(5).withNano(0);
        var older = java.time.LocalDateTime.now().minusDays(45).withNano(0);

        mockMvc.perform(post("/api/devices/{deviceId}/readings", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionKwh":2.5,"recordedAt":"%s"}
                                """.formatted(recent)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/devices/{deviceId}/readings", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionKwh":4.0,"recordedAt":"%s"}
                                """.formatted(older)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/consumption").param("days", "30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].consumptionKwh").value(2.5));
        mockMvc.perform(get("/api/consumption").param("days", "90")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updatingEmailReturnsANewTokenAndInvalidatesTheOldSubject() throws Exception {
        String oldEmail = uniqueEmail();
        String token = register(oldEmail);
        String newEmail = uniqueEmail();

        var response = mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Ada","lastname":"Lovelace","email":"%s"}
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(newEmail))
                .andReturn();
        String refreshedToken = objectMapper.readTree(response.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + refreshedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRequiresCurrentPasswordAndStoresNewPasswordHashed() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        mockMvc.perform(post("/api/profile/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"new-secure-password"}
                                """))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertTrue(
                passwordEncoder.matches("secure-password", users.findByEmailIgnoreCase(email).orElseThrow().getPassword()));

        mockMvc.perform(post("/api/profile/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"secure-password","newPassword":"new-secure-password"}
                                """))
                .andExpect(status().isNoContent());

        User updatedUser = users.findByEmailIgnoreCase(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("new-secure-password", updatedUser.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(
                "new-secure-password", updatedUser.getPassword()));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"new-secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secure-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demotedAdministratorLosesAdminAccessWithoutLoggingInAgain() throws Exception {
        User firstAdmin = saveUser(uniqueEmail(), "ADMIN");
        User secondAdmin = saveUser(uniqueEmail(), "ADMIN");
        String firstToken = login(firstAdmin.getEmail());
        String secondToken = login(secondAdmin.getEmail());

        mockMvc.perform(put("/api/admin/users/{id}/role", secondAdmin.getId())
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions postReading(long deviceId, String token, double kwh)
            throws Exception {
        return mockMvc.perform(post("/api/devices/{deviceId}/readings", deviceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumptionKwh\":" + kwh + "}"));
    }

    private String register(String email) throws Exception {
        var response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email) throws Exception {
        var response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    private String registerJson(String email) {
        return """
                {"firstname":"Ada","lastname":"Lovelace","email":"%s","password":"secure-password"}
                """.formatted(email);
    }

    private User saveUser(String email, String role) {
        var user = new User();
        user.setFirstname("Test");
        user.setLastname("Administrator");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("secure-password"));
        user.setRole(role);
        return users.save(user);
    }

    private String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@example.com";
    }
}
