package com.joselorza.app.application.port.out;

/**
 * Puerto de salida para generar el comprobante de pago descargable (PDF).
 */
public interface ReceiptGeneratorPort {

    byte[] generate(ReceiptData data);
}
