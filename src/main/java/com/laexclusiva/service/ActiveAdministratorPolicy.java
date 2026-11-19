package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;

/**
 * Evalúa modificaciones de una cuenta existente sin acceder al almacenamiento.
 * No garantiza atomicidad, permisos ni seguridad concurrente por sí sola: el llamador
 * debe obtener cuentas y conteo actuales dentro del mismo bloqueo o transacción.
 * No representa creación, bootstrap ni eliminación mediante cuentas nulas.
 */
public final class ActiveAdministratorPolicy {

    /**
     * Impide retirar al último administrador activo de un conteo protegido.
     * No modifica las cuentas ni valida credenciales u otros datos de persistencia.
     * @param current estado actual de la cuenta persistida
     * @param proposed estado propuesto de la misma cuenta
     * @param activeAdministratorCount número actual de administradores activos, incluida la cuenta actual
     * @throws IllegalArgumentException por cuentas nulas, roles o IDs inválidos,
     *         IDs diferentes, conteo negativo o incompatible con la cuenta actual
     * @throws IllegalStateException si la modificación retira al último administrador activo
     */
    public void validateChange(UserAccount current, UserAccount proposed, long activeAdministratorCount) {
        validateAccount(current);
        validateAccount(proposed);
        if (!current.getId().equals(proposed.getId())) {
            throw new IllegalArgumentException("La modificación debe corresponder a la misma cuenta.");
        }
        if (activeAdministratorCount < 0
                || (isActiveAdministrator(current) && activeAdministratorCount == 0)) {
            throw new IllegalArgumentException("El conteo de administradores activos es inválido o inconsistente.");
        }
        if (isActiveAdministrator(current) && !isActiveAdministrator(proposed)
                && activeAdministratorCount == 1) {
            throw new IllegalStateException("No se puede retirar al último administrador activo.");
        }
    }

    private static boolean isActiveAdministrator(UserAccount account) {
        return account.isActive() && account.getRole() == UserRole.ADMINISTRATOR;
    }

    private static void validateAccount(UserAccount account) {
        if (account == null || account.getId() == null || account.getId() <= 0 || account.getRole() == null) {
            throw new IllegalArgumentException("La cuenta debe tener un ID positivo y un rol válido.");
        }
    }
}
