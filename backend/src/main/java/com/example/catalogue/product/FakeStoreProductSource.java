package com.example.catalogue.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ProductSource backed by https://fakestoreapi.com.
 *
 * This is the only class that knows Fake Store's URLs and JSON shape.
 * It fetches the full list in one call (Fake Store has ~20 products) and maps it
 * into our Product model. Any network, timeout, HTTP error or unreadable response
 * becomes a ProductSourceUnavailableException.
 *
 * It also fetches product images, but only from the same host as the configured
 * Fake Store base URL (see fetchImage).
 */
@Component
public class FakeStoreProductSource implements ProductSource, ProductImageSource {

    /** Fake Store images are about 0.3 MB; anything above this is refused. */
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    /** SVG is deliberately excluded: it can contain scripts. */
    static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final RestClient restClient;
    private final URI baseUri;

    @Autowired
    public FakeStoreProductSource(FakeStoreProperties properties) {
        this(createRestClient(properties, RestClient.builder()), properties.baseUrl());
    }

    /** Used by tests to supply a RestClient bound to a mock server. */
    FakeStoreProductSource(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUri = URI.create(baseUrl);
    }

    /**
     * Builds a RestClient with explicit connect and read (response) timeouts.
     * The JDK HttpClient does not follow redirects by default, so an image URL
     * cannot redirect the backend to another host.
     */
    static RestClient createRestClient(FakeStoreProperties properties, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<Product> findAll() {
        FakeStoreProduct[] response;
        try {
            response = restClient.get()
                    .uri("/products")
                    .retrieve()
                    .body(FakeStoreProduct[].class);
        } catch (RestClientException e) {
            // Covers connection failures, timeouts, 4xx/5xx and malformed JSON.
            throw new ProductSourceUnavailableException("Fake Store request failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new ProductSourceUnavailableException("Fake Store returned an empty response", null);
        }
        return Arrays.stream(response).map(FakeStoreProduct::toProduct).toList();
    }

    /**
     * Fetches an image URL taken from Fake Store product data.
     * Safety rules: same scheme and host as the configured base URL, no redirects,
     * only png/jpeg/gif/webp, at most MAX_IMAGE_BYTES.
     */
    @Override
    public ProductImage fetchImage(String sourceImageUrl) {
        URI uri = trustedImageUri(sourceImageUrl);
        try {
            return restClient.get().uri(uri).exchange((request, response) -> {
                if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    throw new ProductImageNotFoundException("Fake Store has no image at " + uri);
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new ProductImageUnavailableException(
                            "Fake Store image request returned " + response.getStatusCode(), null);
                }
                MediaType type = response.getHeaders().getContentType();
                String contentType = type == null ? "" : type.getType() + "/" + type.getSubtype();
                if (!ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                    throw new ProductImageUnavailableException("Unexpected image content type: " + type, null);
                }
                try (InputStream body = response.getBody()) {
                    byte[] bytes = body.readNBytes(MAX_IMAGE_BYTES + 1);
                    if (bytes.length > MAX_IMAGE_BYTES) {
                        throw new ProductImageUnavailableException("Image larger than " + MAX_IMAGE_BYTES + " bytes", null);
                    }
                    return new ProductImage(bytes, contentType);
                }
            });
        } catch (RestClientException e) {
            // Connection failures, timeouts and read errors.
            throw new ProductImageUnavailableException("Fake Store image request failed: " + e.getMessage(), e);
        }
    }

    /** Accepts only an absolute URL on the same scheme and host as the Fake Store base URL. */
    private URI trustedImageUri(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ProductImageNotFoundException("Image URL is not a valid URI");
        }
        boolean trusted = uri.getHost() != null
                && baseUri.getScheme().equalsIgnoreCase(uri.getScheme())
                && baseUri.getHost().equalsIgnoreCase(uri.getHost())
                && baseUri.getPort() == uri.getPort();
        if (!trusted) {
            throw new ProductImageNotFoundException("Image URL is not on the trusted product source host: " + uri.getHost());
        }
        return uri;
    }

    /** Fake Store's JSON shape. Private to this class so it never leaks into the API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FakeStoreProduct(
            int id,
            String title,
            BigDecimal price,
            String description,
            String category,
            String image,
            FakeStoreRating rating) {

        Product toProduct() {
            double rate = rating == null ? 0.0 : rating.rate();
            int count = rating == null ? 0 : rating.count();
            return new Product(id, title, price, description, category, image, rate, count);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FakeStoreRating(double rate, int count) {
    }
}
