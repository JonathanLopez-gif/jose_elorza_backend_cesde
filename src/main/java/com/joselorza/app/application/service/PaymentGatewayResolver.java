package com.joselorza.app.application.service;

import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selecciona el adaptador de pago correcto según el tipo de pasarela.
 * Spring inyecta automáticamente todas las implementaciones de PaymentGatewayPort.
 */
@Component
public class PaymentGatewayResolver {

    private final Map<PaymentGateway, PaymentGatewayPort> adapters;

    public PaymentGatewayResolver(List<PaymentGatewayPort> gateways) {
        this.adapters = gateways.stream()
                .collect(Collectors.toMap(PaymentGatewayPort::getGatewayType, Function.identity()));
    }

    public PaymentGatewayPort resolve(PaymentGateway gateway) {
        PaymentGatewayPort port = adapters.get(gateway);
        if (port == null) {
            throw new BusinessRuleException("There is no adapter set up for the gateway: " + gateway);
        }
        return port;
    }
}