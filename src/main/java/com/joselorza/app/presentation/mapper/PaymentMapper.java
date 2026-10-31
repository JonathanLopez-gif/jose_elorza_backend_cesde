package com.joselorza.app.presentation.mapper;

import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAppointment().getId(),
                payment.getGateway(),
                payment.getGatewayTransactionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentType(),
                payment.getCreatedAt()
        );
    }
}