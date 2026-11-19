# Catalogue backend

Java 21 + Spring Boot 4.1. Serves catalogue data to the Android app and emails customer enquiries
to a sales inbox. Product data comes from the [Fake Store API](https://fakestoreapi.com) through this
backend; the app never calls Fake Store directly, not even for images.

## Prerequisites (Windows)

- JDK 21 (`java -version` should print 21). Maven is **not** needed; the Maven wrapper (`mvnw.cmd`) downloads it.
- Docker Desktop, for Mailpit (the local test inbox used by enquiries).
- Internet access while running, because products come from fakestoreapi.com. The tests do not need it.

## Run (PowerShell, from the repo root)

```powershell
# 1. Start the local test inbox (Docker Desktop must be running)
docker compose up -d mailpit

# 2. Start the backend
cd backend
.\mvnw.cmd spring-boot:run
```

The backend listens on http://localhost:8080. Mailpit's inbox is at http://localhost:8025.
Stop the backend with `Ctrl+C` and Mailpit with `docker compose down` (from the repo root).

> **Mailpit is a local test inbox, not a mail service.** It accepts every message the backend sends and shows
> it at http://localhost:8025, but it **never delivers anything to a real mailbox**. The default sales inbox
> `sales@officeworks.test` does not exist. Sending to a real Officeworks sales mailbox needs a real SMTP server;
> see [Configuration](#configuration).

## Test and build

```powershell
cd backend
.\mvnw.cmd test      # unit and integration tests (no internet or Docker needed)
.\mvnw.cmd verify    # tests + builds target\catalogue-backend-0.0.1-SNAPSHOT.jar
```

## Configuration

Spring Boot reads environment variables. Set them in the same PowerShell window before starting:

```powershell
$env:ENQUIRY_SALES_INBOX = "someone@example.com"
.\mvnw.cmd spring-boot:run
```

Remove an override with `Remove-Item Env:ENQUIRY_SALES_INBOX`. Full list with defaults in `../.env.example`.

| Variable | Default | Meaning |
|---|---|---|
| `FEATURED_PRODUCT_IDS` | `1,5,9,17,20` | Home page products, in display order |
| `FAKE_STORE_BASE_URL` | `https://fakestoreapi.com` | Product source; images are only fetched from this host |
| `FAKE_STORE_CONNECT_TIMEOUT` / `_READ_TIMEOUT` | `3s` / `5s` | Product and image request timeouts |
| `ENQUIRY_SALES_INBOX` | `sales@officeworks.test` | Where enquiries are emailed |
| `ENQUIRY_FROM_ADDRESS` | `catalogue-app@officeworks.test` | Sender address of enquiry emails |
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1025` | SMTP server (Mailpit by default) |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | empty | SMTP login, if the server needs one. Never commit these |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `false` / `false` | Set both to `true` for a real provider |
| `SERVER_PORT` | `8080` | Backend port |

SMTP connect, read and write timeouts are 5 seconds each.

## API

Errors from `/api/...` use the same JSON shape ([RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457))
and always include a `code` the app can switch on. Error responses never contain stack traces, internal
messages or upstream URLs, and our own messages never echo request values (Spring's default 404 detail does name the requested path).

```json
{ "status": 404, "title": "Product not found", "detail": "No product exists with ID 999.",
  "code": "PRODUCT_NOT_FOUND", "instance": "/api/products/999" }
```

Where the `code` comes from:

| Kind of error | `code` | Example |
|---|---|---|
| Catalogue and enquiry failures | a specific code | `PRODUCT_NOT_FOUND`, `EMAIL_SEND_FAILED`, ... (table below) |
| Invalid enquiry fields | `VALIDATION_FAILED` + `errors` map | `{"errors": {"email": "must be a well-formed email address"}}` |
| Request body missing, not JSON, or a field of the wrong type | `MALFORMED_REQUEST` (400) | `{ not json`, `"productId": "abc"` |
| Path or query value of the wrong type | `INVALID_PARAMETER` (400) | `/api/products/abc`, with detail `The value of 'id' is not valid.` |
| Other framework errors | the HTTP status name | `NOT_FOUND` (unknown URL), `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` |
| Anything unexpected | `INTERNAL_ERROR` (500) | generic detail `Something went wrong. Please try again later.`; full error logged on the server |

The app should handle the specific codes it cares about and treat any other code as a generic error,
using `status`.

| Endpoint | Success | Errors (`code`) |
|---|---|---|
| `GET /api/products/featured` | 200 | 503 `PRODUCT_SOURCE_UNAVAILABLE` |
| `GET /api/categories` | 200 | 503 `PRODUCT_SOURCE_UNAVAILABLE` |
| `GET /api/categories/{category}/products` | 200 | 404 `CATEGORY_NOT_FOUND`, 503 |
| `GET /api/products/search?q=text` | 200 (may be empty) | 400 `INVALID_SEARCH_QUERY`, 503 |
| `GET /api/products/{id}` | 200 | 404 `PRODUCT_NOT_FOUND`, 400 `INVALID_PARAMETER` (non-numeric ID), 503 |
| `GET /api/products/{id}/image` | 200 image bytes | 404 `PRODUCT_NOT_FOUND` / `IMAGE_NOT_FOUND`, 503 `IMAGE_UNAVAILABLE` / `PRODUCT_SOURCE_UNAVAILABLE` |
| `POST /api/enquiries` | 200 | 400 `VALIDATION_FAILED`, 400 `MALFORMED_REQUEST`, 404 `PRODUCT_NOT_FOUND`, 415 `UNSUPPORTED_MEDIA_TYPE`, 503 `EMAIL_SEND_FAILED`, 503 |

A "product" in every response has this shape. It deliberately has no image URL; use `/api/products/{id}/image`.

```json
{ "id": 1, "title": "Fjallraven - Foldsack No. 1 Backpack, Fits 15 Laptops", "price": 109.95,
  "description": "...", "category": "men's clothing", "rating": 3.9, "ratingCount": 120 }
```

### `GET /api/products/featured`

The products the business owner chose for the home page (`FEATURED_PRODUCT_IDS`), in configured order.

```json
{ "configuredCount": 5, "products": [ { "id": 1, "...": "..." } ], "missingProductIds": [] }
```

If a configured ID does not exist, it is left out of `products`, listed in `missingProductIds`, and logged.

### `GET /api/categories`

Categories that currently have at least one product, sorted alphabetically.

```json
{ "categories": ["electronics", "jewelery", "men's clothing", "women's clothing"] }
```

("jewelery" is Fake Store's spelling.)

### `GET /api/categories/{category}/products`

The three highest-rated products in the category, then all the remaining ones.

```json
{ "category": "electronics",
  "topRated":      [ {"id": 12, "rating": 4.8, "ratingCount": 400, "...": "..."},
                     {"id": 11, "rating": 4.8, "ratingCount": 319, "...": "..."},
                     {"id": 9,  "rating": 3.3, "...": "..."} ],
  "otherProducts": [ {"id": 10, "...": "..."}, {"id": 13, "...": "..."}, {"id": 14, "...": "..."} ] }
```

- **Order:** rating (highest first), then number of ratings (more first), then ID (lowest first).
  Both groups use this order. The tie-breakers matter with real data: products 11 and 12 are both rated 4.8.
- **No overlap:** the whole category is sorted once and split after the third product, so a product can
  never be in both groups. `topRated` + `otherProducts` is exactly the category.
- **Fewer than four products:** all of them are in `topRated`, and `otherProducts` is `[]`.
- **Matching:** the name is trimmed and matched case-insensitively against the exact category name, so
  `MEN'S CLOTHING` works but `electronic` does not. The response always returns the source's spelling.
- **URL encoding:** names contain spaces and apostrophes, so the app must URL-encode them:
  `men's clothing` becomes `men's%20clothing` (or `men%27s%20clothing`; both work).
- **Unknown or empty category:** `404 CATEGORY_NOT_FOUND`. Categories come from the product list, so a
  category with no products does not exist for this API. It is never listed and returns the same 404.

### `GET /api/products/search?q=text`

```json
{ "query": "backpack", "products": [ { "id": 1, "...": "..." } ] }
```

- The whole query is trimmed and matched **as one phrase**, ignoring case, against the title,
  description and category. It is a substring match: `q=gold ring` matches "Gold Ring", but `q=ring gold`
  does not, even though both words appear.
- Results are in source (ID) order.
- **No matches:** `200` with `"products": []`.
- **Blank or missing `q`** (empty, or only spaces): `400 INVALID_SEARCH_QUERY`. The same applies to more
  than 100 characters after trimming.

### `GET /api/products/{id}`

One product, or `404 PRODUCT_NOT_FOUND`. A non-numeric ID returns `400 INVALID_PARAMETER`.

### `GET /api/products/{id}/image`

The product's image bytes (for example `Content-Type: image/png`), fetched by the backend. The response
has `Cache-Control: max-age=3600` and `X-Content-Type-Options: nosniff`.

The client only supplies a product ID. The image URL comes from the product data, and the backend applies
these restrictions before and while fetching it:

- **Same host only:** the URL must have the same scheme, host and port as `FAKE_STORE_BASE_URL`
  (`https://fakestoreapi.com`). Anything else is refused without a request being made.
- **No redirects:** a redirect is treated as unavailable, so the backend cannot be sent to another host.
- **Allowed types:** only `image/png`, `image/jpeg`, `image/gif` and `image/webp`. SVG is refused because
  it can contain scripts.
- **Size:** at most 5 MB. Fake Store images are about 0.3 MB.

| Situation | Response |
|---|---|
| Product does not exist | 404 `PRODUCT_NOT_FOUND` |
| Product has no image URL, the URL is untrusted, or Fake Store returns 404 for it | 404 `IMAGE_NOT_FOUND` |
| Timeout, connection failure, upstream 5xx, redirect, wrong content type, too large | 503 `IMAGE_UNAVAILABLE` |
| Product list itself unavailable | 503 `PRODUCT_SOURCE_UNAVAILABLE` |

### `POST /api/enquiries`

Request (`Content-Type: application/json`):

```json
{ "productId": 1, "customerName": "Sam Lee", "email": "sam@example.com", "message": "Is this in stock?" }
```

| Field | Rules |
|---|---|
| `productId` | required, positive, must be an existing product (otherwise 404) |
| `customerName` | required, not blank, at most 100 characters, no line breaks or control characters |
| `email` | required, a valid email address, at most 254 characters |
| `message` | optional, at most 2000 characters, line breaks allowed |

Success, returned **only after the SMTP server has accepted the email**:

```json
{ "enquiryId": "59ab5db6-282f-401b-9eee-d594bcfad0ae", "status": "SENT" }
```

Validation failure: every invalid field is listed, and no email is sent:

```json
{ "status": 400, "title": "Invalid request", "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED",
  "errors": { "customerName": "must not be blank", "email": "must be a well-formed email address",
              "productId": "must be greater than 0" } }
```

SMTP server unreachable, slow (5s) or rejecting the message:

```json
{ "status": 503, "title": "Enquiry not sent", "code": "EMAIL_SEND_FAILED",
  "detail": "Your enquiry could not be sent right now. Please try again later." }
```

The email goes to `ENQUIRY_SALES_INBOX`, with Reply-To set to the customer's address. Its subject is
`Product enquiry: <title> (product <id>)`, and its plain-text body contains the product (ID, title,
category, price), the customer's name and email, the message, and the enquiry reference. Product details
come from the product source, not from the request.

**Logging:** enquiry log lines contain only the enquiry reference, the product ID and, on failure, the
exception type. For example `Enquiry 59ab5db6-... for product 1 accepted by the mail server`, or
`Enquiry 59ab5db6-... for product 1 was not sent: ConnectException`. The SMTP provider's error message is
**not** logged, because it can echo email addresses or message content. These lines never include the
customer's name, email address or message, SMTP credentials, or a stack trace. To diagnose a send failure,
use the exception type and check the SMTP server's own logs (for Mailpit, http://localhost:8025).

## Example requests (Windows PowerShell)

Start the backend and Mailpit first (see [Run](#run-powershell-from-the-repo-root)). These were checked in
Windows PowerShell 5.1. `Invoke-RestMethod` throws on error statuses, so the error examples use `curl.exe`
(built into Windows 10/11), or read the error body from the exception.

```powershell
$api = "http://localhost:8080/api"

# Featured products
Invoke-RestMethod "$api/products/featured" | ConvertTo-Json -Depth 5

# Categories
Invoke-RestMethod "$api/categories" | ConvertTo-Json

# Products in a category (URL-encode the name)
$category = [uri]::EscapeDataString("men's clothing")
$result = Invoke-RestMethod "$api/categories/$category/products"
$result.topRated      | Select-Object id, title, rating, ratingCount
$result.otherProducts | Select-Object id, title, rating, ratingCount

# Search
$q = [uri]::EscapeDataString("backpack")
(Invoke-RestMethod "$api/products/search?q=$q").products | Select-Object id, title

# One product
Invoke-RestMethod "$api/products/1"

# Image: save it and open it
$file = Join-Path $env:TEMP "product-1.png"
Invoke-WebRequest "$api/products/1/image" -OutFile $file -UseBasicParsing
Start-Process $file

# Error cases (curl.exe prints the JSON error body)
curl.exe -s "$api/products/999"                 # 404 PRODUCT_NOT_FOUND
curl.exe -s "$api/products/999/image"           # 404 PRODUCT_NOT_FOUND
curl.exe -s "$api/categories/toys/products"     # 404 CATEGORY_NOT_FOUND
curl.exe -s "$api/products/search?q=%20"        # 400 INVALID_SEARCH_QUERY
curl.exe -s "$api/products/search?q=bicycle"    # 200 with empty products

# Enquiry: then open http://localhost:8025 to see the email
$body = @{
    productId    = 1
    customerName = "Test Customer"
    email        = "test.customer@example.com"
    message      = "Is this backpack in stock?"
} | ConvertTo-Json
Invoke-RestMethod "$api/enquiries" -Method Post -ContentType "application/json" -Body $body

# Invalid enquiry: shows the 400 body with the list of invalid fields
try {
    Invoke-RestMethod "$api/enquiries" -Method Post -ContentType "application/json" `
        -Body '{"productId":1,"customerName":"","email":"nope"}'
} catch {
    $_.Exception.Response.StatusCode.value__
    (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd()
}

# List what Mailpit has received (Mailpit's own API)
(Invoke-RestMethod "http://localhost:8025/api/v1/messages").messages | Select-Object Subject, Created
```

To see the email-failure response, run the backend with an SMTP port where nothing listens, for example
`$env:SMTP_PORT = "1"` before `.\mvnw.cmd spring-boot:run`, then send the enquiry again. You should get
`503 EMAIL_SEND_FAILED`.

## Assumptions and limitations

- **Error codes for framework errors** other than malformed bodies and wrong parameter types are the HTTP
  status name, for example `NOT_FOUND`, and their `detail` is Spring's default wording. Errors raised outside
  Spring MVC, such as a failure in the servlet container before a controller is chosen, go to Spring Boot's
  `/error` page. That page never includes stack traces or messages, but it has no `code`.

- **No caching.** Every request fetches the full product list (20 products) from Fake Store, and every
  image request fetches the image again. This is simple and always current. A short in-memory cache
  would be the first change for real traffic. The image response's `Cache-Control` lets the app cache images.
- **Lookups by ID use the full list,** because Fake Store returns `200` with an empty body for unknown IDs
  instead of a 404.
- **Search is a simple phrase (substring) match.** It has no relevance ranking, word splitting, stemming
  or typo tolerance. Because it is a substring match, `men's clothing` also matches `women's clothing`, and short
  queries such as `a` match almost everything.
- **Categories are derived from products,** not from Fake Store's `/products/categories` endpoint, so they
  always agree with the product data and are never empty.
- **Enquiries are sent synchronously.** The request waits for the SMTP server (up to 5s per step). There is no
  retry or queue, so if SMTP is down the customer gets a 503 and must try again. Nothing is stored.
- **No spam protection or rate limiting** on the enquiry endpoint, and no authentication, as agreed for a
  proof of concept. A public deployment would need rate limiting at least.
- **Email addresses** are checked for format only, not for whether the mailbox exists.
- **Mailpit** is for local testing only (see above). A real provider needs `SMTP_HOST`, `SMTP_PORT`,
  `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH=true` and `SMTP_STARTTLS=true`, set as environment variables.
- **Image hosts:** only the Fake Store base URL's host is trusted. If the product source moved images to a
  CDN on another host, that host would need to be explicitly allowed in `FakeStoreProductSource`.
- **Dependent on Fake Store availability.** There is no fallback catalogue; the app shows an error and a
  retry option instead.

## Code layout

```
src/main/java/com/example/catalogue/
  product/
    ProductController         /api/products/... endpoints (featured, search, details, image)
    CategoryController        /api/categories/... endpoints
    CatalogueService          business rules: featured, categories + rating order, search, lookup
    ProductImageService       product ID -> trusted image URL -> image bytes
    ProductSource             interface: "where products come from"
    ProductImageSource        interface: "fetch an image URL that came from the product source"
    FakeStoreProductSource    implements both; the only class that knows Fake Store (URLs, JSON, image rules)
    Product, ProductImage,
    CategoryProducts,
    FeaturedProducts          internal models
    *Response                 the API contract sent to the app
    *Exception                failures, mapped to HTTP errors by ApiExceptionHandler
    CatalogueProperties,
    FakeStoreProperties       typed configuration
  enquiry/
    EnquiryController         POST /api/enquiries (+ @Valid)
    EnquiryRequest            request body with validation rules
    EnquiryService            looks up the product, builds the email, logs without customer data
    EnquiryEmail              provider-neutral email (from, to, replyTo, subject, body)
    EnquiryEmailSender        interface: swap the email provider by adding an implementation
    SmtpEnquiryEmailSender    SMTP implementation (Spring JavaMailSender)
    EnquiryProperties         sales inbox and sender address
  common/
    ApiExceptionHandler       maps every failure to a problem-details JSON error
```

## Enquiry request flow

1. `EnquiryController` receives the JSON. `@Valid` checks `EnquiryRequest`. If a field is invalid, the client
   gets `400 VALIDATION_FAILED` and nothing else happens.
2. `EnquiryService` looks up the product through `CatalogueService`, so the email uses trusted product data.
   An unknown product returns 404, and a Fake Store outage returns 503.
3. It creates an enquiry reference (UUID) and builds an `EnquiryEmail` addressed to the sales inbox,
   with Reply-To set to the customer.
4. `EnquiryEmailSender.send` is called. The SMTP implementation returns only when the SMTP server has
   accepted the message.
5. Accepted: the service logs the reference and product ID, and the client gets `200 {"status":"SENT"}`.
   Not accepted: the service logs the reference, product ID and the exception type (not the provider's message), and the
   client gets `503 EMAIL_SEND_FAILED`.
