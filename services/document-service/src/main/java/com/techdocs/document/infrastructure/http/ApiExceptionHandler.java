package com.techdocs.document.infrastructure.http;

import com.techdocs.document.domain.errors.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException exception) {
        return ResponseEntity.status(exception.httpStatus())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413)
                .body(new ApiError("FILE_TOO_LARGE", "El archivo supera el tamano maximo permitido."));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "No se pudo completar la operacion."));
    }
}
