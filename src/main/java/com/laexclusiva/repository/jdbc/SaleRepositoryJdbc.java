package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.SalePaymentAppender;
import com.laexclusiva.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación JDBC del historial de ventas.
 *
 * <p>Las inserciones de cabecera, líneas y pago inicial se confirman en una sola transacción.
 * Los abonos nuevos requieren una clave idempotente y bloqueo de fila; las actualizaciones por
 * instantánea se rechazan. Las consultas cargan relaciones por lotes y conservan los snapshots SQL.</p>
 */
public final class SaleRepositoryJdbc implements SaleRepository, SalePaymentAppender {

    private static final String SALE_SELECT = """
            SELECT s.id AS sale_id, s.created_at, se.id AS seller_id, se.name AS seller_name,
                   se.phone AS seller_phone, se.address AS seller_address,
                   c.id AS customer_id, c.name AS customer_name, c.phone AS customer_phone,
                   c.address AS customer_address, c.balance AS customer_balance
            FROM sale s
            JOIN seller se ON se.id = s.seller_id
            LEFT JOIN customer c ON c.id = s.customer_id
            """;

    private final JdbcConnectionProvider connectionProvider;
    private final JdbcTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Crea el adaptador con el proveedor compartido y el coordinador transaccional de JDBC.
     *
     * @param connectionProvider proveedor de conexiones PostgreSQL
     * @param transactionManager coordinador que comparte conexión y controla commit/rollback
     * @throws IllegalArgumentException si alguna dependencia es nula
     */
    public SaleRepositoryJdbc(JdbcConnectionProvider connectionProvider,
                              JdbcTransactionManager transactionManager) {
        this(connectionProvider, transactionManager, Clock.systemDefaultZone());
    }

    /**
     * Crea el adaptador con una fuente temporal controlable para pruebas deterministas.
     *
     * @param connectionProvider proveedor JDBC compartido
     * @param transactionManager coordinador de transacciones
     * @param clock reloj usado para fechar abonos nuevos
     * @throws IllegalArgumentException si alguna dependencia es nula
     */
    public SaleRepositoryJdbc(JdbcConnectionProvider connectionProvider,
                              JdbcTransactionManager transactionManager, Clock clock) {
        if (connectionProvider == null || transactionManager == null || clock == null) {
            throw new IllegalArgumentException("El proveedor JDBC, el coordinador y el reloj son obligatorios.");
        }
        this.connectionProvider = connectionProvider;
        this.transactionManager = transactionManager;
        this.clock = clock;
    }

    /**
     * Guarda una venta nueva, sus snapshots de líneas y el pago inicial positivo en una transacción.
     *
     * @param sale venta completa que se desea insertar
     * @throws IllegalArgumentException si la venta o sus importes no cumplen el contrato del esquema,
     *                                  o si ya existe el ID
     * @throws JdbcPersistenceException si falla una sentencia SQL; la transacción completa se revierte
     */
    @Override
    public void save(Sale sale) {
        validateSale(sale);
        transactionManager.inTransaction(() -> connectionProvider.withConnection(connection -> {
            insertSale(connection, sale);
            for (SaleLine line : sale.getLines()) {
                insertLine(connection, sale.getId(), line);
            }
            for (SalePayment payment : sale.getPayments()) {
                insertPayment(connection, sale.getId(), payment, null);
            }
            return null;
        }));
    }

    /**
     * Rechaza las actualizaciones por instantánea, que no incluyen una clave de pago persistente.
     *
     * @param sale estado recibido; no se inspecciona ni se persiste
     * @throws UnsupportedOperationException siempre; use {@link #appendPayment(String, BigDecimal, String)}
     */
    @Override
    public void update(Sale sale) {
        throw new UnsupportedOperationException(
                "La actualización por instantánea no es segura; registre abonos mediante appendPayment.");
    }

    /** {@inheritDoc} */
    @Override
    public SalePayment appendPayment(String saleId, BigDecimal amount, String idempotencyKey) {
        requireId(saleId, "El identificador de venta es obligatorio.");
        if (saleId.length() > 48) throw new IllegalArgumentException("El identificador supera el límite SQL.");
        BigDecimal normalizedAmount = requireMoney(amount, "importe del abono");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La clave de idempotencia debe tener entre 1 y 64 caracteres.");
        }
        return transactionManager.inTransaction(() -> connectionProvider.withConnection(connection -> {
            findHeader(connection, saleId, true).orElseThrow(() ->
                    new IllegalArgumentException("No existe una venta con el ID: " + saleId));
            SalePayment existing = findPaymentByKey(connection, saleId, idempotencyKey);
            if (existing != null) {
                if (existing.getAmount().compareTo(normalizedAmount) != 0) {
                    throw new IllegalArgumentException("La clave de idempotencia ya se registró con otro importe.");
                }
                return existing;
            }
            BigDecimal[] totals = findSaleTotals(connection, saleId);
            BigDecimal balance = totals[0].subtract(totals[1]);
            if (normalizedAmount.compareTo(balance) > 0) {
                throw new IllegalArgumentException("El abono supera el saldo pendiente de la venta.");
            }
            SalePayment payment = new SalePayment(normalizedAmount, normalize(LocalDateTime.now(clock)));
            insertPayment(connection, saleId, payment, idempotencyKey);
            return payment;
        }));
    }

    /**
     * Rechaza el borrado físico para conservar la historia comercial y financiera.
     *
     * @param id identificador de venta (no se ejecuta SQL para ningún ID)
     * @throws UnsupportedOperationException siempre; las ventas no se eliminan desde el adaptador JDBC
     */
    @Override
    public void deleteById(String id) {
        throw new UnsupportedOperationException(
                "El historial de ventas JDBC no admite borrado físico; use rollback dentro de la transacción.");
    }

    /**
     * Recupera cabecera, líneas y pagos sin generar una identidad nueva ni consultar precios actuales.
     *
     * @param id identificador de venta
     * @return venta íntegra o vacío cuando no existe
     * @throws IllegalArgumentException si el identificador es nulo o está en blanco
     * @throws JdbcPersistenceException si falla la consulta o los datos almacenados violan el modelo
     */
    @Override
    public Optional<Sale> findById(String id) {
        requireId(id, "El identificador de venta es obligatorio.");
        return connectionProvider.withConnection(connection -> {
            Optional<Header> header = findHeader(connection, id, false);
            if (header.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(loadSale(connection, header.orElseThrow()));
        });
    }

    /**
     * Lista ventas por fecha e ID, cargando líneas y pagos en dos consultas agrupadas.
     *
     * @return ventas completas en orden determinista
     * @throws JdbcPersistenceException si falla SQL o hay datos persistidos inconsistentes
     */
    @Override
    public List<Sale> findAll() {
        return connectionProvider.withConnection(connection -> {
            List<Header> headers = findHeaders(connection);
            if (headers.isEmpty()) {
                return List.of();
            }
            List<String> ids = headers.stream().map(Header::id).toList();
            Map<String, List<SaleLine>> linesBySale = findLines(connection, ids);
            Map<String, List<SalePayment>> paymentsBySale = findPayments(connection, ids);
            List<Sale> sales = new ArrayList<>(headers.size());
            for (Header header : headers) {
                sales.add(reconstitute(header,
                        linesBySale.getOrDefault(header.id(), List.of()),
                        paymentsBySale.getOrDefault(header.id(), List.of())));
            }
            return List.copyOf(sales);
        });
    }

    private static void insertSale(Connection connection, Sale sale) throws SQLException {
        String sql = "INSERT INTO sale (id, created_at, seller_id, customer_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sale.getId());
            statement.setObject(2, normalize(sale.getCreatedAt()));
            statement.setString(3, sale.getSeller().getId());
            Customer customer = sale.getCustomer();
            if (customer == null) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, customer.getId());
            }
            try {
                requireOneRow(statement.executeUpdate(), "No se insertó la cabecera de la venta.");
            } catch (SQLException failure) {
                if ("23505".equals(failure.getSQLState())) {
                    throw new IllegalArgumentException("Ya existe una venta con el ID: " + sale.getId(), failure);
                }
                throw failure;
            }
        }
    }

    private static void insertLine(Connection connection, String saleId, SaleLine line) throws SQLException {
        String sql = """
                INSERT INTO sale_line
                    (sale_id, product_id, presentation_code, quantity,
                     units_per_basket_snapshot, unit_price)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId);
            statement.setString(2, line.getProduct().getId());
            statement.setString(3, line.getPresentation().code());
            statement.setInt(4, line.getQuantity());
            statement.setInt(5, line.getUnitsPerBasketSnapshot());
            statement.setBigDecimal(6, requireMoney(line.getUnitPrice(), "precio histórico de línea"));
            requireOneRow(statement.executeUpdate(), "No se insertó una línea de venta.");
        }
    }

    private static void insertPayment(Connection connection, String saleId, SalePayment payment,
                                      String idempotencyKey)
            throws SQLException {
        String sql = "INSERT INTO sale_payment (sale_id, amount, occurred_at, idempotency_key) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId);
            statement.setBigDecimal(2, requireMoney(payment.getAmount(), "importe del pago"));
            statement.setObject(3, normalize(payment.getTimestamp()));
            statement.setString(4, idempotencyKey);
            requireOneRow(statement.executeUpdate(), "No se insertó un pago de la venta.");
        }
    }

    private static SalePayment findPaymentByKey(Connection connection, String saleId, String key)
            throws SQLException {
        String sql = "SELECT amount, occurred_at FROM sale_payment WHERE sale_id = ? AND idempotency_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId);
            statement.setString(2, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return new SalePayment(resultSet.getBigDecimal("amount"),
                        resultSet.getObject("occurred_at", LocalDateTime.class));
            }
        }
    }

    private static BigDecimal[] findSaleTotals(Connection connection, String saleId) throws SQLException {
        String sql = """
                SELECT COALESCE((SELECT SUM(quantity * unit_price) FROM sale_line WHERE sale_id = ?), 0) AS total,
                       COALESCE((SELECT SUM(amount) FROM sale_payment WHERE sale_id = ?), 0) AS paid
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, saleId);
            statement.setString(2, saleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("No se pudieron calcular los totales de venta.");
                return new BigDecimal[]{resultSet.getBigDecimal("total"), resultSet.getBigDecimal("paid")};
            }
        }
    }

    private static List<Header> findHeaders(Connection connection) throws SQLException {
        String sql = SALE_SELECT + " ORDER BY s.created_at ASC, s.id ASC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Header> headers = new ArrayList<>();
            while (resultSet.next()) {
                headers.add(mapHeader(resultSet));
            }
            return headers;
        }
    }

    private static Optional<Header> findHeader(Connection connection, String id, boolean forUpdate)
            throws SQLException {
        String sql = SALE_SELECT + " WHERE s.id = ?" + (forUpdate ? " FOR UPDATE OF s" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapHeader(resultSet)) : Optional.empty();
            }
        }
    }

    private static Header mapHeader(ResultSet resultSet) throws SQLException {
        try {
            String sellerId = resultSet.getString("seller_id");
            Seller seller = new Seller(sellerId, resultSet.getString("seller_name"),
                    resultSet.getString("seller_phone"), resultSet.getString("seller_address"));
            String customerId = resultSet.getString("customer_id");
            Customer customer = customerId == null ? null : new Customer(customerId,
                    resultSet.getString("customer_name"), resultSet.getString("customer_phone"),
                    resultSet.getString("customer_address"), resultSet.getBigDecimal("customer_balance"));
            return new Header(resultSet.getString("sale_id"),
                    resultSet.getObject("created_at", LocalDateTime.class), seller, customer);
        } catch (IllegalArgumentException failure) {
            throw new JdbcPersistenceException("La cabecera o las entidades asociadas contienen datos inválidos.",
                    failure);
        }
    }

    private static Sale loadSale(Connection connection, Header header) throws SQLException {
        Map<String, List<SaleLine>> lines = findLines(connection, List.of(header.id()));
        Map<String, List<SalePayment>> payments = findPayments(connection, List.of(header.id()));
        return reconstitute(header, lines.getOrDefault(header.id(), List.of()),
                payments.getOrDefault(header.id(), List.of()));
    }

    private static Sale reconstitute(Header header, List<SaleLine> lines, List<SalePayment> payments) {
        try {
            return Sale.reconstitute(header.id(), header.createdAt(), header.seller(), header.customer(),
                    lines, payments);
        } catch (IllegalArgumentException failure) {
            throw new JdbcPersistenceException(
                    "La venta persistida " + header.id() + " no cumple las invariantes del modelo.", failure);
        }
    }

    private static Map<String, List<SaleLine>> findLines(Connection connection, List<String> saleIds)
            throws SQLException {
        Map<String, List<SaleLine>> grouped = new LinkedHashMap<>();
        if (saleIds.isEmpty()) {
            return grouped;
        }
        String sql = """
                SELECT l.sale_id, l.product_id, l.presentation_code, l.quantity,
                       l.units_per_basket_snapshot, l.unit_price,
                       p.name AS product_name, p.volume_ml, p.units_per_basket,
                       p.container_type, p.container_group
                FROM sale_line l
                JOIN beer_product p ON p.id = l.product_id
                WHERE l.sale_id IN (%s)
                ORDER BY l.sale_id ASC, l.id ASC
                """.formatted(placeholders(saleIds.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, saleIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        String saleId = resultSet.getString("sale_id");
                        BeerProduct product = new BeerProduct(
                                resultSet.getString("product_id"), resultSet.getString("product_name"),
                                resultSet.getInt("volume_ml"), resultSet.getInt("units_per_basket"),
                                ContainerType.valueOf(resultSet.getString("container_type")),
                                resultSet.getString("container_group"));
                        SalePresentation presentation = presentationForCode(
                                resultSet.getString("presentation_code"));
                        SaleLine line = SaleLine.reconstitute(product, presentation,
                                resultSet.getInt("quantity"), resultSet.getBigDecimal("unit_price"),
                                resultSet.getInt("units_per_basket_snapshot"));
                        grouped.computeIfAbsent(saleId, ignored -> new ArrayList<>()).add(line);
                    } catch (IllegalArgumentException | NullPointerException failure) {
                        throw new JdbcPersistenceException("Una línea de venta persistida contiene datos inválidos.",
                                failure);
                    }
                }
            }
        }
        return grouped;
    }

    private static Map<String, List<SalePayment>> findPayments(Connection connection, List<String> saleIds)
            throws SQLException {
        Map<String, List<SalePayment>> grouped = new LinkedHashMap<>();
        if (saleIds.isEmpty()) {
            return grouped;
        }
        String sql = """
                SELECT sale_id, amount, occurred_at
                FROM sale_payment
                WHERE sale_id IN (%s)
                ORDER BY sale_id ASC, occurred_at ASC, id ASC
                """.formatted(placeholders(saleIds.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, saleIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        String saleId = resultSet.getString("sale_id");
                        SalePayment payment = new SalePayment(resultSet.getBigDecimal("amount"),
                                resultSet.getObject("occurred_at", LocalDateTime.class));
                        grouped.computeIfAbsent(saleId, ignored -> new ArrayList<>()).add(payment);
                    } catch (IllegalArgumentException | NullPointerException failure) {
                        throw new JdbcPersistenceException("Un pago persistido contiene datos inválidos.", failure);
                    }
                }
            }
        }
        return grouped;
    }

    private static void validateSale(Sale sale) {
        if (sale == null) {
            throw new IllegalArgumentException("La venta no puede ser nula.");
        }
        requireId(sale.getId(), "El identificador de venta es obligatorio.");
        if (sale.getId().length() > 48 || sale.getSeller().getId().length() > 32
                || (sale.getCustomer() != null && sale.getCustomer().getId().length() > 64)) {
            throw new IllegalArgumentException("Un identificador de venta, vendedor o cliente supera el esquema SQL.");
        }
        for (SaleLine line : sale.getLines()) {
            if (line.getProduct().getId().length() > 64) {
                throw new IllegalArgumentException("El identificador del producto supera el límite SQL.");
            }
            requireMoney(line.getUnitPrice(), "precio histórico de línea");
            if (line.getUnitsPerBasketSnapshot() <= 0 || line.getUnitsPerBasketSnapshot() % 2 != 0) {
                throw new IllegalArgumentException("La capacidad histórica de canasta debe ser positiva y par.");
            }
        }
        sale.getPayments().forEach(payment -> requireMoney(payment.getAmount(), "importe del pago"));
    }

    private static BigDecimal requireMoney(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El " + label + " debe ser mayor que cero.");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new IllegalArgumentException("El " + label + " supera NUMERIC(19,2).");
            }
            return normalized;
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("El " + label + " no puede tener más de dos decimales.", failure);
        }
    }

    private static SalePresentation presentationForCode(String code) {
        return switch (code) {
            case "UNIT" -> SalePresentation.UNIT;
            case "HALF_BASKET" -> SalePresentation.HALF_BASKET;
            case "FULL_BASKET" -> SalePresentation.FULL_BASKET;
            default -> throw new IllegalArgumentException("Código de presentación persistido desconocido: " + code);
        };
    }

    private static void bindIds(PreparedStatement statement, List<String> ids) throws SQLException {
        for (int index = 0; index < ids.size(); index++) {
            statement.setString(index + 1, ids.get(index));
        }
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static LocalDateTime normalize(LocalDateTime value) {
        return value.withNano((value.getNano() / 1_000) * 1_000);
    }

    private static void requireId(String id, String message) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireOneRow(int updated, String message) throws SQLException {
        if (updated != 1) {
            throw new SQLException(message);
        }
    }

    private record Header(String id, LocalDateTime createdAt, Seller seller, Customer customer) { }

}
