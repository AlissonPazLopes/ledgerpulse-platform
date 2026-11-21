package dev.ledgerpulse.core.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationsTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyV1ThroughV3AndCreateAllCoreTables() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertThat(appliedVersions).containsExactly("1", "2", "3");

        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT LOWER(table_name)
                FROM information_schema.tables
                WHERE LOWER(table_schema) = 'ledger_core'
                  AND table_type IN ('BASE TABLE', 'TABLE')
                """,
                String.class
        );

        assertThat(tables).contains(
                "users",
                "merchants",
                "accounts",
                "ledger_transactions",
                "ledger_entries",
                "payments",
                "payment_splits",
                "outbox_events"
        );
    }

    @Test
    void shouldEnforceBigintMonetaryColumnsAndAppendOnlyLedgerSchema() {
        assertBigintColumn("accounts", "available_balance_cents");
        assertBigintColumn("accounts", "pending_balance_cents");
        assertBigintColumn("ledger_entries", "amount_cents");
        assertBigintColumn("ledger_entries", "balance_after_cents");
        assertBigintColumn("payments", "amount_cents");
        assertBigintColumn("payments", "platform_fee_cents");
        assertBigintColumn("payments", "net_merchant_cents");
        assertBigintColumn("payment_splits", "amount_cents");

        Integer updatedAtInLedgerTx = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'ledger_core'
                  AND LOWER(table_name) = 'ledger_transactions'
                  AND LOWER(column_name) = 'updated_at'
                """,
                Integer.class
        );
        Integer updatedAtInLedgerEntries = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'ledger_core'
                  AND LOWER(table_name) = 'ledger_entries'
                  AND LOWER(column_name) = 'updated_at'
                """,
                Integer.class
        );

        assertThat(updatedAtInLedgerTx).isZero();
        assertThat(updatedAtInLedgerEntries).isZero();
    }

    @Test
    @Transactional
    void shouldRejectZeroOrNegativeAmountCentsInLedgerEntries() {
        UUID accountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO ledger_core.accounts (id, merchant_id, account_type, holder_name, currency)
                VALUES (?, NULL, 'ESCROW', 'Schema Test Escrow', 'BRL')
                """,
                accountId
        );
        jdbcTemplate.update(
                """
                INSERT INTO ledger_core.ledger_transactions (id, reference_type, reference_id, description, currency, correlation_id)
                VALUES (?, 'PAYMENT_SETTLEMENT', ?, 'Schema check', 'BRL', 'corr-schema-test')
                """,
                txId,
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO ledger_core.ledger_entries (id, ledger_transaction_id, account_id, direction, amount_cents, balance_after_cents)
                VALUES (?, ?, ?, 'DEBIT', 0, 0)
                """,
                UUID.randomUUID(),
                txId,
                accountId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO ledger_core.ledger_entries (id, ledger_transaction_id, account_id, direction, amount_cents, balance_after_cents)
                VALUES (?, ?, ?, 'CREDIT', -500, -500)
                """,
                UUID.randomUUID(),
                txId,
                accountId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertBigintColumn(String tableName, String columnName) {
        String dataType = jdbcTemplate.queryForObject(
                """
                SELECT UPPER(data_type)
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'ledger_core'
                  AND LOWER(table_name) = ?
                  AND LOWER(column_name) = ?
                """,
                String.class,
                tableName,
                columnName
        );
        assertThat(dataType).isEqualTo("BIGINT");
    }
}
