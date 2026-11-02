package com.omnichannel.reservation.booking.presentation.rest;

import com.omnichannel.reservation.booking.application.dto.ReservationResult;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryCommand;
import com.omnichannel.reservation.booking.application.port.in.ReserveInventoryUseCase;
import com.omnichannel.reservation.booking.presentation.dto.CreateReservationRequest;
import com.omnichannel.reservation.booking.presentation.dto.ReservationResponse;
import com.omnichannel.reservation.shared.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reservations", description = "Endpoints for booking and reservation lifecycle")
public class ReservationController {

    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final IdempotencyService idempotencyService;

    @PostMapping
    @Operation(
            summary = "Create an inventory reservation",
            description = "Acquires hold lock on inventory items for specified hold duration. Supports distributed idempotency via X-Idempotency-Key header to prevent duplicate orders."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reservation created or safely replayed from cache"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or malformed idempotency key",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Inventory insufficient or concurrent idempotency conflict (in-flight)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Idempotency key payload mismatch (same key sent with different request body)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ReservationResponse> reserve(
            @Parameter(
                    in = ParameterIn.HEADER,
                    name = "X-Idempotency-Key",
                    description = "Khóa định danh giao dịch duy nhất (UUID v4) giúp ngăn chặn trùng lặp khi người dùng click nhiều lần hoặc mạng chập chờn retry.",
                    required = false,
                    schema = @Schema(
                            type = "string",
                            format = "uuid",
                            maxLength = 128,
                            example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
                    )
            )
            @RequestHeader(value = "X-Idempotency-Key", required = false)
            @Size(max = 128, message = "X-Idempotency-Key không được vượt quá 128 ký tự")
            @Pattern(
                    regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "X-Idempotency-Key phải đúng định dạng UUID v4 (ví dụ: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d)"
            )
            String idempotencyKey,

            @Valid @RequestBody CreateReservationRequest request) {

        List<ReserveInventoryCommand.ItemReservationRequest> itemCommands = request.items().stream()
                .map(it -> new ReserveInventoryCommand.ItemReservationRequest(
                        it.inventoryItemId(),
                        it.quantity(),
                        it.unitPrice(),
                        it.assignedSeat()))
                .toList();

        List<ReserveInventoryCommand.MerchandiseReservationRequest> merchandiseCommands = request.getSafeMerchandises().stream()
                .map(m -> new ReserveInventoryCommand.MerchandiseReservationRequest(m.merchandiseId(), m.quantity()))
                .toList();

        ReserveInventoryCommand command = new ReserveInventoryCommand(
                request.customerId(),
                request.channel(),
                Duration.ofSeconds(request.holdDurationSeconds() != null ? request.holdDurationSeconds() : 600),
                itemCommands,
                merchandiseCommands
        );

        ReservationResult result = idempotencyService.execute(
                idempotencyKey,
                "ReservationController.reserve",
                request,
                Duration.ofSeconds(120),
                ReservationResult.class,
                () -> reserveInventoryUseCase.reserve(command)
        );

        List<ReservationResponse.ItemResponse> itemResponses = result.items().stream()
                .map(it -> new ReservationResponse.ItemResponse(it.itemId(), it.inventoryItemId(), it.quantity()))
                .toList();

        List<ReservationResponse.MerchandiseResponse> merchandiseResponses = result.merchandises().stream()
                .map(m -> new ReservationResponse.MerchandiseResponse(m.id(), m.merchandiseId(), m.quantity(), m.unitPrice()))
                .toList();

        ReservationResponse response = new ReservationResponse(
                result.reservationId(),
                result.reservationCode(),
                result.channel(),
                result.status(),
                result.customerId(),
                result.expiresAt(),
                itemResponses,
                merchandiseResponses
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
