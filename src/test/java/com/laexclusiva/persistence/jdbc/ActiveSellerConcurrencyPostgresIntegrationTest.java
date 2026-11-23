package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Seis contenciones V105 reales; solo laboratorio verificado, nunca base compartida. */
class ActiveSellerConcurrencyPostgresIntegrationTest {
    static Stream<Arguments> scenarios() {
        return Stream.of("assign-assign", "reactivate-reactivate", "assign-reactivate")
                .flatMap(kind -> Stream.of(true, false).map(commit -> Arguments.of(kind, commit)));
    }

    @ParameterizedTest(name = "{0}, commitFirst={1}")
    @MethodSource("scenarios")
    void uniqueIndexSerializesAssignmentsAndReactivations(String kind, boolean commitFirst) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(AccountInstancePostgresIntegrationTest.ENABLE));
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        try (Fixture fixture = new Fixture(config, kind)) {
            Connection first = fixture.open();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            fixture.writersStopped = false;
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger secondPid = new AtomicInteger();
            try {
                first.setAutoCommit(false);
                int firstPid = pid(first);
                fixture.activate(first, fixture.left);
                Future<String> right = executor.submit(() -> {
                    try (Connection second = fixture.open()) {
                        second.setAutoCommit(false);
                        try {
                            secondPid.set(pid(second)); started.countDown();
                            fixture.activate(second, fixture.right);
                            second.rollback();
                            return "updated-and-rolled-back";
                        } catch (SQLException failure) {
                            second.rollback();
                            if (!(failure instanceof PSQLException pg) || !"23505".equals(pg.getSQLState())
                                    || pg.getServerErrorMessage() == null
                                    || !"uq_user_account_active_seller".equals(pg.getServerErrorMessage().getConstraint())) {
                                throw new IllegalStateException("Conflicto SQL inesperado en laboratorio.");
                            }
                            return "23505-active-seller";
                        }
                    }
                });
                assertTrue(started.await(10, TimeUnit.SECONDS));
                assertNotEquals(firstPid, secondPid.get());
                try (Connection observer = fixture.open(); var check = observer.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                    check.setInt(1, firstPid); check.setInt(2, secondPid.get()); check.setQueryTimeout(2);
                    long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
                    boolean blocked = false;
                    while (System.nanoTime() < deadline && !blocked) {
                        try (var rows = check.executeQuery()) { assertTrue(rows.next()); blocked = rows.getBoolean(1); }
                    }
                    assertTrue(blocked, "No se observó contención real entre conexiones independientes");
                }
                if (commitFirst) first.commit(); else first.rollback();
                assertEquals(commitFirst ? "23505-active-seller" : "updated-and-rolled-back", right.get(25, TimeUnit.SECONDS));
                try (Connection observer = fixture.open(); var count = observer.prepareStatement(
                        "SELECT count(*) FROM public.user_account WHERE seller_id=? AND active=TRUE")) {
                    count.setString(1, fixture.seller);
                    try (var rows = count.executeQuery()) { assertTrue(rows.next()); assertEquals(commitFirst ? 1 : 0, rows.getInt(1)); }
                }
            } finally {
                try {
                    try { first.rollback(); } finally { first.close(); }
                } finally {
                    executor.shutdownNow();
                    fixture.writersStopped = executor.awaitTermination(30, TimeUnit.SECONDS);
                    assertTrue(fixture.writersStopped, "Escritor sin terminar: se prohíbe limpiar fixtures");
                }
            }
        }
    }

    private static int pid(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT pg_backend_pid()"); var rows = query.executeQuery()) {
            assertTrue(rows.next()); return rows.getInt(1);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DatabaseConfig config;
        final String seller = "iv_c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        final String leftName = "iv_c_left_" + UUID.randomUUID();
        final String rightName = "iv_c_right_" + UUID.randomUUID();
        UserAccount left, right;
        boolean writersStopped = true;
        Fixture(DatabaseConfig config, String kind) {
            this.config = config;
            JdbcConnectionProvider provider = new JdbcConnectionProvider(config, this::open);
            UserRepositoryJdbc users = new UserRepositoryJdbc(provider);
            try {
                new JdbcTransactionManager(provider).inTransaction(() -> {
                    provider.withConnection(connection -> {
                        try (var insert = connection.prepareStatement("INSERT INTO public.seller(id,name,phone,address) VALUES (?,'Fixture V105','000','Ficticia')")) {
                            insert.setString(1, seller); assertEquals(1, insert.executeUpdate());
                        }
                        return null;
                    });
                    String hash = "$pbkdf2-sha256$i=1000$AAAAAAAAAAA=$AAAAAAAAAAAAAAAAAAAAAA==";
                    left = new UserAccount(null, leftName, hash, UserRole.WORKER, kind.equals("reactivate-reactivate") ? seller : null);
                    right = new UserAccount(null, rightName, hash, UserRole.WORKER, kind.equals("assign-assign") ? null : seller);
                    left.setActive(false); right.setActive(false);
                    users.save(left); users.save(right);
                });
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (Exception ignored) { /* La destrucción del laboratorio es el último recurso. */ }
                throw failure;
            }
        }
        Connection open() throws SQLException { return AccountInstancePostgresIntegrationTest.openVerified(config); }
        void activate(Connection connection, UserAccount user) throws SQLException {
            try (var update = connection.prepareStatement("UPDATE public.user_account SET active=TRUE,seller_id=? WHERE id=? AND account_instance_id=?")) {
                update.setString(1, seller); update.setLong(2, user.getId()); update.setObject(3, user.getAccountInstanceId());
                assertEquals(1, update.executeUpdate());
            }
        }
        public void close() throws SQLException {
            if (!writersStopped) throw new IllegalStateException("No se limpia con escritores activos.");
            try (Connection connection = open()) {
                connection.setAutoCommit(false);
                try (var delete = connection.prepareStatement("DELETE FROM public.user_account WHERE username IN (?,?)")) {
                    delete.setString(1, leftName); delete.setString(2, rightName); delete.executeUpdate();
                }
                try (var delete = connection.prepareStatement("DELETE FROM public.seller WHERE id=?")) {
                    delete.setString(1, seller); delete.executeUpdate();
                }
                connection.commit();
            }
        }
    }
}
