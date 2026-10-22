package com.omnichannel.reservation.presentation.rest;

import com.omnichannel.reservation.application.dto.ReservationResult;
import com.omnichannel.reservation.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.presentation.dto.CreateReservationRequest;
import com.omnichannel.reservation.presentation.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Endpoints for booking and reservation lifecycle")
public class ReservationController {

    private final ReserveInventoryUseCase reserveInventoryUseCase;

    @PostMapping
    @Operation(summary = "Create an inventory reservation", description = "Acquires hold lock on inventory items for specified hold duration")
    public ResponseEntity<ReservationResponse> reserve(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request) {

        List<ReserveInventoryCommand.ItemReservationRequest> itemCommands = request.items().stream()
                .map(it -> new ReserveInventoryCommand.ItemReservationRequest(
                        it.inventoryItemId(),
                        it.quantity(),
                        it.unitPrice(),
                        it.assignedSeat()))
                .toList();

        ReserveInventoryCommand command = new ReserveInventoryCommand(
                request.customerId(),
                request.channel(),
                Duration.ofSeconds(request.holdDurationSeconds() != null ? request.holdDurationSeconds() : 600),
                itemCommands
        );

        ReservationResult result = reserveInventoryUseCase.reserve(command);

        List<ReservationResponse.ItemResponse> itemResponses = result.items().stream()
                .map(it -> new ReservationResponse.ItemResponse(it.itemId(), it.inventoryItemId(), it.quantity()))
                .toList();

        ReservationResponse response = new ReservationResponse(
                result.reservationId(),
                result.reservationCode(),
                result.channel(),
                result.status(),
                result.customerId(),
                result.expiresAt(),
                itemResponses
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
