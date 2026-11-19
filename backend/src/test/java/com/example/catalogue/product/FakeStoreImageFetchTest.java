package com.example.catalogue.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** FakeStoreProductSource.fetchImage: trusted-host rule, content checks and upstream failures. */
class FakeStoreImageFetchTest {

    private static final String BASE_URL = "https://fakestore.test";
    private static final String IMAGE_URL = BASE_URL + "/img/1.png";
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FakeStoreProductSource source = new FakeStoreProductSource(builder.build(), BASE_URL);

    @Test
    void returnsImageBytesAndContentTypeFromTrustedHost() {
        server.expect(requestTo(IMAGE_URL)).andRespond(withSuccess(PNG_BYTES, MediaType.IMAGE_PNG));

        ProductImage image = source.fetchImage(IMAGE_URL);

        assertThat(image.content()).isEqualTo(PNG_BYTES);
        assertThat(image.contentType()).isEqualTo("image/png");
        server.verify();
    }

    @Test
    void refusesUrlsOnOtherHostsWithoutMakingARequest() {
        // No expectations: MockRestServiceServer fails the test if any request is made.
        assertThatThrownBy(() -> source.fetchImage("https://evil.example/img/1.png"))
                .isInstanceOf(ProductImageNotFoundException.class);
        assertThatThrownBy(() -> source.fetchImage("http://fakestore.test/img/1.png"))   // wrong scheme
                .isInstanceOf(ProductImageNotFoundException.class);
        assertThatThrownBy(() -> source.fetchImage("https://fakestore.test:8443/img/1.png"))   // wrong port
                .isInstanceOf(ProductImageNotFoundException.class);
        assertThatThrownBy(() -> source.fetchImage("not a url"))
                .isInstanceOf(ProductImageNotFoundException.class);
        server.verify();
    }

    @Test
    void upstream404BecomesImageNotFound() {
        server.expect(requestTo(IMAGE_URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> source.fetchImage(IMAGE_URL)).isInstanceOf(ProductImageNotFoundException.class);
    }

    @Test
    void upstreamServerErrorBecomesImageUnavailable() {
        server.expect(requestTo(IMAGE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> source.fetchImage(IMAGE_URL)).isInstanceOf(ProductImageUnavailableException.class);
    }

    @Test
    void nonImageOrSvgContentBecomesImageUnavailable() {
        server.expect(requestTo(IMAGE_URL)).andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));
        server.expect(requestTo(IMAGE_URL)).andRespond(withSuccess("<svg/>", MediaType.valueOf("image/svg+xml")));

        assertThatThrownBy(() -> source.fetchImage(IMAGE_URL)).isInstanceOf(ProductImageUnavailableException.class);
        assertThatThrownBy(() -> source.fetchImage(IMAGE_URL)).isInstanceOf(ProductImageUnavailableException.class);
    }

    @Test
    void imageOverSizeLimitBecomesImageUnavailable() {
        byte[] tooBig = new byte[FakeStoreProductSource.MAX_IMAGE_BYTES + 1];
        server.expect(requestTo(IMAGE_URL)).andRespond(withSuccess(tooBig, MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> source.fetchImage(IMAGE_URL)).isInstanceOf(ProductImageUnavailableException.class);
    }

    @Test
    void doesNotFollowRedirects() throws IOException {
        // A real local HTTP server, because the mock server bypasses the HTTP client's redirect handling.
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicBoolean redirectTargetCalled = new AtomicBoolean(false);
        http.createContext("/img/1.png", exchange -> {
            exchange.getResponseHeaders().add("Location", "/elsewhere.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        http.createContext("/elsewhere.png", exchange -> {
            redirectTargetCalled.set(true);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG_BYTES.length);
            exchange.getResponseBody().write(PNG_BYTES);
            exchange.close();
        });
        http.start();
        try {
            String baseUrl = "http://127.0.0.1:" + http.getAddress().getPort();
            FakeStoreProperties properties = new FakeStoreProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(1));
            FakeStoreProductSource realSource = new FakeStoreProductSource(
                    FakeStoreProductSource.createRestClient(properties, RestClient.builder()), baseUrl);

            assertThatThrownBy(() -> realSource.fetchImage(baseUrl + "/img/1.png"))
                    .isInstanceOf(ProductImageUnavailableException.class);
            assertThat(redirectTargetCalled).isFalse();
        } finally {
            http.stop(0);
        }
    }

    @Test
    void unreachableHostBecomesImageUnavailable() {
        String baseUrl = "http://127.0.0.1:1";   // nothing listens on port 1
        FakeStoreProperties properties = new FakeStoreProperties(baseUrl, Duration.ofMillis(500), Duration.ofMillis(500));
        FakeStoreProductSource realSource = new FakeStoreProductSource(
                FakeStoreProductSource.createRestClient(properties, RestClient.builder()), baseUrl);

        assertThatThrownBy(() -> realSource.fetchImage(baseUrl + "/img/1.png"))
                .isInstanceOf(ProductImageUnavailableException.class);
    }
}
