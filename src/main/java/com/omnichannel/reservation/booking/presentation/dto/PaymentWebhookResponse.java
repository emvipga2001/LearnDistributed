package com.omnichannel.reservation.booking.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phản hồi kết quả xử lý Webhook cho cổng thanh toán")
public record PaymentWebhookResponse(
        @Schema(description = "Trạng thái xử lý", example = "SUCCESS")
        String status,

        @Schema(description = "Thông điệp chi tiết", example = "Webhook processed successfully")
        String message
) {
    public static PaymentWebhookResponse success(String message) {
        return new PaymentWebhookResponse("SUCCESS", message);
    }
}
