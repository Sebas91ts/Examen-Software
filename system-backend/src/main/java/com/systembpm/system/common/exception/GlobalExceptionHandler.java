package com.systembpm.system.common.exception;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentSizeExceededException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentUrlExpiredException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.InvalidDocumentAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleBadCredentials(BadCredentialsException ex) {
        return ApiResponse.error("Unauthorized", "Email o contrasena incorrectos");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return ApiResponse.error("Validation Error", errors);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleBindErrors(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return ApiResponse.error("Validation Error", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error("Bad Request", ex.getMessage());
    }

    @ExceptionHandler(DocumentValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleDocumentValidation(DocumentValidationException ex) {
        return ApiResponse.error("Bad Request", ex.getMessage());
    }

    @ExceptionHandler(DocumentSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<?> handleDocumentSize(DocumentSizeExceededException ex) {
        return ApiResponse.error("Payload Too Large", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleIllegalState(IllegalStateException ex) {
        return ApiResponse.error("Internal Server Error", ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ApiResponse.error("Not Found", ex.getMessage());
    }

    @ExceptionHandler(DocumentUrlExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ApiResponse<?> handleExpiredUrl(DocumentUrlExpiredException ex) {
        return ApiResponse.error("Gone", ex.getMessage());
    }

    @ExceptionHandler(DocumentRequesterNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleRequesterNotFound(DocumentRequesterNotFoundException ex) {
        return ApiResponse.error("Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(DocumentTenantAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleTenantAccessDenied(DocumentTenantAccessDeniedException ex) {
        return ApiResponse.error("Forbidden", ex.getMessage());
    }

    @ExceptionHandler(InvalidDocumentAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleInvalidAccess(InvalidDocumentAccessException ex) {
        return ApiResponse.error("Forbidden", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleRuntime(RuntimeException ex) {
        return ApiResponse.error("Internal Server Error", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception ex) {
        return ApiResponse.error("Internal Server Error", "Ocurrio un error inesperado");
    }
}
