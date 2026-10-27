package com.laexclusiva.validation;

/**
 * Validador para asegurar que los datos personales (nombre, teléfono y dirección)
 * cumplan con las reglas de negocio obligatorias del proyecto.
 */
public class PersonValidator {

    /**
     * Valida que los datos obligatorios de una persona no sean nulos ni estén vacíos.
     * 
     * @param name Nombre de la persona.
     * @param phone Teléfono de contacto.
     * @param address Dirección de residencia o comercial.
     * @throws IllegalArgumentException si alguno de los datos es inválido.
     */
    public static void validatePersonData(String name, String phone, String address) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio y no puede estar vacío.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono es obligatorio y no puede estar vacío.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección es obligatoria y no puede estar vacía.");
        }
    }
}