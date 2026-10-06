package com.smartmed.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {
    @Test
    void allMigrationsApplyOnH2MysqlMode() throws Exception {
        String url = "jdbc:h2:mem:flyway_schema_test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT active FROM medications")) {
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void v3CancelsOnlyPendingDosesForInactiveSchedules() throws Exception {
        String url = "jdbc:h2:mem:flyway_upgrade_test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id, full_name, email, password, role, created_at, updated_at) "
                    + "VALUES (1, 'Patient', 'patient@example.test', 'hash', 'PATIENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO medications (id, patient_id, name, dosage, frequency, start_date, active, created_at, updated_at) "
                    + "VALUES (1, 1, 'Medicine', '1 tablet', 'daily', CURRENT_DATE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO medication_schedules (id, medication_id, frequency, time_of_day, start_date, active, created_at, updated_at) "
                    + "VALUES (1, 1, 'ONCE_DAILY', '08:00:00', CURRENT_DATE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO dose_records (id, schedule_id, scheduled_date, scheduled_time, status, created_at, updated_at) "
                    + "VALUES (1, 1, CURRENT_DATE, '08:00:00', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO dose_records (id, schedule_id, scheduled_date, scheduled_time, status, created_at, updated_at) "
                    + "VALUES (2, 1, CURRENT_DATE, '12:00:00', 'TAKEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        assertThat(Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .load().migrate().migrationsExecuted).isEqualTo(2);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT status FROM dose_records ORDER BY id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("CANCELLED");
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("TAKEN");
        }
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> statement.executeUpdate("DELETE FROM medications WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
