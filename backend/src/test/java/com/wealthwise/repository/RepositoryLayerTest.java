package com.wealthwise.repository;

import com.wealthwise.model.Transaction;
import com.wealthwise.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — Repository Layer Tests (Data JPA Slice)
 *  Test Suite ID : TS-REPO-001
 *
 *  Uses H2 in-memory database. Tests real JPQL queries and Spring Data
 *  derived finder methods without hitting PostgreSQL.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Disable PanCardEncryptor — it needs app.jwt.secret; we don't need encryption in repo tests
    "app.jwt.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "app.jwt.expiration-ms=86400000"
})
@DisplayName("Repository Layer Tests (H2 In-Memory)")
class RepositoryLayerTest {

    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository        userRepository;

    private Long userId1 = 101L;
    private Long userId2 = 202L;

    // ── Test data builders ────────────────────────────────────────────────────
    private Transaction txn(Long userId, String amfiCode, String type, LocalDate date, BigDecimal amount) {
        Transaction t = new Transaction();
        t.setTransactionRef("REF-" + System.nanoTime());
        t.setUserId(userId);
        t.setSchemeAmfiCode(amfiCode);
        t.setTransactionType(type);
        t.setTransactionDate(date);
        t.setAmount(amount);
        t.setUnits(BigDecimal.ONE);
        t.setNav(amount);
        return t;
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-REPO-001..005  UserRepository
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-REPO-001..005 | UserRepository")
    class UserRepositoryTests {

        @Test
        @DisplayName("TC-REPO-001 | findByEmail returns user when email exists")
        void findByEmail_found() {
            User u = new User();
            u.setFullName("Alice");
            u.setEmail("alice@test.com");
            u.setPassword("hashed");
            userRepository.save(u);

            Optional<User> found = userRepository.findByEmail("alice@test.com");
            assertThat(found).isPresent();
            assertThat(found.get().getFullName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("TC-REPO-002 | findByEmail returns empty Optional for unknown email")
        void findByEmail_notFound() {
            Optional<User> found = userRepository.findByEmail("ghost@test.com");
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("TC-REPO-003 | existsByEmail returns true for registered email")
        void existsByEmail_true() {
            User u = new User();
            u.setFullName("Bob");
            u.setEmail("bob@test.com");
            u.setPassword("hashed");
            userRepository.save(u);

            assertThat(userRepository.existsByEmail("bob@test.com")).isTrue();
        }

        @Test
        @DisplayName("TC-REPO-004 | existsByEmail returns false for unregistered email")
        void existsByEmail_false() {
            assertThat(userRepository.existsByEmail("nobody@test.com")).isFalse();
        }

        @Test
        @DisplayName("TC-REPO-005 | Default riskProfile is MODERATE on new user")
        void defaultRiskProfile_isModerate() {
            User u = new User();
            u.setFullName("Carol");
            u.setEmail("carol@test.com");
            u.setPassword("hashed");
            User saved = userRepository.save(u);

            assertThat(saved.getRiskProfile()).isEqualTo("MODERATE");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-REPO-006..015  TransactionRepository
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-REPO-006..015 | TransactionRepository")
    class TransactionRepositoryTests {

        @Test
        @DisplayName("TC-REPO-006 | findByUserId returns only that user's transactions")
        void findByUserId_isolation() {
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(5000)));
            transactionRepository.save(txn(userId2, "100033", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(3000)));

            List<Transaction> result = transactionRepository
                .findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(userId1);
        }

        @Test
        @DisplayName("TC-REPO-007 | findByUserId returns empty list for user with no transactions")
        void findByUserId_emptyForNewUser() {
            List<Transaction> result = transactionRepository
                .findByUserIdOrderByTransactionDateDescCreatedAtDesc(999L);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-REPO-008 | Pagination returns correct page size")
        void findByUserId_paginationWorks() {
            for (int i = 0; i < 5; i++) {
                transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP",
                    LocalDate.now().minusDays(i), BigDecimal.valueOf(1000)));
            }

            Page<Transaction> page = transactionRepository
                .findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId1, PageRequest.of(0, 3));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("TC-REPO-009 | findDistinctSchemesByUserId returns unique AMFI codes")
        void findDistinctSchemes_unique() {
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(5000)));
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", LocalDate.now().minusDays(1), BigDecimal.valueOf(5000)));
            transactionRepository.save(txn(userId1, "100033", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(3000)));

            List<String> codes = transactionRepository.findDistinctSchemesByUserId(userId1);
            assertThat(codes).hasSize(2).containsExactlyInAnyOrder("119598", "100033");
        }

        @Test
        @DisplayName("TC-REPO-010 | findByTransactionRef returns correct transaction")
        void findByTransactionRef_found() {
            Transaction t = txn(userId1, "119598", "PURCHASE_LUMPSUM", LocalDate.now(), BigDecimal.valueOf(100000));
            t.setTransactionRef("UNIQUE-REF-001");
            transactionRepository.save(t);

            Optional<Transaction> found = transactionRepository.findByTransactionRef("UNIQUE-REF-001");
            assertThat(found).isPresent();
            assertThat(found.get().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        }

        @Test
        @DisplayName("TC-REPO-011 | findByTransactionRef returns empty for unknown ref")
        void findByTransactionRef_notFound() {
            Optional<Transaction> found = transactionRepository.findByTransactionRef("NON-EXISTENT");
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("TC-REPO-012 | findByUserIdAndSchemeAmfiCode returns only matching scheme")
        void findByUserAndScheme_filtered() {
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(5000)));
            transactionRepository.save(txn(userId1, "100033", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(3000)));

            List<Transaction> result = transactionRepository
                .findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(userId1, "119598");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSchemeAmfiCode()).isEqualTo("119598");
        }

        @Test
        @DisplayName("TC-REPO-013 | findByUserIdAndTransactionDateBetween returns date-range matches")
        void findByDateRange() {
            LocalDate base = LocalDate.of(2025, 1, 15);
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", base.minusDays(10), BigDecimal.valueOf(1000)));
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", base, BigDecimal.valueOf(2000)));
            transactionRepository.save(txn(userId1, "119598", "PURCHASE_SIP", base.plusDays(10), BigDecimal.valueOf(3000)));

            List<Transaction> result = transactionRepository.findByUserIdAndTransactionDateBetween(
                userId1, base.minusDays(1), base.plusDays(1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }

        @Test
        @DisplayName("TC-REPO-014 | bulkUpdateSchemeAmfiCode updates all matching rows")
        void bulkUpdateSchemeAmfiCode() {
            transactionRepository.save(txn(userId1, "WW_ISIN_ABC", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(1000)));
            transactionRepository.save(txn(userId1, "WW_ISIN_ABC", "PURCHASE_SIP", LocalDate.now().minusDays(1), BigDecimal.valueOf(2000)));

            int updated = transactionRepository.bulkUpdateSchemeAmfiCode(
                "WW_ISIN_ABC", "119598", "Mirae Asset Large Cap Fund");

            assertThat(updated).isEqualTo(2);

            List<Transaction> updated_txns = transactionRepository.findBySchemeAmfiCode("119598");
            assertThat(updated_txns).hasSize(2);
        }

        @Test
        @DisplayName("TC-REPO-015 | Default source is MANUAL on saved transaction")
        void defaultSource_isManual() {
            Transaction t = txn(userId1, "119598", "PURCHASE_SIP", LocalDate.now(), BigDecimal.valueOf(5000));
            Transaction saved = transactionRepository.save(t);
            assertThat(saved.getSource()).isEqualTo("MANUAL");
        }
    }
}
