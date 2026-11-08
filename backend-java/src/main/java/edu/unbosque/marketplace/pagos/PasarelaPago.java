package edu.unbosque.marketplace.pagos;

import java.math.BigDecimal;

/** Puerto hacia la pasarela de pagos (Stripe en modo prueba o simulada; RN-09). */
public interface PasarelaPago {

    record Cobro(boolean aprobado, String idOperacion, String motivoRechazo) {
    }

    String nombre();

    /** @param llave clave de idempotencia hacia el proveedor (se usa el id del pago). */
    Cobro cobrar(BigDecimal monto, String moneda, String metodoPago, String llave);

    /** Devuelve el id del reembolso en el proveedor. Lanza excepción si no se pudo. */
    String reembolsar(String idOperacion, BigDecimal monto, String moneda, String llave);
}
