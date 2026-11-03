package br.com.criativosia.controller;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ProblemDetail> storage(IOException ex) {
        LoggerFactory.getLogger(ApiErrors.class).error("Falha no armazenamento local", ex);
        return ResponseEntity.internalServerError().body(ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Falha no armazenamento local. Consulte o log do servidor."));
    }
}
