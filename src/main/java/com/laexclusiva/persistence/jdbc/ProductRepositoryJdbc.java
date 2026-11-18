package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.repository.ProductRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persiste el catálogo y sus precios en PostgreSQL. */
public final class ProductRepositoryJdbc implements ProductRepository {
    private static final List<SalePresentation> PRESENTATIONS = List.of(
            SalePresentation.UNIT, SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET);
    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.name, p.volume_ml, p.units_per_basket, p.container_type,
                   p.container_group, pp.presentation_code, pp.price
            FROM beer_product p
            LEFT JOIN product_price pp ON pp.product_id = p.id
            """;

    private final JdbcConnectionProvider connectionProvider;
    private final JdbcTransactionManager transactionManager;

    public ProductRepositoryJdbc(JdbcConnectionProvider connectionProvider,
                                 JdbcTransactionManager transactionManager) {
        if (connectionProvider == null || transactionManager == null) {
            throw new IllegalArgumentException("El proveedor JDBC y el coordinador son obligatorios.");
        }
        this.connectionProvider = connectionProvider;
        this.transactionManager = transactionManager;
    }

    @Override
    public void save(BeerProduct product) {
        if (product == null || !product.hasAllPrices()) {
            throw new IllegalArgumentException("Se requiere un producto con sus tres precios.");
        }
        BeerProduct snapshot = product.copy();
        PRESENTATIONS.forEach(presentation -> BeerProduct.validatePrice(snapshot.priceFor(presentation)));

        transactionManager.inTransaction(() -> connectionProvider.withConnection(connection -> {
            insertProduct(connection, snapshot);
            String sql = """
                    INSERT INTO product_price (product_id, presentation_code, price)
                    VALUES (?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (SalePresentation presentation : PRESENTATIONS) {
                    statement.setString(1, snapshot.getId());
                    statement.setString(2, presentation.code());
                    statement.setBigDecimal(3, snapshot.priceFor(presentation));
                    statement.executeUpdate();
                }
            }
            return null;
        }));
    }

    private static void insertProduct(Connection connection, BeerProduct product) throws SQLException {
        String sql = """
                INSERT INTO beer_product
                    (id, name, volume_ml, units_per_basket, container_type, container_group)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getId());
            statement.setString(2, product.getName());
            statement.setInt(3, product.getVolumeMl());
            statement.setInt(4, product.getUnitsPerBasket());
            statement.setString(5, product.getContainerType().name());
            statement.setString(6, product.getContainerGroup());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Ya existe un producto con el id " + product.getId() + ".");
            }
        }
    }

    @Override
    public Optional<BeerProduct> findById(String id) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SELECT + " WHERE p.id = ?")) {
                statement.setString(1, id);
                return readProducts(statement).stream().findFirst();
            }
        });
    }

    @Override
    public List<BeerProduct> findAll() {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SELECT + " ORDER BY p.id")) {
                return readProducts(statement);
            }
        });
    }

    private static List<BeerProduct> readProducts(PreparedStatement statement) throws SQLException {
        Map<String, BeerProduct> products = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                BeerProduct product = products.get(id);
                if (product == null) {
                    product = readProduct(rows);
                    products.put(id, product);
                }
                readPrice(rows, product);
            }
        }
        return new ArrayList<>(products.values());
    }

    static BeerProduct readProduct(ResultSet row) throws SQLException {
        return new BeerProduct(row.getString("id"), row.getString("name"), row.getInt("volume_ml"),
                row.getInt("units_per_basket"), ContainerType.valueOf(row.getString("container_type")),
                row.getString("container_group"));
    }

    static void readPrice(ResultSet row, BeerProduct product) throws SQLException {
        String code = row.getString("presentation_code");
        if (code != null) {
            product.definePrice(SalePresentation.fromCode(code), row.getBigDecimal("price"));
        }
    }

    @Override
    public boolean existsById(String id) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM beer_product WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }

    @Override
    public void updatePrice(String productId, SalePresentation presentation, BigDecimal newPrice) {
        String code = validatePriceChange(productId, presentation, newPrice);
        connectionProvider.withConnection(connection -> {
            String sql = """
                    UPDATE product_price SET price = ?
                    WHERE product_id = ? AND presentation_code = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBigDecimal(1, newPrice);
                statement.setString(2, productId);
                statement.setString(3, code);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("No existe el producto o su precio para " + code + ".");
                }
            }
            return null;
        });
    }

    @Override
    public void addMissingPrice(String productId, SalePresentation presentation, BigDecimal price) {
        String code = validatePriceChange(productId, presentation, price);
        connectionProvider.withConnection(connection -> {
            String sql = """
                    INSERT INTO product_price (product_id, presentation_code, price)
                    SELECT id, ?, ? FROM beer_product WHERE id = ?
                    ON CONFLICT (product_id, presentation_code) DO NOTHING
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, code);
                statement.setBigDecimal(2, price);
                statement.setString(3, productId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("El producto no existe o ya tiene precio para " + code + ".");
                }
            }
            return null;
        });
    }

    private static String validatePriceChange(String productId, SalePresentation presentation, BigDecimal price) {
        if (productId == null || productId.isBlank() || presentation == null) {
            throw new IllegalArgumentException("El id del producto y la presentación son obligatorios.");
        }
        SalePresentation supported = SalePresentation.fromCode(presentation.code());
        BeerProduct.validatePrice(price);
        return supported.code();
    }
}
