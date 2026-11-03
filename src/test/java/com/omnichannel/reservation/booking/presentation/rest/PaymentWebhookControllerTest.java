package com.omnichannel.reservation.booking.presentation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.reservation.booking.application.port.in.ProcessPaymentResultUseCase;
import com.omnichannel.reservation.booking.domain.exception.InvalidReservationStateException;
import com.omnichannel.reservation.booking.domain.exception.ReservationNotFoundException;
import com.omnichannel.reservation.booking.presentation.dto.PaymentWebhookRequest;
import com.omnichannel.reservation.booking.presentation.dto.PaymentWebhookRequest.PaymentWebhookStatus;
import com.omnichannel.reservation.shared.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProcessPaymentResultUseCase processPaymentResultUseCase;

    @InjectMocks
    private PaymentWebhookController paymentWebhookController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Mockup: Ngân hàng báo SUCCESS -> Trả về 200 OK và gọi handlePaymentSuccess")
    void shouldReturn200_WhenPaymentSucceeds() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "RES-123456",
                "TXN-BANK-001",
                PaymentWebhookStatus.SUCCESS,
                null
        );

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Payment success: Reservation confirmed"));

        verify(processPaymentResultUseCase).handlePaymentSuccess("RES-123456", "TXN-BANK-001");
    }

    @Test
    @DisplayName("Mockup: Ngân hàng báo FAILED -> Trả về 200 OK và gọi handlePaymentFailed")
    void shouldReturn200_WhenPaymentFails() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "RES-123456",
                "TXN-BANK-002",
                PaymentWebhookStatus.FAILED,
                "Insufficient funds"
        );

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Payment failure recorded: Reservation cancelled"));

        verify(processPaymentResultUseCase).handlePaymentFailed("RES-123456", "Insufficient funds");
    }

    @Test
    @DisplayName("Mockup: Payload thiếu reservationCode -> Trả về 400 Bad Request")
    void shouldReturn400_WhenReservationCodeIsBlank() throws Exception {
        String invalidJson = """
                {
                    "reservationCode": "",
                    "paymentTransactionId": "TXN-001",
                    "status": "SUCCESS"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(processPaymentResultUseCase);
    }

    @Test
    @DisplayName("Mockup: Mã đơn không tồn tại -> Trả về 404 Not Found")
    void shouldReturn404_WhenReservationNotFound() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "RES-NONEXISTENT",
                "TXN-BANK-003",
                PaymentWebhookStatus.SUCCESS,
                null
        );

        doThrow(new ReservationNotFoundException("Không tìm thấy mã đặt chỗ"))
                .when(processPaymentResultUseCase).handlePaymentSuccess("RES-NONEXISTENT", "TXN-BANK-003");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Mockup: Đơn quá hạn 10 phút -> Trả về 400 Bad Request")
    void shouldReturn400_WhenReservationIsExpired() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "RES-EXPIRED-999",
                "TXN-BANK-004",
                PaymentWebhookStatus.SUCCESS,
                null
        );

        doThrow(new InvalidReservationStateException("Cannot confirm an expired reservation"))
                .when(processPaymentResultUseCase).handlePaymentSuccess("RES-EXPIRED-999", "TXN-BANK-004");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
