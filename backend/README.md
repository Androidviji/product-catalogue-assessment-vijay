# Catalogue backend

Java 21 + Spring Boot 4.1. Serves catalogue data to the Android app. Product data comes from the
[Fake Store API](https://fakestoreapi.com) through this backend; the app never calls Fake Store directly.

## Prerequisites (Windows)

- JDK 21 (`java -version` should print 21). Maven is **not** needed; the Maven wrapper (`mvnw.cmd`) downloads it.
- Docker Desktop (only for Mailpit, used by the enquiry stage).

## Run (PowerShell, from the repo root)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Then, in a second PowerShell window:

```powershell
Invoke-RestMethod http://localhost:8080/api/products/featured | ConvertTo-Json -Depth 5
```

Stop the backend with `Ctrl+C`.

### Override configuration (optional)

Spring Boot reads environment variables. Set them in the same window before starting:

```powershell
$env:FEATURED_PRODUCT_IDS = "1,999,5"   # 999 does not exist: see missingProductIds in the response
.\mvnw.cmd spring-boot:run
```

Remove the override with `Remove-Item Env:FEATURED_PRODUCT_IDS`. All settings are listed in `../.env.example`.

## Test

```powershell
cd backend
.\mvnw.cmd test
```

The tests do not need internet access or Docker.

## Mailpit (local email inbox, for the enquiry stage)

```powershell
# from the repo root, with Docker Desktop running
docker compose up -d mailpit
```

Open http://localhost:8025 to see the inbox. Stop it with `docker compose down`.

## API

### `GET /api/products/featured`

The products the business owner chose for the home page (`catalogue.featured-product-ids`), in configured order.

```json
{
  "configuredCount": 5,
  "products": [
    { "id": 1, "title": "...", "price": 109.95, "description": "...",
      "category": "men's clothing", "rating": 3.9, "ratingCount": 120 }
  ],
  "missingProductIds": []
}
```

If a configured ID does not exist in the product source, it is left out of `products`, listed in
`missingProductIds`, and logged as a warning. The other products are still returned in order.

If Fake Store is unreachable, slow (connect timeout 3s, read timeout 5s) or returns unusable data,
the response is `503`:

```json
{ "status": 503, "title": "Product source unavailable",
  "detail": "Products are temporarily unavailable. Please try again shortly.",
  "code": "PRODUCT_SOURCE_UNAVAILABLE", "instance": "/api/products/featured" }
```

## Code layout

```
src/main/java/com/example/catalogue/
  product/
    ProductController         HTTP endpoint (no business logic)
    CatalogueService          business rules (featured selection, missing IDs)
    ProductSource             interface: "where products come from"
    FakeStoreProductSource    the Fake Store implementation (only class that knows Fake Store)
    Product                   our internal product model
    ProductResponse,
    FeaturedProductsResponse  the API contract sent to the app
    CatalogueProperties,
    FakeStoreProperties       typed configuration from application.yml
  common/
    ApiExceptionHandler       maps failures to clean JSON errors
```
