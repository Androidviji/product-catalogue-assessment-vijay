package com.example.catalogue.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
 */
@Component
public class FakeStoreProductSource implements ProductSource {

    private final RestClient restClient;

    @Autowired
    public FakeStoreProductSource(FakeStoreProperties properties) {
        this(createRestClient(properties, RestClient.builder()));
    }

    /** Used by tests to supply a RestClient bound to a mock server. */
    FakeStoreProductSource(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Builds a RestClient with explicit connect and read (response) timeouts. */
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
