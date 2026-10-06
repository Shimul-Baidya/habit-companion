# Private academic Gemini demo

Chunk 12 owner decision, 7 October 2026: direct Android Gemini API using `gemini-3.5-flash-lite`. No backend, accounts, deployment service or cloud habit database. The existing provider-neutral `CoachService` can later be replaced without changing Coach UI/domain contracts.

## Local build

The supplied key is already in root `.env`, ignored by Git, with mode `600`. Do not paste it into source, documentation, logs or Git. `.env.example` contains only an empty placeholder. Gradle reads `GEMINI_API_KEY` from the environment first, otherwise from `.env`, and generates private BuildConfig configuration. A missing key builds an honestly unconfigured app. Do not use Gradle build scans/debug logging to share local credential-bearing configuration.

Build with `./gradlew :app:assembleDebug --max-workers=2`. Android Studio may be closed; the Gradle wrapper, JDK and configured Android SDK are sufficient. Device checks need the Pixel connected with USB debugging. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. An ordinary compatible install/update preserves local data; never clear/uninstall the owner's app to test it.

The owner explicitly accepts that the private demo APK embeds an extractable credential. Keep this APK private. Before public distribution, remove the embedded key and replace the service adapter with an authorised protected provider boundary. This chunk creates no such infrastructure.

## Quota and demonstration

The owner confirmed **500 requests/day, 15 requests/minute** and authorised up to approximately 200 verification requests, while asking to minimise use. Public Google docs now direct users to their active project quota in AI Studio, so those project numbers are owner-confirmed rather than independently retrieved here. Rate limits apply per project, not per key; daily counters reset at midnight Pacific time, not Dhaka midnight. Other tools using the same project share the allowance.

**Verification used exactly two Gemini generation requests (0.4% of the confirmed 500-RPD daily allowance), both successful.** No retries or additional live calls were needed. Normal tests consume no Gemini requests. `GeminiLiveSmokeTest` is explicitly opt-in (`-Pandroid.testInstrumentationRunnerArguments.coachLive=true`) and makes one call per case: a synthetic binary planning draft and a synthetic existing Weekly quantity history. Do not include that opt-in in routine/full regression commands. No automatic network retry, fallback model, background polling, or request on cache re-entry. Explicit New suggestions, Send and Retry are interactions; each may consume a request. The screen retains bounded cooldown and the longer provider retry delay. Disable Coach in Profile if you do not want to make requests during unrelated exploration; re-enable it for the demonstration.

## Disclosure and privacy

The owner accepted Google's unpaid-service data use for this private academic demo in Bangladesh. Coach/Profile/form copy names Google Gemini and discloses transmission and possible product improvement/human review. Avoid sensitive, confidential or personal information in draft names, units and questions. Existing-habit requests omit automatic names, IDs, dates, raw logs, other habits and the full conversation. Planning sends the approved draft name/schedule/tracking fields and aggregate count. Both send the current question and three locally admitted strategies. Stored habits/settings/Coach history remain local; explicit export remains deliberate. Backup exclusions remain intact.

Google's terms require supported regions and 18+ use, prohibit medical advice/clinical integration, and require Paid Services for clients offered in EEA/Switzerland/UK. This private Bangladesh academic setup is not approval for worldwide distribution. No billing/account/provider settings were changed.

## Reviewed official references

- [Exact model and structured-output support](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)
- [REST generation API](https://ai.google.dev/api/generate-content)
- [Structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [API keys and client credential extractability](https://ai.google.dev/gemini-api/docs/api-key)
- [Current rate-limit rules and project quota](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Additional terms, effective 23 March 2026](https://ai.google.dev/gemini-api/terms)
- [Supported regions, including Bangladesh](https://ai.google.dev/gemini-api/docs/available-regions)
