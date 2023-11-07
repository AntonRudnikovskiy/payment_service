package faang.school.paymentservice.service.payment;

import faang.school.paymentservice.dto.InvoiceDto;
import faang.school.paymentservice.dto.PaymentDto;
import faang.school.paymentservice.dto.PaymentStatus;
import faang.school.paymentservice.dto.payment.PaymentEvent;
import faang.school.paymentservice.mapper.PaymentMapper;
import faang.school.paymentservice.messaging.PaymentEventPublisher;
import faang.school.paymentservice.model.Payment;
import faang.school.paymentservice.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentMapper paymentMapper;

    private final PaymentEventPublisher eventPublisher;

    public PaymentDto create(InvoiceDto dto) {
        Payment optionalPayment = getPaymentIfExist(dto);
        if (optionalPayment != null) {
            return paymentMapper.toDto(optionalPayment);
        }

        Payment payment = createPayment(dto);
        log.info("Created payment: {}", payment);

        return paymentMapper.toDto(payment);
    }

    public PaymentDto cancel(Long paymentId) {
        Payment payment = checkPaymentExist(paymentId);
        payment = cancelPayment(payment);
        log.info("Cancelled payment: {}", payment);

        return paymentMapper.toDto(payment);
    }

    public PaymentDto clear(Long paymentId) {
         Payment payment = checkPaymentExist(paymentId);

         payment = clearPayment(payment);
         log.info("Payment cleared: {}", payment);

         return paymentMapper.toDto(payment);
    }

    public void clear(Payment payment) {
        payment = checkPaymentExist(payment.getId());
        payment = clearPayment(payment);

        log.info("Payment cleared: {}", payment);
    }

    public PaymentDto schedule(Long paymentId, LocalDateTime scheduledAt) {
        Payment payment = checkPaymentExist(paymentId);
        payment = schedulePayment(payment, scheduledAt);

        return paymentMapper.toDto(payment);
    }

    public List<Payment> getScheduledPayment() {
        return paymentRepository.findAllScheduledPayments();
    }

    private Payment schedulePayment(Payment payment, LocalDateTime scheduledAt) {
        payment.setScheduledAt(scheduledAt);
        return paymentRepository.save(payment);
    }

    private Payment clearPayment(Payment payment) {
        payment.setStatus(PaymentStatus.CLEARED);

        sendEvent(payment);
        return paymentRepository.save(payment);
    }

    private Payment checkPaymentExist(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment with id %d not found".formatted(paymentId)));
    }

    private Payment cancelPayment(Payment payment) {
        payment.setStatus(PaymentStatus.CANCELED);
        if (payment.getScheduledAt() != null) {
            payment.setScheduledAt(null);
        }

        sendEvent(payment);
        return paymentRepository.save(payment);
    }

    private Payment createPayment(InvoiceDto dto) {
        Payment payment = Payment.builder()
                .senderAccount(dto.getSenderAccount())
                .receiverAccount(dto.getReceiverAccount())
                .currency(dto.getCurrency())
                .amount(dto.getAmount())
                .status(PaymentStatus.AUTHORIZATION)
                .idempotencyKey(dto.getIdempotencyKey())
                .build();
        sendEvent(payment);
        return paymentRepository.save(payment);
    }

    private Payment getPaymentIfExist(InvoiceDto dto) {
        Optional<Payment> optionalPayment = paymentRepository.findByIdempotencyKey(dto.getIdempotencyKey().toString());
        if (optionalPayment.isEmpty()) {
            return null;
        }
        Payment payment = optionalPayment.get();
        checkPaymentFields(payment, dto);
        return payment;
    }

    private void checkPaymentFields(Payment payment, InvoiceDto dto) {
        List<RuntimeException> exceptions = new ArrayList<>();
        if (!payment.getSenderAccount().equals(dto.getSenderAccount())) {
            exceptions.add(new IllegalArgumentException("Sender account does not match"));
        }
        if (!payment.getReceiverAccount().equals(dto.getReceiverAccount())) {
            exceptions.add(new IllegalArgumentException("Receiver account does not match"));
        }
        if (!payment.getCurrency().equals(dto.getCurrency())) {
            exceptions.add(new IllegalArgumentException("Currency does not match"));
        }
        if (!payment.getAmount().equals(dto.getAmount())) {
            exceptions.add(new IllegalArgumentException("Amount does not match"));
        }
        if (exceptions.isEmpty()) {
            return;
        }
        IllegalArgumentException exception = new IllegalArgumentException();
        exceptions.forEach(exception::addSuppressed);
        throw exception;
    }

    private void sendEvent(Payment payment) {
        PaymentEvent event = paymentMapper.toEvent(payment);
        eventPublisher.publish(event);
        log.info("Sent event: {}", event);
    }
}
