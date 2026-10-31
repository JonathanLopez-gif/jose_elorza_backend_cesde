package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.presentation.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentMapper paymentMapper;

    /**
     * Confirma manualmente un pago (gateway MANUAL) y aplica la transición
     * de estado correspondiente sobre la cita asociada.
     */
    @Transactional
    public PaymentResponse confirmManualPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (payment.getGateway() != PaymentGateway.MANUAL) {
            throw new BusinessRuleException(
                    "Only manual payments can be confirmed through this endpoint.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException(
                    "The payment has already been processed previously. Current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.COMPLETED);

        Appointment appointment = payment.getAppointment();
        applyAppointmentTransition(appointment, payment.getPaymentType());

        appointment.setPaymentDueAt(null);

        appointmentRepository.save(appointment);
        Payment saved = paymentRepository.save(payment);

        return paymentMapper.toResponse(saved);
    }

    private void applyAppointmentTransition(Appointment appointment, PaymentType paymentType) {
        if (paymentType == PaymentType.BOOKING) {
            if (appointment.getServicePlan().getModality() == Modality.RECORDED) {
                appointment.setStatus(AppointmentStatus.COMPLETED);
            } else {
                appointment.setStatus(AppointmentStatus.CONFIRMED);
            }
        } else if (paymentType == PaymentType.PENALTY) {
            if (appointment.getPendingRescheduledAt() != null) {
                // Reprogramación tardía: se aplica la nueva fecha
                appointment.setScheduledAt(appointment.getPendingRescheduledAt());
                appointment.setPendingRescheduledAt(null);
                appointment.setStatus(AppointmentStatus.CONFIRMED);
            } else {
                // Cancelación tardía: se confirma la cancelación
                appointment.setStatus(AppointmentStatus.CANCELLED);
            }
        }
    }

}