package com.example.wgwarden.config;

import com.example.wgwarden.auth.EmailAlreadyTakenException;
import com.example.wgwarden.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.example.wgwarden.server.ServerNotFoundException;
import com.example.wgwarden.ssh.SshConnectionException;
import com.example.wgwarden.peer.PeerNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String error) {
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    ResponseEntity<ErrorResponse> emailTaken(EmailAlreadyTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid request body"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(ServerNotFoundException.class)
    ResponseEntity<ErrorResponse> serverNotFound(ServerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(PeerNotFoundException.class)
    ResponseEntity<ErrorResponse> peerNotFound(PeerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(SshConnectionException.class)
    ResponseEntity<ErrorResponse> provisioningFailure(SshConnectionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
    }
}
