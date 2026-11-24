package com.laexclusiva.model;

/** Indica que un cliente conserva saldo o referencias que impiden eliminarlo. */
public final class CustomerDeletionBlockedException extends IllegalArgumentException {
    public CustomerDeletionBlockedException() {
        super("No se puede eliminar el cliente porque tiene saldo pendiente o historial asociado.");
    }
}
