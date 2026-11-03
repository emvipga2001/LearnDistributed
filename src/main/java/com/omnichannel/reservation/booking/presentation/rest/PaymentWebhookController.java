package com.omnichannel.reservation.booking.presentation.rest;

import com.omnichannel.reservation.booking.application.port.in.ProcessPaymentResultUseCase;
import com.omnichannel.reservation.booking.presentation.dto.PaymentWebhookRequest;
import com.omnichannel.reservation.booking.presentation.dto.PaymentWebhookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Webhooks", description = "Endpoints tiếp nhận kết quả thanh toán từ các Payment Gateway")
public class PaymentWebhookController {

    private final ProcessPaymentResultUseCase processPaymentResultUseCase;

    @PostMapping("/webhook")
    @Operation(
            summary = "Xử lý Webhook kết quả thanh toán",
            description = "Tiếp nhận callback từ cổng thanh toán (VNPay / MoMo / Stripe). Điều phối Saga để xác nhận đơn hoặc kích hoạt bồi hoàn."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook được tiếp nhận và xử lý thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu payload không hợp lệ hoặc vi phạm máy trạng thái (đơn quá hạn)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy mã đơn đặt chỗ")
    })
    public ResponseEntity<PaymentWebhookResponse> handlePaymentWebhook(
            @Valid @RequestBody PaymentWebhookRequest request
    ) {
        log.info("Nhận Webhook thanh toán cho đơn: {}, txnId: {}, status: {}",
                request.reservationCode(), request.paymentTransactionId(), request.status());

        String message = switch (request.status()) {
            case SUCCESS -> {
                processPaymentResultUseCase.handlePaymentSuccess(request.reservationCode(), request.paymentTransactionId());
                yield "Payment success: Reservation confirmed";
            }
            case FAILED -> {
                String reason = request.failureReason() != null ? request.failureReason() : "Payment failed at gateway";
                processPaymentResultUseCase.handlePaymentFailed(request.reservationCode(), reason);
                yield "Payment failure recorded: Reservation cancelled";
            }
        };
        return ResponseEntity.ok(PaymentWebhookResponse.success(message));
    }
}
