# Decisions and assumptions

This covers what the brief asks for: assumptions where the acceptance criteria were unclear, what was
dropped or simplified, how the design can grow, and what I would do next. Technical detail lives in
[`README.md`](README.md) and [`backend/README.md`](backend/README.md).

The client and backend choice, and the reasons for it, are in the README
([Technology choice](README.md#technology-choice)).

## Assumptions where the acceptance criteria were unclear

| Topic | Assumption | Why |
|---|---|---|
| **"Top rated" on the home page** | "GIVEN I have chosen 5 products" means a business person *chooses* them; they are not calculated from ratings. They are configured as product IDs in `FEATURED_PRODUCT_IDS` (default `1,5,9,17,20`, which spans all four Fake Store categories) and shown in that order. | The criterion says "I have chosen", so ratings would take the choice away from the business. |
| **Missing featured products** | If a configured ID doesn't exist, the other products still show, the backend reports `missingProductIds` and logs a warning, and the app says "Showing 3 of 5 featured products". It never implies all five loaded. Customers don't see the IDs. | One bad ID shouldn't break the home page, but the shortfall shouldn't be hidden. |
| **Category "top rated" and "additional"** | "Top rated" = the 3 highest-rated products in the category. "Additional" = all the rest. Order: rating (high first), then number of ratings (more first), then ID (low first). The category is sorted once and split, so no product is in both groups. | The criterion gives no number. Fake Store has a real rating tie (products 11 and 12, both 4.8), so a fixed tie-breaker was needed for a stable order. |
| **Categories** | Built from the product list, not Fake Store's categories endpoint. Matched case-insensitively. Names with spaces or apostrophes (`men's clothing`) are URL-encoded by the app. An unknown category returns 404. | A listed category can then never be empty. |
| **Search: "text matching"** | The whole trimmed search text is matched, ignoring case, as one phrase inside the title, description or category. A blank search is rejected (the app shows a message and sends no request). Searches longer than 100 characters are rejected. No matches gives an empty result. | The simplest reading of "text matching that search term". A side effect is documented below: `men's clothing` also matches `women's clothing`. |
| **"Click the search button"** | A search runs only on an explicit submit: the Search button or the keyboard's Search key. Typing never searches. | Matches the criterion; the keyboard key is the same explicit action on a phone. |
| **"Page" / "click" / "link" on mobile** | Pages are screens; clicks are taps; category links are chips on the home screen (and a "More in …" chip on the details screen). | The brief is written for web or mobile. |
| **Currency** | Prices are shown as Australian dollars (`en-AU` format). | Fake Store prices have no currency; the client is Australian. |
| **Product images** | The app loads images through the backend (`/api/products/{id}/image`), never from Fake Store directly. The backend fetches only image URLs from the product data, on the Fake Store host, with no redirects, png/jpeg/gif/webp only, at most 5 MB. | The brief says all product data must go through the backend. Taking only a product ID means a client can't make the backend fetch arbitrary URLs. |
| **Enquiry form** | Name and email are required; the message is optional. The app checks the same rules as the backend, and the backend validates everything again. The product details in the email come from the product source, not from the app. | Contact details plus an optional question is the minimum useful enquiry. |
| **"The sales inbox will receive an email"** | Success is reported only after the configured SMTP server has accepted the message. The sales inbox is configuration (`ENQUIRY_SALES_INBOX`). Locally it is Mailpit, which captures mail and never delivers it to a real mailbox. | The PoC can show real email delivery without paid services or accounts. |
| **"The customer will see a Thank you message"** | Shown only after that confirmed success, never optimistically. | Saying thank you for an email that wasn't sent would mislead the customer. |

## Requirements dropped or simplified, and why

- **No database, queue, authentication or caching.** Every request reads Fake Store, and enquiries are
  sent synchronously with no retry queue. This keeps the PoC small and inside the time-box. It's
  acceptable at Fake Store's scale (20 products) but not for real traffic.
- **No duplicate-enquiry prevention.** If the outcome of a send is unknown (timeout, dropped connection,
  `EMAIL_SEND_FAILED`, an unexpected or proxy error), the app says it couldn't confirm the enquiry and warns
  that sending again may create a duplicate. It doesn't prevent one. See "What I'd do next".
- **No rate limiting or spam protection** on the enquiry endpoint.
- **English only.** UI text is in Kotlin code rather than `strings.xml`.
- **One Android module and manual dependency injection** (`AppContainer`) instead of feature modules and
  Hilt, so the whole app is easy to read and explain. Several use cases are thin pass-throughs; they
  give ViewModels a named operation to depend on.
- **No instrumented or Compose UI tests.** The critical path is covered by JVM unit tests on both sides
  and was checked by hand on an emulator (see README for exactly what).
- **Process death:** the navigation state and route arguments are restored, and data reloads.
  Text typed into the enquiry form, and an edited search box, are lost (`SavedStateHandle` isn't used).
- **Release build points at a placeholder HTTPS URL.** Only the debug build is meant to run.

## How the design supports growth beyond the PoC

The brief says the product data source, look and feel, and email provider will change. Each one sits
behind a single boundary:

| Expected change | Where it lives | What changes |
|---|---|---|
| **Product data source** (Fake Store → database, PIM, commerce platform) | Backend `ProductSource` / `ProductImageSource` interfaces; `FakeStoreProductSource` is the only class that knows Fake Store. Our own `Product` model and API contract (`ProductResponse`) sit between the source and the app. | Write a new `ProductSource` implementation. The API and the Android app don't change. |
| **Email provider** (SMTP → an email API such as Amazon SES) | Backend `EnquiryEmailSender` interface; `SmtpEnquiryEmailSender` is one implementation. The email content (`EnquiryEmail`) has nothing provider-specific in it. | Add another implementation, or point the SMTP settings at another provider through environment variables. |
| **Look and feel** | Android `presentation/theme/Theme.kt` (colours) and shared components in `presentation/common/`. Screens only render state from ViewModels. | Restyle the theme and components; ViewModels, domain and data code don't change. |
| **API changes** | Android DTOs are mapped to domain models in the data layer. Unknown JSON fields are ignored, and unknown error codes fall back to safe generic messages. | Additive backend changes don't break the app. |
| **Deployment** | The backend is a single runnable jar configured entirely by environment variables, with no local state. | It could run on an AWS container or app service, with SMTP pointed at a managed provider. Not tried. |

Errors use one JSON format ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details) with a
stable `code`, so new clients can rely on it.

## Known limitations

- **Duplicate enquiries are possible.**
  - The app turns off OkHttp's automatic connection-failure retries for enquiry POSTs (a test showed the
    default client resending a POST).
  - It doesn't make duplicates impossible. The customer can choose **Send again** after an unconfirmed
    outcome. A proxy or load balancer in front of the backend could retry. OkHttp's redirect following is
    still at its default; the backend never redirects, so this wasn't tested.
- **`EMAIL_SEND_FAILED` is always "unconfirmed".** The backend uses one code for every SMTP failure,
  including clear ones like "server unreachable", so the app can't tell them apart.
- **Connect timeouts count as "unconfirmed"** even though nothing was sent; OkHttp reports them the same
  way as a read timeout.
- **Search is a plain phrase (substring) match:** no ranking, typo tolerance or word splitting.
  `men's clothing` also matches `women's clothing`.
- **Every request goes to Fake Store.** If it's down, the app shows an error with Retry; there is no fallback.
- **Process-death state loss** for typed form text (see above).
- **A physical phone** needs the debug base URL changed from `10.0.2.2` to the computer's network address.
- **Not everything was checked on a device.**
  - The "Image unavailable" placeholder was never seen, because every Fake Store image loaded.
  - TalkBack wasn't run; the accessibility labels were only checked through UI Automator dumps.
  - The current enquiry "not sent" and "couldn't confirm" messages are covered by unit tests only.
- **Dependency versions are pinned** to match the installed Android Studio template (AGP 9.5.1,
  Kotlin 2.2.10), with libraries from 2025 that compile with Kotlin 2.2. Lint reports newer versions as
  warnings.

## What I'd do next with another day

1. **Idempotency key for enquiries.** The app creates a UUID when the form is first submitted and sends it
   with every attempt. The backend remembers recent keys and returns the original result instead of
   emailing again. This needs storage shared by all backend instances, with an expiry.
2. **Check the new enquiry outcome messages on the emulator** ("not sent" and "couldn't confirm"). They
   are covered by unit tests but haven't been seen on a device since they were added.
3. **Compose UI tests for the critical path:** home → category → details → enquiry → thank you, against a
   fake repository.
4. **Keep typed form text across process death** with `SavedStateHandle`, and move UI text to `strings.xml`.
5. **CI** (for example GitHub Actions) running backend and Android tests and lint on every pull request.
6. **Backend:** a short in-memory cache of the product list, rate limiting on enquiries, and a separate
   error code when SMTP failed before any message data was sent, so the app can say "not sent" in that case.
