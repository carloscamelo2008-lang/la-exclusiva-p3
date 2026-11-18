package com.laexclusiva.repository;

import com.laexclusiva.model.Sale;
import java.util.Optional;

/** Puerto para persistir ventas con una identidad estable del intento del usuario. */
public interface IdempotentSaleRepository {

    /** Resultado de reservar una solicitud y guardar su venta dentro de la transacción actual. */
    record SaveResult(Sale sale, boolean inserted) { }

    /** Registro histórico que permite reconocer un reintento antes de validar saldos y stock actuales. */
    record RequestRecord(Sale sale, String fingerprint) { }

    /**
     * Busca una solicitud por su clave estable.
     *
     * @param idempotencyKey clave del intento original
     * @return venta y huella persistidas, si existen
     */
    Optional<RequestRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Inserta la venta y sus dependencias, o devuelve la venta original si otro intento ya usó la clave.
     * Debe ejecutarse dentro de la misma transacción que inventario, movimientos y saldo del cliente.
     *
     * @param sale snapshot de la solicitud validada
     * @param idempotencyKey identidad estable del intento
     * @param fingerprint huella SHA-256 de los datos solicitados
     * @return venta existente o nueva y si esta llamada la insertó
     * @throws IllegalArgumentException si la clave se reutiliza con datos distintos
     */
    SaveResult saveOnce(Sale sale, String idempotencyKey, String fingerprint);
}
