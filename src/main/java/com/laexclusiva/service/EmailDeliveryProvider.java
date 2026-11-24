package com.laexclusiva.service;

/** Puerto de entrega de correo; las pruebas sustituyen el proveedor por un doble sin red. */
public interface EmailDeliveryProvider {
    enum Outcome { ACCEPTED, REJECTED, UNCERTAIN, SIMULATED }
    enum Evidence { SMTP_ACCEPTED, SMTP_REJECTED, SMTP_OUTCOME_UNCERTAIN, SIMULATED }
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

    /** Resultado cerrado que distingue rechazo confirmado de aceptación o respuesta incierta. */
    record Result(Outcome outcome, Evidence evidence) {
        public Result {
            if (outcome == null || evidence == null) throw new IllegalArgumentException("El resultado del proveedor es obligatorio.");
            Evidence expected = switch (outcome) {
                case ACCEPTED -> Evidence.SMTP_ACCEPTED;
                case REJECTED -> Evidence.SMTP_REJECTED;
                case UNCERTAIN -> Evidence.SMTP_OUTCOME_UNCERTAIN;
                case SIMULATED -> Evidence.SIMULATED;
            };
            if (evidence != expected) throw new IllegalArgumentException("La evidencia no corresponde al resultado SMTP.");
        }
        public static Result accepted() { return new Result(Outcome.ACCEPTED, Evidence.SMTP_ACCEPTED); }
        public static Result rejected() { return new Result(Outcome.REJECTED, Evidence.SMTP_REJECTED); }
        public static Result uncertain() { return new Result(Outcome.UNCERTAIN, Evidence.SMTP_OUTCOME_UNCERTAIN); }
        public static Result simulated() { return new Result(Outcome.SIMULATED, Evidence.SIMULATED); }
        public boolean acceptedByProvider() { return outcome == Outcome.ACCEPTED; }
    }

    /** @return true solo cuando se configuró explícitamente transporte SMTP real */
    boolean isRealDelivery();

    /** Entrega un mensaje solo tras las verificaciones de consentimiento, ventana y permisos. */
    Result deliver(Message message);
}
