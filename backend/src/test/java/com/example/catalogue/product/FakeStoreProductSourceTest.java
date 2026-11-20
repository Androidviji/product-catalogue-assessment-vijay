package com.example.catalogue.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FakeStoreProductSourceTest {

    private static final String BASE_URL = "https://fakestore.test";

    private static final String FAKE_STORE_JSON = """
            [
              {
                "id": 1,
                "title": "Backpack",
                "price": 109.95,
                "description": "A backpack",
                "category": "men's clothing",
                "image": "https://fakestoreapi.com/img/1.png",
                "rating": { "rate": 3.9, "count": 120 },
                "someFieldWeDoNotUse": true
              }
            ]
            """;

    @Test
    void mapsFakeStoreJsonIntoOurProductModel() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fakestore.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fakestore.test/products"))
                .andRespond(withSuccess(FAKE_STORE_JSON, MediaType.APPLICATION_JSON));

        List<Product> products = new FakeStoreProductSource(builder.build(), BASE_URL).findAll();

        assertThat(products).containsExactly(new Product(1, "Backpack", new BigDecimal("109.95"), "A backpack",
                "men's clothing", "https://fakestoreapi.com/img/1.png", 3.9, 120));
        server.verify();
    }

    @Test
    void serverErrorBecomesProductSourceUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fakestore.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fakestore.test/products")).andRespond(withServerError());

        FakeStoreProductSource source = new FakeStoreProductSource(builder.build(), BASE_URL);

        assertThatThrownBy(source::findAll).isInstanceOf(ProductSourceUnavailableException.class);
    }

    @Test
    void malformedJsonBecomesProductSourceUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fakestore.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fakestore.test/products"))
                .andRespond(withSuccess("<html>not json</html>", MediaType.APPLICATION_JSON));

        FakeStoreProductSource source = new FakeStoreProductSource(builder.build(), BASE_URL);

        assertThatThrownBy(source::findAll).isInstanceOf(ProductSourceUnavailableException.class);
    }

    @Test
    void slowResponseTimesOutAndBecomesProductSourceUnavailable() throws IOException {
        // A local server that accepts the connection but never replies.
        try (ServerSocket silentServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(5_000);
                } catch (IOException | InterruptedException ignoredException) {
                    // test finished or socket closed
                }
            });

            FakeStoreProperties properties = new FakeStoreProperties(
                    "http://127.0.0.1:" + silentServer.getLocalPort(), Duration.ofSeconds(1), Duration.ofMillis(300));
            FakeStoreProductSource source = new FakeStoreProductSource(
                    FakeStoreProductSource.createRestClient(properties, RestClient.builder()), properties.baseUrl());

            long start = System.nanoTime();
            assertThatThrownBy(source::findAll).isInstanceOf(ProductSourceUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));

            acceptor.interrupt();
        }
    }
}
