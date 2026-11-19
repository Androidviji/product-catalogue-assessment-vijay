package com.example.catalogue.common;

import com.example.catalogue.enquiry.EmailDeliveryException;
import com.example.catalogue.product.CategoryNotFoundException;
import com.example.catalogue.product.InvalidSearchQueryException;
import com.example.catalogue.product.ProductImageNotFoundException;
import com.example.catalogue.product.ProductImageUnavailableException;
import com.example.catalogue.product.ProductNotFoundException;
import com.example.catalogue.product.ProductSourceUnavailableException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns known failures into clear JSON errors (RFC 9457 "problem details").
 * Every error has a stable "code" the app can switch on. Technical details are
 * logged on the server, never sent to the client.
 *
 * Extends ResponseEntityExceptionHandler so framework errors (malformed JSON, wrong
 * parameter type, unknown URL) also return problem details, and so validation errors
 * can list the invalid fields.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductSourceUnavailableException.class)
    public ProblemDetail handleProductSourceUnavailable(ProductSourceUnavailableException ex) {
        log.warn("Product source unavailable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Product source unavailable",
                "Products are temporarily unavailable. Please try again shortly.", "PRODUCT_SOURCE_UNAVAILABLE");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Product not found",
                "No product exists with ID " + ex.productId() + ".", "PRODUCT_NOT_FOUND");
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Category not found",
                "No products exist in this category. GET /api/categories lists the available categories.",
                "CATEGORY_NOT_FOUND");
    }

    @ExceptionHandler(InvalidSearchQueryException.class)
    public ProblemDetail handleInvalidSearchQuery(InvalidSearchQueryException ex) {
        // The message is written for the client (see CatalogueService.search).
        return problem(HttpStatus.BAD_REQUEST, "Invalid search", ex.getMessage(), "INVALID_SEARCH_QUERY");
    }

    @ExceptionHandler(ProductImageNotFoundException.class)
    public ProblemDetail handleImageNotFound(ProductImageNotFoundException ex) {
        log.warn("Product image not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Image not found",
                "This product has no image.", "IMAGE_NOT_FOUND");
    }

    @ExceptionHandler(ProductImageUnavailableException.class)
    public ProblemDetail handleImageUnavailable(ProductImageUnavailableException ex) {
        log.warn("Product image unavailable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Image unavailable",
                "The image is temporarily unavailable. Please try again shortly.", "IMAGE_UNAVAILABLE");
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ProblemDetail handleEmailDelivery(EmailDeliveryException ex) {
        // Already logged by EnquiryService without customer details; no stack trace here,
        // because SMTP exceptions can echo message addresses.
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Enquiry not sent",
                "Your enquiry could not be sent right now. Please try again later.", "EMAIL_SEND_FAILED");
    }

    /** Bean Validation failures on a request body: list each invalid field and why. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "One or more fields are invalid.", "VALIDATION_FAILED");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
