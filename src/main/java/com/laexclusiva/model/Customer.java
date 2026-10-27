package com.laexclusiva.model;

import com.laexclusiva.validation.PersonValidator;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo que representa a un cliente del sistema La Exclusiva.
 * <p>
 * Mantiene la información personal del cliente y un saldo acumulado coherente.
 * <b>Nota sobre integración con Ventas (Cuentas por Cobrar):</b> El saldo acumulado en este
 * modelo es un indicador provisional para reflejar la deuda actual del cliente. No reemplaza
 * ni constituye el historial completo de ventas pendientes ni la traza individual de abonos.
 * La futura integración del módulo de ventas relacionará las cuentas por cobrar con sus
 * respectivas fechas, ventas e importes.
 * </p>
 */
public class Customer {

    private final String id;
    private String name;
    private String phone;
    private String address;
    private BigDecimal currentDebt;

    /**
     * Constructor para registrar un nuevo cliente con deuda inicial en cero.
     */
    public Customer(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.id = "CLI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
        this.currentDebt = BigDecimal.ZERO;
    }

    /**
     * Constructor de reconstrucción para carga o persistencia de datos.
     * 
     * @param id Identificador único existente.
     * @param name Nombre completo.
     * @param phone Teléfono de contacto.
     * @param address Dirección de entrega.
     * @param currentDebt Saldo acumulado (debe ser distinto de nulo y mayor o igual a cero).
     */
    public Customer(String id, String name, String phone, String address, BigDecimal currentDebt) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del cliente no puede estar vacío.");
        }
        Objects.requireNonNull(currentDebt, "El saldo inicial no puede ser nulo.");
        if (currentDebt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo inicial de deuda no puede ser negativo.");
        }
        PersonValidator.validatePersonData(name, phone, address);

        this.id = id.trim();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
        this.currentDebt = currentDebt;
    }

    // --- Métodos de Negocio para Saldos ---

    /**
     * Incrementa la deuda del cliente por concepto de fiado.
     * <p>Alcance provisional: Representa el incremento directo del saldo pendiente sin registrar el detalle de la venta.</p>
     * 
     * @param amount Monto a fiar (debe ser estrictamente mayor que cero).
     */
    public void addDebt(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a fiar debe ser mayor que cero.");
        }
        this.currentDebt = this.currentDebt.add(amount);
    }

    /**
     * Aplica un abono o pago parcial a la deuda del cliente.
     * <p>Alcance provisional: Reduce el saldo acumulado directo sin registrar la transacción de pago detallada.</p>

     * 
     * @param amount Monto a abonar (debe ser mayor que cero y no superar el saldo actual).
     */
    public void applyPayment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a abonar debe ser mayor que cero.");
        }
        if (amount.compareTo(this.currentDebt) > 0) {
            throw new IllegalArgumentException("El monto del abono no puede superar la deuda actual (" + currentDebt + ").");
        }
        this.currentDebt = this.currentDebt.subtract(amount);
    }

    // --- Actualización con Validaciones ---

    public void updateData(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }

    @Override
    public String toString() {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        return String.format("Cliente [ID: %s, Nombre: %s, Teléfono: %s, Dirección: %s, Deuda Actual: %s]",
                id, name, phone, address, currencyFormat.format(currentDebt));
    }
}