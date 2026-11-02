package com.omnichannel.reservation.booking.application.port.in;

public interface ProcessPaymentResultUseCase {
    void handlePaymentSuccess(String reservationCode, String paymentTransactionId);
    void handlePaymentFailed(String reservationCode, String reason);
}
