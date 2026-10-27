package com.omnichannel.reservation.shared.presentation.exception;

import com.omnichannel.reservation.booking.domain.model.Reservation;
import com.omnichannel.reservation.inventory.domain.exception.HaveMerchandise;
import com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld;

import com.omnichannel.reservation.inventory.domain.exception.InventoryInsufficientException;
import com.omnichannel.reservation.booking.domain.exception.ReservationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryInsufficientException.class)
    public ProblemDetail handleInventoryInsufficient(InventoryInsufficientException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Inventory Conflict");
        problem.setType(URI.create("https://api.omnichannel.com/errors/inventory-insufficient"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld.class)
    public ProblemDetail handleReservationIsHeld(com.omnichannel.reservation.inventory.domain.exception.ReservationIsHeld ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Table Is Already Held");
        problem.setType(URI.create("https://api.omnichannel.com/errors/table-already-held"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(HaveMerchandise.class)
    public ProblemDetail handleHaveMerchandise(HaveMerchandise ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Merchandise Rule Violation");
        problem.setType(URI.create("https://api.omnichannel.com/errors/vip-merchandise-conflict"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Reservation Not Found");
        problem.setType(URI.create("https://api.omnichannel.com/errors/reservation-not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }
}
