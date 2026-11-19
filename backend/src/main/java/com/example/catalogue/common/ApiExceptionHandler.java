package com.example.catalogue.common;

import com.example.catalogue.product.ProductSourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns known failures into clear JSON errors (RFC 9457 "problem details").
 * Technical details are logged on the server, never sent to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductSourceUnavailableException.class)
    public ProblemDetail handleProductSourceUnavailable(ProductSourceUnavailableException ex) {
        log.warn("Product source unavailable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Products are temporarily unavailable. Please try again shortly.");
        problem.setTitle("Product source unavailable");
        problem.setProperty("code", "PRODUCT_SOURCE_UNAVAILABLE");
        return problem;
    }
}
