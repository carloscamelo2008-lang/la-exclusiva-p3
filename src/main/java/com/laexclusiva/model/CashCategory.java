package com.laexclusiva.model;

/** Categorías fijas para separar recaudos reales de ventas, deuda general y otros movimientos. */
public enum CashCategory {
    /** Pago inicial que se recibe en la misma operación de creación de la venta. */
    SALE_INITIAL_PAYMENT("Pago inicial de venta", CashDirection.INCOME, false),
    /** Abono asociado a una venta específica. */
    SALE_INSTALLMENT("Abono a venta", CashDirection.INCOME, false),
    /** Pago aplicado a deuda general del cliente. */
    GENERAL_DEBT_PAYMENT("Pago de deuda general", CashDirection.INCOME, false),
    /** Ingreso manual no generado por ventas ni cartera. */
    OTHER_INCOME("Otro ingreso", CashDirection.INCOME, true),
    /** Egreso manual de servicios públicos u operativos. */
    SERVICES("Servicios", CashDirection.EXPENSE, true),
    /** Egreso manual por hielo. */
    ICE("Hielo", CashDirection.EXPENSE, true),
    /** Egreso manual por pagos a trabajadores. */
    WORKER_PAYMENT("Pago a trabajadores", CashDirection.EXPENSE, true),
    /** Retiro manual de efectivo. */
    WITHDRAWAL("Retiro", CashDirection.EXPENSE, true),
    /** Otro egreso manual no incluido en categorías específicas. */
    OTHER_EXPENSE("Otro gasto", CashDirection.EXPENSE, true);

    private final String label;
    private final CashDirection direction;
    private final boolean manual;

    CashCategory(String label, CashDirection direction, boolean manual) {
        this.label = label;
        this.direction = direction;
        this.manual = manual;
    }

    /** @return etiqueta visible en la interfaz en español */
    public String label() { return label; }
    /** @return sentido monetario obligatorio para la categoría */
    public CashDirection direction() { return direction; }
    /** @return {@code true} si se permite capturar desde la pantalla de caja */
    public boolean isManualEntry() { return manual; }

    @Override public String toString() { return label; }
}
