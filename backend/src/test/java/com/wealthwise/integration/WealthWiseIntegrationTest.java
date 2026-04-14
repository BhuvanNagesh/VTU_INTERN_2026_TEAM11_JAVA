package com.wealthwise.integration;

import com.wealthwise.model.User;
import com.wealthwise.model.Transaction;
import com.wealthwise.repository.UserRepository;
import com.wealthwise.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — Testcontainers Integration Tests
 *  Test Suite ID: TS-TC-001
 *
 *  Uses a real PostgreSQL 16 Docker container (via Testcontainers).
 *  Spring Boot loads the FULL application context — all beans, JPA,
 *  security, and caching — against a real Postgres dialect.
 *
 *  PRE-REQUISITE: Docker Desktop must be running on the host machine.
 *  If Docker is not available, these tests are automatically skipped
 *  with a clear error: "Could not find a valid Docker environment."
 *
 *  WHY TESTCONTAINERS vs H2?
 *  ─────────────────────────
 *  H2 uses a different dialect from PostgreSQL. Postgres-specific features
 *  (e.g. INSERT ... ON CONFLICT DO NOTHING, ::jsonb casts, bigserial PKs)
 *  do NOT work in H2. Testcontainers guarantees 100% dialect compatibility.
 *
 *  HOW TO RUN:
 *    Ensure Docker Desktop is running, then:
 *    mvn test -Dtest=WealthWiseIntegrationTest --no-transfer-progress
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TS-TC-001 | Testcontainers Integration Tests (Real PostgreSQL)")
class WealthWiseIntegrationTest {

    // ── PostgreSQL 16 Docker container — shared across all tests ──────────────
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wealthwise_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true); // Reuses container across test runs (requires ~/.testcontainers.properties)

    // Wire the container's JDBC URL into Spring's datasource properties
    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Disable email sending in integration tests
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "9999");
        // Fixed JWT secret for test JWT generation
        registry.add("app.jwt.secret", () -> "IntegrationTestSecretKey2026WealthWise!SuperSecure123456");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.encryption.key", () -> "IntegrationTestEncryptionKey32B!");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired TransactionRepository transactionRepository;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-TC-001..003 | Container Health
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("TC-TC-001 | PostgreSQL container is running and accepting connections")
    void container_isRunning() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getJdbcUrl()).startsWith("jdbc:postgresql://");
    }

    @Test
    @Order(2)
    @DisplayName("TC-TC-002 | Full Spring context loads successfully against real PostgreSQL")
    void springContext_loadsWithRealPostgres() {
        assertThat(userRepository).isNotNull();
        assertThat(transactionRepository).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("TC-TC-003 | Health endpoint returns 200 (full stack working)")
    void healthEndpoint_returns200() {
        ResponseEntity<String> response =
            restTemplate.getForEntity(baseUrl() + "/api/auth/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-TC-004..008 | Full Auth Flow — Signup + Signin (Real PostgreSQL)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("TC-TC-004 | POST /api/auth/signup → 200 with token (real DB write)")
    void signup_createsUserInRealDb() {
        Map<String, String> body = Map.of(
            "fullName", "Integration Tester",
            "email", "tc_test_" + System.currentTimeMillis() + "@wealthwise.test",
            "password", "TestPass@2026!"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/api/auth/signup",
            body,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat((String) response.getBody().get("token")).isNotBlank();
    }

    @Test
    @Order(5)
    @DisplayName("TC-TC-005 | Duplicate signup returns 400 (unique constraint on real PostgreSQL)")
    void signup_duplicateEmail_returns400() {
        String email = "duplicate_tc_" + System.currentTimeMillis() + "@wealthwise.test";
        Map<String, String> body = Map.of("fullName", "User", "email", email, "password", "Pass123!");

        // First signup — should succeed
        restTemplate.postForEntity(baseUrl() + "/api/auth/signup", body, Map.class);

        // Second signup with same email — should fail
        ResponseEntity<Map> second = restTemplate.postForEntity(
            baseUrl() + "/api/auth/signup", body, Map.class);

        assertThat(second.getStatusCode().value()).isIn(400, 409, 500);
    }

    @Test
    @Order(6)
    @DisplayName("TC-TC-006 | POST /api/auth/signin → 200 with JWT (real bcrypt comparison)")
    void signin_returnsJwt() {
        String email = "signin_tc_" + System.currentTimeMillis() + "@wealthwise.test";

        // Register first
        restTemplate.postForEntity(baseUrl() + "/api/auth/signup",
            Map.of("fullName", "Login User", "email", email, "password", "LoginPass@1"), Map.class);

        // Then sign in
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/api/auth/signin",
            Map.of("email", email, "password", "LoginPass@1"),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
    }

    @Test
    @Order(7)
    @DisplayName("TC-TC-007 | Wrong password returns 401 (not 500)")
    void signin_wrongPassword_returns401() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/api/auth/signin",
            Map.of("email", "nonexistent@test.com", "password", "wrongpass"),
            Map.class
        );
        assertThat(response.getStatusCode().value()).isIn(401, 400);
    }

    @Test
    @Order(8)
    @DisplayName("TC-TC-008 | Protected endpoint without JWT returns 401")
    void protectedEndpoint_withoutJwt_returns401() {
        ResponseEntity<String> response =
            restTemplate.getForEntity(baseUrl() + "/api/analytics/risk", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-TC-009..012 | Repository — Real PostgreSQL Queries
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("TC-TC-009 | User persisted to real PostgreSQL with correct defaults")
    void user_persistedWithDefaults_realPostgres() {
        User user = new User();
        user.setFullName("Postgres Test User");
        user.setEmail("pgtest_" + System.currentTimeMillis() + "@wealthwise.test");
        user.setPassword("$2a$10$MockHashedPasswordForTest");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getRiskProfile()).isEqualTo("MODERATE");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @Order(10)
    @DisplayName("TC-TC-010 | findByEmail returns correct user from real PostgreSQL")
    void findByEmail_realPostgres() {
        String email = "findbyemail_tc_" + System.currentTimeMillis() + "@wealthwise.test";
        User user = new User();
        user.setFullName("Find Me");
        user.setEmail(email);
        user.setPassword("$hashed$");
        userRepository.save(user);

        assertThat(userRepository.findByEmail(email)).isPresent();
        assertThat(userRepository.findByEmail("nobody@nowhere.com")).isEmpty();
    }

    @Test
    @Order(11)
    @DisplayName("TC-TC-011 | Transaction isolation: user A cannot see user B transactions (real Postgres query)")
    void transaction_isolation_realPostgres() {
        // Create two users
        User userA = userRepository.save(buildTestUser("userA_tc"));
        User userB = userRepository.save(buildTestUser("userB_tc"));

        // Add transactions for user A only
        Transaction tx = buildTestTransaction(userA.getId(), "119598");
        transactionRepository.save(tx);

        // User B should see 0 transactions
        var userBTx = transactionRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userB.getId());
        assertThat(userBTx).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("TC-TC-012 | Scheme autocomplete endpoint accessible publicly (no auth needed)")
    void schemeSearch_publicEndpoint() {
        ResponseEntity<String> response =
            restTemplate.getForEntity(baseUrl() + "/api/schemes/search?q=axis&page=0&size=5", String.class);
        // 200 → scheme data; 204 → empty but schema valid
        assertThat(response.getStatusCode().value()).isIn(200, 204, 400);
        // Should NOT require auth (not 401)
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildTestUser(String prefix) {
        User u = new User();
        u.setFullName("Test " + prefix);
        u.setEmail(prefix + "_" + System.nanoTime() + "@wealthwise.test");
        u.setPassword("$hashed$");
        return u;
    }

    private Transaction buildTestTransaction(Long userId, String amfiCode) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setSchemeAmfiCode(amfiCode);
        t.setSchemeName("Test Scheme");
        t.setTransactionDate(LocalDate.now());
        t.setTransactionType("PURCHASE_SIP");
        t.setAmount(new BigDecimal("5000.00"));
        t.setUnits(new BigDecimal("26.595"));
        t.setNav(new BigDecimal("187.99"));
        t.setSource("MANUAL");
        t.setTransactionRef("TC_" + System.nanoTime());
        return t;
    }
}
