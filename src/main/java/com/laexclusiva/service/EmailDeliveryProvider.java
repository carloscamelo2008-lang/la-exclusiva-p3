package com.laexclusiva.service;

/** Puerto de entrega de correo; las pruebas sustituyen el proveedor por un doble sin red. */
public interface EmailDeliveryProvider {
    /** Solicitud efímera: no se guarda su cuerpo ni adjunto en el repositorio de recordatorios. */
    record Message(String recipient, String subject, String text, String attachmentName, byte[] attachment) {
        public Message {
            if (recipient == null || recipient.isBlank() || subject == null || text == null
                    || attachmentName == null || attachment == null || attachment.length == 0) {
                throw new IllegalArgumentException("El correo y el estado de cuenta adjunto son obligatorios.");
            }
            attachment = attachment.clone();
        }
        @Override public byte[] attachment() { return attachment.clone(); }
        @Override public String toString() { return "Message[contenido protegido]"; }
    }

    /** Resultado seguro que no contiene cabeceras, destinatario ni cuerpo. */
    record Result(boolean acceptedByProvider, String safeCode) { }

    /** @return true solo cuando se configuró explícitamente transporte SMTP real */
    boolean isRealDelivery();

    /** Entrega un mensaje solo tras las verificaciones de consentimiento, ventana y permisos. */
    Result deliver(Message message);
}
