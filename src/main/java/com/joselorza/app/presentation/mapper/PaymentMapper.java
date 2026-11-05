package com.joselorza.app.presentation.mapper;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        Appointment appointment = payment.getAppointment();
        BigDecimal taxRate = appointment.getServicePlan().getTaxRate();

        // El monto almacenado incluye IVA: base = total / (1 + tasa)
        BigDecimal base = payment.getAmount().divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
        BigDecimal tax = payment.getAmount().subtract(base);

        return new PaymentResponse(
                payment.getId(),
                appointment.getId(),
                payment.getUser().getId(),
                payment.getUser().getName(),
                payment.getUser().getEmail(),
                appointment.getServicePlan().getName(),
                payment.getGateway(),
                payment.getGatewayTransactionId(),
                payment.getAmount(),
                base,
                tax,
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentType(),
                payment.getCheckoutUrl(),
                payment.getPaidAt(),
                refundDue(payment),
                payment.getRefundedAmount(),
                payment.getRefundReason(),
                payment.getRefundedAt(),
                payment.getCreatedAt()
        );
    }

    /**
     * Reembolso sugerido: lo pagado menos la penalización por cancelación tardía.
     * Solo aplica a pagos de reserva completados de citas canceladas.
     */
    private BigDecimal refundDue(Payment payment) {
        Appointment appointment = payment.getAppointment();
        if (payment.getStatus() != PaymentStatus.COMPLETED
                || payment.getPaymentType() != PaymentType.BOOKING
                || appointment.getStatus() != AppointmentStatus.CANCELLED) {
            return null;
        }
        BigDecimal penalty = Boolean.TRUE.equals(appointment.getPenaltyApplied()) && appointment.getPenaltyAmount() != null
                ? appointment.getPenaltyAmount()
                : BigDecimal.ZERO;
        return payment.getAmount().subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
