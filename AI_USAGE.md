# AI usage

How AI tools were used to plan, build and review this submission. The running notes made during the
build, with more detail, are in [`docs/ai-log.md`](docs/ai-log.md).

## Tools and models

| Tool | Model | What it was used for |
|---|---|---|
| **Claude Code** (Anthropic's terminal coding agent) | Claude Opus 5.5 | Planning from the assessment PDF; writing all code, tests and docs; running builds and tests; starting the backend; driving the Android emulator through `adb` and UI Automator dumps; checking results through the Mailpit API and emulator screenshots; reporting what it had and hadn't verified. |
| **ChatGPT** | Model not recorded | Discussing the assessment; developing and refining the prompts given to Claude Code; reviewing code after it was pushed to GitHub. |

ChatGPT didn't edit the code or run anything locally. Its review findings were given to Claude Code as
prompts; Claude Code investigated, changed the code and ran the tests.

## Agent instruction files

None. No `CLAUDE.md`, `AGENTS.md`, `.cursor/rules` or similar file is used or committed. Instructions
were given to Claude Code as prompts, one per stage, each with explicit working rules (see below).

## Plan or spec

- **Plan:** the first prompt asked Claude Code to read the assessment PDF and produce a plan only, with no
  code: a requirements checklist, architecture, API contract, assumptions, testing approach, run flow and
  risks. That plan was a conversation output and is not committed.
- **Spec:** each later stage was driven by a detailed prompt that acted as the spec for that stage:
  1. the backend foundation;
  2. the remaining backend features;
  3. the Android app;
  4. review findings.

  I developed and refined these prompts with ChatGPT. Each one repeated rules such as:
  - inspect the existing code first;
  - explain the change before making it;
  - don't commit;
  - don't claim a check passed unless it was actually run;
  - report exactly what was and wasn't verified.

## Key prompts and moments

1. **Planning, and overriding Claude Code's proposals.**
   - Claude Code pointed out that the PDF lists only Node.js and PHP/Laravel as backend options. I later
     received a clarification from Officeworks approving Java Spring Boot.
   - I changed two of its proposals:
     - **Images:** it suggested letting the app load Fake Store image URLs directly. I chose a backend
       image endpoint keyed by product ID, so the client never receives or supplies arbitrary URLs.
     - **Fallback:** it suggested a static fallback product source. I decided to show an error and a Retry
       button instead.
2. **"Use a deterministic tie-breaker"** (backend features prompt). Before choosing one, Claude Code
   checked the live Fake Store data and found a real tie: products 11 and 12 are both rated 4.8. That's
   why the category order uses rating, then number of ratings, then ID. The test uses that real tie.
3. **ChatGPT review: logging and error contract.** ChatGPT reviewed the backend code on GitHub and raised
   two concerns, which I gave to Claude Code as a prompt:
   - **Logging:** the enquiry-failure log included the SMTP provider's raw error message, which can
     contain customer email addresses. Claude Code changed it to log only the enquiry ID, product ID and
     exception type, and added a test.
   - **Error codes:** the README promised a `code` on every error, but framework errors (malformed JSON,
     a non-numeric product ID) had none. Claude Code added codes for common framework errors and a
     generic unexpected-error response, with tests. Errors handled outside the controller advice may
     still lack a code, so the Android app has a generic fallback.
4. **The first emulator run failed.** Every request from the app timed out. Claude Code narrowed it down:
   - The backend answered from inside the emulator in 0.28s (using `nc`).
   - The backend's threads were idle, so no app request had arrived.
   - The emulator image defines `android.permission.ACCESS_LOCAL_NETWORK`. Granting it made the app work.

   Android 17 blocks connections to local-network addresses such as `10.0.2.2` without this permission,
   and the unit tests could never have caught that. The fix is limited to the debug build: the permission
   is declared there and requested on first launch. The release build doesn't ask for it.
5. **ChatGPT review: "could the enquiry be sent twice?"** ChatGPT reviewed the Android code on GitHub and
   raised concerns that I gave to Claude Code in two rounds:
   - **Wrong message:** after a timeout the app said "not sent", although the email might have gone.
   - **Not certain:** `EMAIL_SEND_FAILED` and a bare 4xx don't prove it wasn't sent either, and OkHttp's
     automatic retries needed checking.

   Claude Code read OkHttp 4.12.0's retry code and wrote a test showing that the default client resends
   a POST when a reused connection drops after sending. Run against the app at that point, the test
   showed our enquiry being sent twice and reported as success. Claude Code then:
   - turned that automatic retry off for enquiries;
   - limited "not sent" to cases with a concrete basis;
   - made everything else say the enquiry couldn't be confirmed and may arrive twice;
   - ran the Android tests.

## Where the AI got things wrong, and how it was caught

All of these were mistakes in Claude Code's output.

| What Claude Code got wrong | How it was caught | What changed |
|---|---|---|
| Logged the SMTP provider's raw error message on send failure (possible customer data in logs). | ChatGPT's review of the code on GitHub, given to Claude Code as a prompt. | Claude Code changed the log to the enquiry ID, product ID and exception type. A test feeds in an error containing an email address and asserts it is not logged. |
| The README promised a `code` on every API error, but framework errors had none. | ChatGPT's review, given to Claude Code as a prompt. | Claude Code added codes for common framework errors and a generic unexpected-error response, with tests. Errors handled outside the controller advice may still lack a code, so the Android app has a generic fallback. |
| The enquiry screen said "Your enquiry was not sent" for every failure, including timeouts where the email may have gone. | ChatGPT's review, given to Claude Code as a prompt. | Added a separate "couldn't confirm" outcome with a duplicate warning, and a **Send again** button. |
| It then treated `EMAIL_SEND_FAILED` and any 4xx as certain "not sent", and relied on a "POST reached the server once" test that only covered a fresh connection, which happens not to retry. | ChatGPT's second review asked for a conservative recheck. Claude Code then read the OkHttp source and wrote a test that reproduced the replay. | Retries turned off for enquiries; "not sent" limited to never-connected and our backend's codes returned before sending; tests for each case. |
| Fallback error codes were added to the error body *before* Spring had built it, so 404/405/415 still had no `code`. | 3 failing tests. | Added the code to the response after Spring builds it. |
| A search test assumed `men's clothing` matches only men's products; `women's clothing` contains that text. | Claude Code re-read the test before running it. | The test asserts both match, and the limitation is documented. |
| A MockMvc test passed an already-encoded URL as a string, which MockMvc encoded again. | A failing test. | Passed a `URI` instead. |
| Smaller ones: an unescaped Windows path in the git-ignored `local.properties` (lint error), and a navigation callback passed as a function reference instead of a call. | Lint; Claude Code's own review of its change. | Fixed before moving on. |

Claude Code also recorded environment problems that weren't its mistakes:
- `mvnw.cmd` couldn't reach Maven Central from the sandbox.
- Shell quoting broke some multi-file commands.
- The emulator was stopped by the system when the computer was low on memory.

## How AI output was reviewed

- I gave each stage to Claude Code as a scoped prompt and reviewed the result before moving on.
- After code was pushed to GitHub, I used ChatGPT as a second reviewer. Its findings went back to Claude
  Code as new prompts, and Claude Code investigated, fixed and tested them (see the table above).
- I made Claude Code separate what it had run from what it assumed.
  - **Example:** emulator checks done before the last two enquiry changes are reported as such, and the
    new enquiry messages are listed as not yet verified on a device.
- Generated code and docs were treated as drafts: I own and can explain every decision, including the
  ones the AI tools proposed.
