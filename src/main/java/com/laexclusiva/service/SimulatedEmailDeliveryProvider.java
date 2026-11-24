package com.laexclusiva.service;

/** Proveedor predeterminado; informa simulación y nunca abre conexiones de red. */
public final class SimulatedEmailDeliveryProvider implements EmailDeliveryProvider {
    @Override public boolean isRealDelivery() { return false; }

    @Override public Result deliver(Message message) {
        if (message == null) throw new IllegalArgumentException("El mensaje es obligatorio.");
        return Result.simulated();
    }
}
