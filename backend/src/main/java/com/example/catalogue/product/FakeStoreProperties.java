package com.example.catalogue.product;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the Fake Store API (bound from "fake-store.*" in application.yml). */
@ConfigurationProperties("fake-store")
public record FakeStoreProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
