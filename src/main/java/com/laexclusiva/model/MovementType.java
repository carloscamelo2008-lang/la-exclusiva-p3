package com.laexclusiva.model;

/** Sentido de un movimiento de inventario. */
public enum MovementType {
    ENTRY, // entra mercancía (compra, recepción de pedido, ajuste positivo)
    EXIT   // sale mercancía (venta)
}
