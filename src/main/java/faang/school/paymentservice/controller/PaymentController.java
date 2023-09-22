package faang.school.paymentservice.controller;

import faang.school.paymentservice.dto.*;

import java.text.DecimalFormat;
import java.util.Random;

import faang.school.paymentservice.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payment")
    public ResponseEntity<PaymentResponse> sendPayment(@RequestBody @Validated PaymentRequest dto) {
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        String formattedSum = decimalFormat.format(dto.amount());
        int verificationCode = new Random().nextInt(1000, 10000);
        String message = String.format("Dear friend! Thank you for your purchase! " +
                        "Your payment on %s %s was accepted.",
                formattedSum, dto.currency().name());

        return ResponseEntity.ok(new PaymentResponse(
                PaymentStatus.SUCCESS,
                verificationCode,
                dto.paymentNumber(),
                dto.amount(),
                dto.currency(),
                message)
        );
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentDto> createPayment(@RequestBody @Validated InvoiceDto dto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.create(dto));
    }

    @DeleteMapping("{paymentId}/cancel")
    public ResponseEntity<PaymentDto> cancelPayment(@PathVariable Long paymentId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(paymentService.cancel(paymentId));
    }

    @PutMapping("{paymentId}/clear")
    public ResponseEntity<PaymentDto> clearPayment(@PathVariable Long paymentId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(paymentService.clear(paymentId));
    }
}
