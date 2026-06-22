package com.yasirkhan.trip.exceptions;

import com.yasirkhan.trip.responses.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataBaseException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(DataBaseException ex, HttpServletRequest request) {

        ErrorResponse response =
                ErrorResponse
                        .builder()
                        .message(ex.getMessage())
                        .status(ex.getStatus().value())
                        .error(ex.getStatus().getReasonPhrase())
                        .path(request.getRequestURI())
                        .timeStamp(LocalDateTime.now())
                        .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({UnauthorizedException.class, io.jsonwebtoken.JwtException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationExceptions(Exception exception, HttpServletRequest request) {

        // If it's a raw JwtException (like signature tampered), customize the message
        String message = (exception instanceof io.jsonwebtoken.JwtException)
                ? "Invalid or Expired JWT Token"
                : exception.getMessage();

        ErrorResponse response =
                ErrorResponse
                        .builder()
                        .message(message)
                        .status(HttpStatus.UNAUTHORIZED.value()) // 401
                        .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                        .timeStamp(LocalDateTime.now())
                        .path(request.getRequestURI())
                        .build();

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e,  HttpServletRequest request) {

        ErrorResponse response =
                ErrorResponse
                        .builder()
                        .message(e.getMessage())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .path(request.getRequestURI())
                        .timeStamp(LocalDateTime.now())
                        .build();
        return  new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles cases where getReferenceById() fails to find the target entity.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        ErrorResponse errorResponse =
                ErrorResponse
                        .builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .path(ex.getMessage())
                        .message("The requested resource (like the Shift Template) does not exist in the database.")
                        .timeStamp(LocalDateTime.now())
                        .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles cases where the database blocks the save due to a bad Foreign Key
     * (e.g., the UUID was valid format, but missing from the parent table).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorResponse errorResponse =
                ErrorResponse
                        .builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .path(ex.getMessage())
                        .message( "Cannot save record: invalid relation. Please ensure the provided Template, Driver, and Route IDs are correct.")
                        .timeStamp(LocalDateTime.now())
                        .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * A generic fallback for any other unexpected crashes.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse errorResponse =
                ErrorResponse
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                        .path(ex.getMessage())
                        .message( "An unexpected error occurred in the Schedule Service: " + ex.getMessage())
                        .timeStamp(LocalDateTime.now())
                        .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
