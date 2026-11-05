package com.joselorza.app.presentation.mapper;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppointmentMapper {

    private final PaymentMapper paymentMapper;

    public AppointmentResponse toResponse(Appointment appointment, Payment payment) {
        PaymentResponse paymentResponse = payment == null
                ? null
                : paymentMapper.toResponse(payment);

        ServicePlan servicePlan = appointment.getServicePlan();

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getUser().getId(),
                appointment.getUser().getName(),
                appointment.getUser().getEmail(),
                servicePlan.getId(),
                servicePlan.getName(),
                servicePlan.getModality(),
                servicePlan.getPriceCop(),
                servicePlan.getPriceUsd(),
                servicePlan.getTaxRate(),
                appointment.getScheduledAt(),
                appointment.getDurationMinutes(),
                appointment.getUserTimezone(),
                appointment.getStatus(),
                appointment.getMeetingLink(),
                appointment.getGroupKey(),
                appointment.getPenaltyApplied(),
                appointment.getPenaltyAmount(),
                appointment.getPaymentDueAt(),
                appointment.getPendingRescheduledAt(),
                appointment.getCancellationReason(),
                appointment.getCancelledAt(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt(),
                paymentResponse
        );
    }
}
