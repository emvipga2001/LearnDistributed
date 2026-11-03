package com.omnichannel.reservation.booking.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload nhận từ Webhook cổng thanh toán (VNPay / MoMo / Stripe)")
public record PaymentWebhookRequest(
        @NotBlank(message = "Reservation code is required")
        @Schema(description = "Mã đơn đặt chỗ cần thanh toán", example = "RES-20261102-123456")
        String reservationCode,

        @NotBlank(message = "Payment transaction ID is required")
        @Schema(description = "Mã giao dịch từ cổng thanh toán / ngân hàng", example = "TXN-VNPAY-998877")
        String paymentTransactionId,

        @NotNull(message = "Payment status is required")
        @Schema(description = "Trạng thái thanh toán từ cổng", example = "SUCCESS")
        PaymentWebhookStatus status,

        @Schema(description = "Lý do thất bại nếu thanh toán không thành công", example = "INSUFFICIENT_FUNDS")
        String failureReason
) {
    public enum PaymentWebhookStatus {
        SUCCESS,
        FAILED
    }
}
