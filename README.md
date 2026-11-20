# Product Catalogue Assessment

A product catalogue proof of concept. Customers browse featured products and categories, search, view
product details and send an enquiry that is emailed to a sales inbox.

- **`backend/`**: Java 21 + Spring Boot. Serves products from the Fake Store API and emails customer
  enquiries over SMTP. Its API, configuration and limitations are in [`backend/README.md`](backend/README.md).
- **`android/`**: Kotlin + Jetpack Compose app (MVVM, practical Clean Architecture). It only talks to the
  backend; it never calls Fake Store, not even for images.
- **`docker-compose.yml`**: Mailpit, a local test inbox for enquiry emails.
- **[`DECISIONS.md`](DECISIONS.md)**: assumptions, simplifications, how the design can grow, known
  limitations, and what's next.
- **[`AI_USAGE.md`](AI_USAGE.md)**: how AI tools were used, key moments, and AI mistakes caught.
  The raw notes made during the build are in `docs/ai-log.md`.

## Technology choice

- **Client: Android app in Kotlin** (Jetpack Compose, MVVM). This assessment is for a Senior Android
  Engineer role.
- **Backend: Java 21 + Spring Boot.** The brief lists Node.js and PHP/Laravel. I received a clarification
  from Officeworks approving Java Spring Boot for the backend, and built on that basis.

Configuration uses environment variables, listed with their defaults in [`.env.example`](.env.example).
No secrets, accounts or paid services are needed; the defaults work as-is.

```
Android app  --HTTP-->  backend (localhost:8080)  --HTTPS-->  fakestoreapi.com (products, images)
                                                  --SMTP--->  Mailpit (localhost:1025, inbox UI on :8025)
```

## Prerequisites (Windows)

- **JDK 21** (`java -version`). Maven isn't needed; the backend uses the Maven wrapper.
- **Docker Desktop**, for Mailpit.
- **Android Studio** recent enough for Android Gradle Plugin 9.5.1, with **Android SDK Platform 37**
  (compileSdk/targetSdk 37) and an emulator. The app supports Android 8.0+ (minSdk 26). It was tested on
  an API 37 "Medium Phone" emulator.
- **Internet access** while running (fakestoreapi.com, and the first Maven and Gradle downloads).

The first backend build and the first Android Gradle sync download dependencies, so they take longest.
Setup time hasn't been measured on a clean machine.

## Run everything end to end

**1. Start Mailpit** (PowerShell, repo root; Docker Desktop running):

```powershell
docker compose up -d mailpit
```

Open http://localhost:8025 and leave it open. If another Mailpit container already uses ports 1025/8025,
use that one instead; the backend only needs something listening on localhost:1025.

> **Mailpit only captures email locally.** It accepts every message the backend sends over SMTP and shows
> it at http://localhost:8025, but it **never delivers anything to a real external mailbox**. The default
> sales inbox, `sales@officeworks.test`, is not a real address. Delivering to a real sales mailbox needs a
> real SMTP provider, configured with the `SMTP_*` and `ENQUIRY_*` environment variables
> (see [`backend/README.md`](backend/README.md#configuration)).

**2. Start the backend** (a second PowerShell window):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Check it with `Invoke-RestMethod http://localhost:8080/api/categories`. Leave this window running.

**3. Open and run the app in Android Studio**

1. *File > Open* and choose the **`android`** folder (not the repo root, and not `android/app`).
2. Wait for Gradle sync to finish. Android Studio creates `android/local.properties` with your SDK path;
   it is git-ignored.
3. Start an emulator from Device Manager, select the **app** run configuration and press *Run*.
4. **First launch on Android 17 (API 37):** the debug build asks for the "Nearby devices" permission.
   Tap **Allow**. This is Android 17's local-network permission, which is needed to reach the backend on
   the development computer (`10.0.2.2`). If you tap *Don't allow*, the app shows "taking too long to
   respond" errors. To fix that, grant it under *Settings > Apps > Catalogue > Permissions > Nearby devices*,
   or reinstall. On Android 16 and lower there is no prompt.

The debug build calls `http://10.0.2.2:8080/`, which the emulator maps to `localhost:8080` on your computer.

To build and install from the command line instead (emulator running):

```powershell
cd android
.\gradlew.bat installDebug
```

## Try it end to end

The critical user journey is **Home → Category → Product → Enquire → Thank you → the email in Mailpit**,
plus **Search**. The last column says what was actually checked by hand on the API 37 emulator, against
the local backend and Mailpit. Those checks were done before the last two enquiry-outcome changes.

| Feature | Steps | Expected | Checked on the emulator? |
|---|---|---|---|
| Home | Launch the app | Search box, four category chips, five featured products with images | Yes |
| Missing featured products | Restart the backend with `$env:FEATURED_PRODUCT_IDS = "1,999,5,998,9"`, reopen the app | Yellow notice "Showing 3 of 5 featured products. Some are unavailable right now." plus the 3 products | Yes |
| Category | Tap **Men's clothing** | "Top rated" (3 products) and "More in Men's clothing" (the rest); no product appears twice | Yes |
| Search | Type `backpack`, tap **Search** | "1 result for "backpack"". Typing alone does not search | Yes |
| Search: no match / blank | Search `bicycle`; then clear the box and tap **Search** | "No products match…"; "Enter a product name…" under the box, with no request sent | Yes |
| Details | Tap a product | Image (loaded through `/api/products/{id}/image`), price, rating, category, description | Yes |
| Enquiry validation | **Enquire about this product**, tap **Send enquiry** empty, then with email `abc` | Messages under the name and email fields; nothing sent | Yes |
| Enquiry success | Fill in name, a valid email and an optional message, then **Send enquiry** | "Thank you, …" with a reference. The email appears in Mailpit with the same reference | Yes: thank-you shown, email seen in Mailpit with matching reference |
| Enquiry: couldn't confirm | Stop the backend, restart it with `$env:SMTP_PORT = "1"`, then send | "We couldn't confirm your enquiry… may receive it twice"; fields kept; button reads **Send again**. Restart the backend normally and tap **Send again** to succeed | **No.** The same steps were run before this message existed (it then said "not sent"); the current message is covered by unit tests only |
| Enquiry: not sent | Open the enquiry form, stop the backend completely, then send | "Your enquiry was not sent"; fields kept | **No.** Unit tests only |
| Network failure + retry | Stop the backend, reopen the app; start the backend, tap **Retry** | "Can't reach the catalogue…" with **Retry**, then content loads | Yes |
| Back / rotation | Use system Back and the in-app back buttons; rotate on the enquiry form | Normal back stack; typed text survives rotation | Yes: system Back from category, details and search; the thank-you screen's **Back to product** button; rotation on the enquiry form only |

## Tests

```powershell
cd backend
.\mvnw.cmd test                 # backend: unit + integration tests (no internet or Docker needed)

cd ..\android
.\gradlew.bat testDebugUnitTest # Android: JVM unit tests (no emulator needed)
.\gradlew.bat lintDebug         # Android lint
.\gradlew.bat assembleDebug assembleRelease
```

The Android unit tests cover:

- use cases: enquiry validation rules, trimming, and blank search
- ViewModels: loading, content, error and retry states; search only on the button; enquiry success and
  failure, duplicate taps, and server field errors
- the real data layer against MockWebServer: DTO mapping, URL encoding of `men's clothing`, problem-details
  error codes, unknown codes, non-JSON errors, timeouts and connection refused, and which enquiry
  failures count as "not sent" and which as "unconfirmed"

There are no instrumented or Compose UI tests; the UI was checked manually on the emulator.

## Android architecture

One Gradle module, three layers, each a package under `com.example.catalogue`:

```
presentation/   Compose screens + ViewModels (one pair per screen), navigation, shared components, theme
domain/         Models, repository interfaces, use cases, validation. Pure Kotlin: no Android, HTTP or JSON
data/           Retrofit API + DTOs, repository implementations, safeApiCall (exceptions/HTTP errors -> AppError)
AppContainer    Manual constructor injection: builds OkHttp, Retrofit, repositories and use cases once
```

Why each piece exists:

- **Screens** only draw state and forward taps. They collect state with
  `collectAsStateWithLifecycle()`, so collection stops when the screen is not visible.
- **ViewModels** hold screen state in a `StateFlow`. They survive rotation, call use cases in
  `viewModelScope` (cancelled when the screen leaves the back stack), and expose explicit
  loading, content, empty and error states.
- **Use cases** name each operation and hold rules that are not UI or HTTP: search validation, and enquiry
  validation before sending. Most of the others are thin pass-throughs today; they keep ViewModels
  depending on operations rather than repositories.
- **Repository interfaces (domain) and implementations (data)** let ViewModel tests use fakes, and keep
  DTOs and Retrofit out of the UI. The repository builds each product's image URL from the backend base URL.
- **`safeApiCall`** turns timeouts, connection failures, problem-details errors (including unknown or
  missing `code`s) and unreadable JSON into an `AppError`. `ErrorMessages.kt` turns those into customer text.
- **`AppContainer`** gives constructor injection without a framework. ViewModels are created with
  `viewModel { … }` inside each navigation destination, so each one is scoped to its back-stack entry.

### Search flow

1. On Home, `SearchBox` gives the text to `HomeViewModel`.
2. On **Search**, `SearchProductsUseCase.validate` checks the text. If it is blank, a message is shown and no
   request is made. Otherwise the app navigates to `SearchRoute(query)`.
3. `SearchViewModel(initialQuery)` runs the query once. Later searches only run when **Search** is tapped.
4. The use case trims the query. `CatalogueRepositoryImpl.searchProducts` calls
   `GET api/products/search?q=…` through `safeApiCall`, and maps the DTOs to `Product`s.
5. The ViewModel maps the result to Searching, Found, NoMatches or Failed. A newer search cancels an
   older one still in flight, and Retry repeats the last submitted query.

### Enquiry flow

1. `EnquiryViewModel` holds the typed name, email and message, so they survive rotation and failure.
2. **Send enquiry** sets `isSubmitting = true` synchronously, which disables the button and ignores more
   taps. Then `SubmitEnquiryUseCase` runs.
3. The use case validates with `EnquiryValidator` (the same rules as the backend). Invalid input returns
   `Invalid(fieldErrors)` without a request.
4. `EnquiryRepositoryImpl` POSTs `api/enquiries` and turns the answer into one of four outcomes:

   | Outcome | When | What the customer sees |
   |---|---|---|
   | `Sent(enquiryId)` | 200 with a reference | Thank-you message |
   | `Invalid(fieldErrors)` | 400 `VALIDATION_FAILED` for name, email or message | Messages under the fields |
   | `Failed(error)`: definitely not sent | (a) The app never connected (unknown host, connection refused, no route), or (b) the response carries one of our backend's codes that it returns **before** trying to send: `MALFORMED_REQUEST`, `UNSUPPORTED_MEDIA_TYPE`, `PRODUCT_NOT_FOUND`, `PRODUCT_SOURCE_UNAVAILABLE` | "Your enquiry was not sent", and Send enquiry to try again |
   | `Unconfirmed(error)`: outcome unknown | Everything else, including `EMAIL_SEND_FAILED`, any 4xx/5xx without one of those codes (e.g. from a proxy), a timeout, a connection that dropped after connecting, or a 200 whose body can't be read | "We couldn't confirm your enquiry… It may already have been sent… may receive it twice", and the button reads **Send again** |

   The rule: the app only says "not sent" when it has a concrete reason to be sure.
   - **Never connected:** no request bytes left the device. This relies on the enquiry client having
     OkHttp's automatic retries turned off (see below), so no earlier attempt can have sent the request.
   - **Pre-send codes:** the backend checks the JSON body and content type, runs `@Valid`, and looks up
     the product, all before `EnquiryService` calls the email sender. A response carrying one of those
     codes therefore means our backend stopped before sending. A bare status code proves nothing,
     because a proxy can also return 4xx or 5xx.
   - **`EMAIL_SEND_FAILED` is unconfirmed:** the backend did attempt SMTP. The mail server may have
     accepted the message, and then the connection failed before the backend received the final
     acknowledgement.
   - **Timeouts and dropped connections are unconfirmed:** they can happen after the email was sent.

   In both failure cases every typed field is kept. The app itself does not resend the enquiry: sending
   again is the customer's choice.

   **Automatic retries:** OkHttp's default `retryOnConnectionFailure = true` can silently resend a request
   whose connection failed after it was written. In OkHttp 4.12.0 source
   (`RetryAndFollowUpInterceptor`), such an `IOException` is recovered when the body is not one-shot
   (Retrofit bodies are not) and another route is available, which includes a reused keep-alive
   connection. A unit test reproduced it: the default client sent the same POST twice. Enquiries therefore
   use a client derived from the shared one with `retryOnConnectionFailure(false)`, which shares the
   connection pool. Catalogue GETs keep automatic retries, because repeating a read is harmless. Tests
   cover both.

   This turns off one known replay path; it does **not** make a duplicate request impossible:
   - **Redirects:** OkHttp's redirect following is still at its default. The backend doesn't redirect
     enquiries, so this wasn't tested.
   - **Intermediaries:** a proxy or load balancer between the app and the backend could retry on its own.
   - **The customer:** they can tap **Send again** after a "couldn't confirm" outcome.

   An idempotency key would handle all of these; it is listed as a next step in
   [`DECISIONS.md`](DECISIONS.md#what-id-do-next-with-another-day).
5. The thank-you view is shown only for `Sent`, which the backend returns only after the SMTP server has
   accepted the email.

## Configuration, security and production notes

- **Base URL:** `BuildConfig.API_BASE_URL`, set per build type in `android/app/build.gradle.kts`.
  - Debug uses `http://10.0.2.2:8080/`.
  - Release uses a placeholder, `https://catalogue.example.invalid/`, which must be replaced with the real
    HTTPS backend.
- **Cleartext HTTP is debug-only.** `src/debug` adds a network security config that allows plain HTTP to
  `10.0.2.2` and `localhost` only. Release builds use Android's default, which allows HTTPS only.
  Production needs HTTPS on the backend with a valid certificate.
- **Local-network permission is debug-only.** `ACCESS_LOCAL_NETWORK` is declared in the debug manifest,
  and requested by `src/debug/.../DevNetworkAccess.kt`. The release twin in `src/release` does nothing.
- **No secrets in the app.** No API keys or paid services are used. SMTP settings live only on the backend.
- **A physical phone** cannot use `10.0.2.2`. Change the debug base URL to your computer's LAN IP, and add
  it to the debug network security config.

## Assumptions and known limitations

These are in [`DECISIONS.md`](DECISIONS.md): assumptions about the acceptance criteria (featured products,
category ranking, search, currency), simplifications and why, how the product source, UI and email
provider can change, known limitations (including possible duplicate enquiries and process-death state
loss) and what's next.

## Time spent

**Total: approximately 4 hours**, against the brief's 2–3 hour time-box.

>
> | Activity | Approximate time |
> |---|---|
> | Planning | 30 min |
> | Building | 2 hours |
> | Testing | 45 min |
> | Review | 45 min |
