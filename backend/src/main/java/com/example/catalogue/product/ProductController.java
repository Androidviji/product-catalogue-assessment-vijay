package com.example.catalogue.product;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer only: translates requests into service calls and results into API responses. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogueService catalogueService;
    private final ProductImageService imageService;

    public ProductController(CatalogueService catalogueService, ProductImageService imageService) {
        this.catalogueService = catalogueService;
        this.imageService = imageService;
    }

    @GetMapping("/featured")
    public FeaturedProductsResponse featured() {
        return FeaturedProductsResponse.from(catalogueService.getFeaturedProducts());
    }

    /** "featured" and "search" are literal paths, so Spring matches them before "/{id}". */
    @GetMapping("/search")
    public SearchResponse search(@RequestParam(name = "q", required = false) String query) {
        var products = catalogueService.search(query).stream().map(ProductResponse::from).toList();
        return new SearchResponse(query.trim(), products);
    }

    @GetMapping("/{id}")
    public ProductResponse product(@PathVariable int id) {
        return ProductResponse.from(catalogueService.getProduct(id));
    }

    /** The image bytes, fetched by the backend from the trusted product source. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable int id) {
        ProductImage image = imageService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
