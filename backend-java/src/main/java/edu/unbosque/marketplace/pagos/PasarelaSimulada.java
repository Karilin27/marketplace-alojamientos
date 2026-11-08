package edu.unbosque.marketplace.pagos;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pasarela simulada para desarrollo y pruebas (sin llamadas externas). Usa los mismos identificadores de
 * método de pago de prueba de Stripe, así los casos de prueba valen para ambas implementaciones.
 */
@Component
@ConditionalOnProperty(name = "marketplace.pasarela", havingValue = "simulada", matchIfMissing = true)
public class PasarelaSimulada implements PasarelaPago {

    private static final Map<String, String> RECHAZOS = Map.of(
            "pm_card_chargeDeclined", "La tarjeta fue rechazada.",
            "pm_card_chargeDeclinedInsufficientFunds", "Fondos insuficientes.",
            "pm_card_chargeDeclinedExpiredCard", "La tarjeta está vencida.",
            "pm_card_chargeDeclinedIncorrectCvc", "El código de seguridad es incorrecto.");

    @Override
    public String nombre() {
        return "SIMULADA";
    }

    @Override
    public Cobro cobrar(BigDecimal monto, String moneda, String metodoPago, String llave) {
        if (metodoPago != null && RECHAZOS.containsKey(metodoPago)) {
            return new Cobro(false, null, RECHAZOS.get(metodoPago));
        }
        if (metodoPago != null && metodoPago.startsWith("pm_card_")) {
            return new Cobro(true, "sim_pi_" + UUID.nameUUIDFromBytes(llave.getBytes()), null);
        }
        return new Cobro(false, null, "Método de pago no soportado.");
    }

    @Override
    public String reembolsar(String idOperacion, BigDecimal monto, String moneda, String llave) {
        return "sim_re_" + UUID.nameUUIDFromBytes(llave.getBytes());
    }
}
