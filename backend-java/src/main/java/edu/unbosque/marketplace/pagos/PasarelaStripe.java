package edu.unbosque.marketplace.pagos;

import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stripe en modo prueba (clave sk_test_...). Métodos de prueba: pm_card_visa (aprueba),
 * pm_card_chargeDeclined (rechaza). El monto se envía en la unidad menor (x100); en la cuenta de prueba
 * COP se maneja con dos decimales.
 */
@Component
@ConditionalOnProperty(name = "marketplace.pasarela", havingValue = "stripe")
public class PasarelaStripe implements PasarelaPago {

    private final PropiedadesApp props;

    public PasarelaStripe(PropiedadesApp props) {
        this.props = props;
        if (props.stripeApiKey() == null || !props.stripeApiKey().startsWith("sk_test_")) {
            throw new IllegalStateException("Stripe solo se permite en modo prueba: defina STRIPE_API_KEY=sk_test_...");
        }
    }

    @Override
    public String nombre() {
        return "STRIPE_TEST";
    }

    @Override
    public Cobro cobrar(BigDecimal monto, String moneda, String metodoPago, String llave) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(aUnidadMenor(monto))
                    .setCurrency(moneda)
                    .setPaymentMethod(metodoPago)
                    .setConfirm(true)
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                            .build())
                    .build();
            PaymentIntent pi = PaymentIntent.create(params, opciones(llave));
            return "succeeded".equals(pi.getStatus())
                    ? new Cobro(true, pi.getId(), null)
                    : new Cobro(false, pi.getId(), "El pago no se completó (estado " + pi.getStatus() + ").");
        } catch (CardException e) {
            return new Cobro(false, null, e.getUserMessage() != null ? e.getUserMessage() : "Tarjeta rechazada.");
        } catch (StripeException e) {
            throw new IllegalStateException("Error al comunicarse con Stripe: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public String reembolsar(String idOperacion, BigDecimal monto, String moneda, String llave) {
        try {
            Refund r = Refund.create(RefundCreateParams.builder().setPaymentIntent(idOperacion)
                    .setAmount(aUnidadMenor(monto)).build(), opciones("refund-" + llave));
            return r.getId();
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo reembolsar en Stripe: " + e.getClass().getSimpleName(), e);
        }
    }

    private RequestOptions opciones(String llave) {
        return RequestOptions.builder().setApiKey(props.stripeApiKey()).setIdempotencyKey(llave).build();
    }

    private static long aUnidadMenor(BigDecimal monto) {
        return monto.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}
