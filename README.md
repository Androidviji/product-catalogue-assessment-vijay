# Product Catalogue Assessment

A product catalogue proof of concept:

- **`backend/`**: Java 21 + Spring Boot. Serves products from the Fake Store API and emails customer
  enquiries over SMTP. Its API, configuration and limitations are in [`backend/README.md`](backend/README.md).
- **`android/`**: Kotlin + Jetpack Compose app (MVVM, practical Clean Architecture). It only talks to the backend.
- **`docker-compose.yml`**: Mailpit, a local test inbox for enquiry emails.
- **`docs/ai-log.md`**: notes on how AI assistance was used.

```
Android app  --HTTP-->  backend (localhost:8080)  --HTTPS-->  fakestoreapi.com (products, images)
                                                  --SMTP--->  Mailpit (localhost:1025, inbox UI on :8025)
```

## Prerequisites (Windows)

- JDK 21, Docker Desktop, Android Studio with Android SDK 37 and an emulator (the app was tested on an
  API 37 "Medium Phone" emulator).
- Internet access while running, for fakestoreapi.com.

## Run everything end to end

**1. Start Mailpit** (PowerShell, repo root; Docker Desktop running):

```powershell
docker compose up -d mailpit
```

Open http://localhost:8025 and leave it open. If another Mailpit container already uses ports 1025/8025,
use that one instead; the backend only needs something listening on localhost:1025.

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

| Feature | Steps | Expected |
|---|---|---|
| Home | Launch the app | Search box, four category chips, five featured products with images |
| Missing featured products | Restart the backend with `$env:FEATURED_PRODUCT_IDS = "1,999,5,998,9"`, reopen the app | Yellow notice "Showing 3 of 5 featured products. Some are unavailable right now." plus the 3 products |
| Category | Tap **Men's clothing** | "Top rated" (3 products) and "More in Men's clothing" (the rest); no product appears twice |
| Search | Type `backpack`, tap **Search** | "1 result for "backpack"". Typing alone does not search |
| Search: no match / blank | Search `bicycle`; then clear the box and tap **Search** | "No products match…"; "Enter a product name…" under the box, with no request sent |
| Details | Tap a product | Image (loaded through `/api/products/{id}/image`), price, rating, category, description |
| Enquiry validation | **Enquire about this product**, tap **Send enquiry** empty, then with email `abc` | Messages under the name and email fields; nothing sent |
| Enquiry success | Fill in name, a valid email and an optional message, then **Send enquiry** | Button shows "Sending…" and is disabled; then "Thank you, …" with a reference. The email appears in Mailpit with the same reference |
| Enquiry failure | Stop the backend, restart it with `$env:SMTP_PORT = "1"`, send again | "Your enquiry was not sent…"; every field keeps its text. Restart the backend normally and tap **Send enquiry** again to succeed |
| Network failure + retry | Stop the backend, reopen the app | "Can't reach the catalogue…" or "taking too long…" with **Retry**; start the backend and tap Retry |
| Back / rotation | Use system Back and the top-bar arrow; rotate on the enquiry form | Normal back stack; typed text and results survive rotation |

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
  error codes, unknown codes, non-JSON errors, timeouts and connection refused

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
4. `EnquiryRepositoryImpl` POSTs `api/enquiries`. A 200 response returns `Sent(enquiryId)`. A 400
   `VALIDATION_FAILED` maps the backend's field names to form fields. Anything else is
   `Failed(AppError)`.
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

## Known limitations and assumptions

- **No caching or offline mode.** Every screen fetches fresh data. Coil caches images in memory and on disk.
- **Process death:** ViewModels are not backed by `SavedStateHandle`.
  - After the system kills the app in the background, the navigation back stack and route arguments (for
    example the search query) are restored, and data reloads.
  - Text typed into the enquiry form, and a search box edited after the search, are lost.
- **English only.** UI text is in Kotlin code, not `strings.xml`, to keep the PoC compact. Localisation
  would move it to resources.
- **Prices** are formatted as AUD with an `en-AU` locale. Fake Store prices have no currency, so this is
  an assumption.
- **Not all states were exercised on the emulator.** The image-unavailable placeholder was not seen,
  because every Fake Store image loaded; it is implemented with Coil's error slot. TalkBack was not run,
  although the semantics were checked through UI Automator dumps.
- **Search** submits from the Search button or the keyboard's Search key; both are explicit submits, and
  nothing searches while typing.
- **Featured notice:** when the backend reports missing featured products, the app says how many are shown
  out of how many were configured. It does not show the missing IDs, which mean nothing to a customer; the
  backend logs them.
- **Dependency versions** were chosen for compatibility with Kotlin 2.2.10 and AGP 9.5.1, as used by the
  installed Android Studio template. Lint reports newer versions (warnings only).
