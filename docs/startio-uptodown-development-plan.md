# TriSpeech: Start.io + Uptodown implementation plan

Execution update: local integration and automated builds are implemented. See [setup, verification, and remaining activation work](startio-setup.md) for current status. The scope and loop below record the implementation plan.

Status: Corrected product scope; reward amount and Start.io verification approach must be resolved in Part 1 before dependent implementation. No implementation or publication performed by this document.
Prepared: 2026-09-05.

## Objective and defaults

Publish TriSpeech on Uptodown without paying a store registration fee. Start.io rewarded ads unlock only GPT-4o Mini online transcription through the existing TriSpeech/TokenMix backend. Determine whether legitimate ad usage produces nonzero publisher earnings; withdrawal and optimizing ad revenue against API cost are later milestones. The experiment still needs working online access controls and spending limits.

- Distribution: Uptodown, using the existing `com.trispeech.app` package and a persistent release signing key.
- Monetization: Start.io only. No mediation, banners, automatic interstitials, splash ads, or return ads.
- Reward: access to GPT-4o Mini online transcription. State the granted allowance before playback; no badge or unrelated reward. Resolve the fixed experimental allowance in Part 1 without inventing a profitable exchange rate.
- Public experiment: retain GPT-4o Mini online transcription using the existing backend and `gpt-4o-mini-transcribe` provider route. Prepare that backend for this public milestone rather than dropping the online feature. Native STT, Offline Whisper, and existing local features remain free and require no ads. Preserve the private development preview separately.
- Publisher: individual based in Azerbaijan. Verify available payment setup, without assuming PayPal or bank-wire eligibility.
- Preserve existing English, Azerbaijani, Turkish, and Russian UI support.
- Do not replace the older V1/V2 roadmaps. This is a separate proof-of-revenue milestone.

## Mandatory development loop

Start with Part 1. Complete each part using this loop:

1. **Inspect:** Read the relevant implementation, instructions, and existing changes. Identify dependencies and define the part's acceptance criteria before editing.
2. **Implement:** Make the smallest complete change that satisfies the part. Preserve unrelated working-tree changes.
3. **Verify:** Run targeted automated checks and inspect the actual user flow or artifact where applicable.
4. **Review:** Check failure paths, lifecycle behavior, privacy, maintainability, and regressions. Distinguish observed defects from speculative improvements.
5. **Fix and recheck:** Repair every blocking issue, rerun affected checks, and repeat until acceptance criteria pass. Do not repeat unrelated passing checks without a reason.
6. **Record and advance:** Record changes, checks and results, remaining limitations, and the next part. Only then advance.

A check that was not run is not a pass. A provider approval or real-device check that is unavailable remains pending. If an external dependency blocks a part, complete independent preparation but do not declare that part complete or enable dependent live behavior. Do not widen scope to resolve optional improvements.

Use a progress table during execution: Part | Status | Evidence | Issues | Next action. Status values: pending, in progress, blocked, passed. Request missing account details when they first become necessary; never invent IDs, seller records, account eligibility, or approval results.

## Part 1 — Baseline and release boundaries

**Work**

- Inspect repository instructions and current Git changes. Existing edits to the main Compose UI and launcher artwork belong to the working baseline; preserve them.
- Run the current Android unit tests, lint, and debug build. Record pre-existing failures separately from new defects. Run backend tests/checks if changes will affect it.
- Trace the existing online selection, upload, worker, provider request, and credit flow. Keep the TokenMix integration and prepare a separate public configuration without development-access bypasses.
- Resolve the fixed experimental transcription allowance with the owner after inspecting current credit behavior. The aim is proof of ad earnings, not an optimized token-price calculation; disclose any intentionally subsidized API use.
- Verify whether Start.io offers usable authenticated server-side reward verification for this account/integration. The public Android guide documents a client video-completion listener, which is not equivalent to AdMob SSV. Do not invent a Start.io SSV API or treat signed client requests as proof that an ad was watched. If no provider verification is available, document the limitation and resolve an explicitly bounded experimental alternative with the owner before public paid-API access.
- Keep database compatibility and package identity intact. Preserve unrelated AdMob scaffolding; implement Start.io-specific handling separately.

**Exit gate:** Baseline and the online flow are documented; reward allowance and verification approach are settled; public backend controls are specified. Online transcription remains in scope, and local transcription/history remain unchanged.

## Part 2 — Publisher setup and website preparation

**Work**

- Establish the Start.io app entry for the exact package; obtain its App ID and the full account-generated `app-ads.txt` contents. App ID and publisher/seller ID are distinct; do not substitute one for the other.
- Establish an Uptodown developer profile and prepare the app draft. Complete owner-required identity and account steps without creating inaccurate declarations.
- Verify Start.io accepts the resulting alternative-store URL when available. Document acceptance separately from any claimed increase in demand.
- Prepare a minimal public developer website containing app information, support contact, privacy policy, and a root `/app-ads.txt` endpoint. Default to free static hosting on a provider subdomain; use an existing suitable owner-controlled site if available. Do not buy a domain or hosting.
- Copy every required seller line exactly from Start.io. Update the website address in Start.io and the developer website field on the store listing where supported.
- Check Azerbaijan payment onboarding and record any requirements or unresolved restrictions. No withdrawal is required for experiment success.

**Exit gate:** Account identifiers and website artifacts are ready. Once hosted, `/app-ads.txt` returns HTTP 200 with plain text, without login or an HTML application fallback. Final crawler recognition is checked after publication in Part 6; hosting alone is not proof of discovery.

## Part 3 — Rewarded SDK and privacy integration

**Work**

- Add a pinned Start.io SDK dependency from Maven Central. The official integration page currently identifies 5.3.1; verify artifact availability and compatibility before pinning. Do not use `5.+` or the outdated Bintray repository shown in another snippet on that page.
- Wrap SDK access in a small Android rewarded-ad controller exposing load, show, and observable state. Keep SDK callbacks out of composable rendering code.
- Use explicit initialization so privacy choices can be handled before ad requests. Audit merged manifest initialization providers, permissions, and services. Apply current provider consent requirements and keep choices editable.
- Disable automatic splash, return, and automatic interstitial ads explicitly. Add no optional location/Bluetooth permission simply because a sample includes it.
- Separate test and live configurations. Debug/test builds always use test ads. Live builds require an explicit valid App ID and live-mode selection; missing configuration must fail clearly.
- Apply required R8 rules and verify the minified build, rather than relying on debug success.

**Exit gate:** Test rewarded ads load and display on a physical Android device; no ads appear automatically on launch, resume, or navigation; consent behavior and the merged manifest are reviewed.

## Part 4 — Online reward flow and backend access

**Work**

- Place the rewarded action in the GPT-4o Mini online-access flow only. Display the allowance before watching. Use existing valid credit without forcing another ad; no-fill prevents new credit, not use of already granted credit. Local modes remain available without ads.
- Model idle, loading, ready, showing, verifying, credited, unavailable, and error states. Show an ad only after explicit user action with an active foreground Activity.
- Connect completion to the backend award mechanism settled in Part 1. Persist credit server-side, bind awards to installation/session, and make grants single-use. Dismissal alone never grants access. Never describe client-reported completion as provider-verified.
- Reuse existing credit reservation and idempotent transcription jobs. Handle insufficient/expired allowance, duplicate requests, failure/refund behavior, and ambiguous provider outcomes without double charging or duplicate paid requests. A delayed award must remain recoverable without forcing another watch.
- Replace volatile preview persistence for public credits/jobs, turn off development-access bypasses, retain provider secrets server-side, and enforce provider-side spending ceilings, backend request limits, concurrency limits, and an online kill switch. Verify any attestation choice actually works for Uptodown installs rather than assuming Play-distribution behavior.
- Handle no-fill, network failure, double taps, backgrounding, rotation, and process recreation. Permit a deliberate retry; do not implement automatic watch/reload loops.
- Do not pause or overlay an active recording/transcription session with an ad. Explain when the user must finish that operation first.
- Translate new UI strings into all four supported languages and check accessibility, contrast, text scaling, and long translations.

**Exit gate:** A test rewarded completion grants the configured online allowance exactly once, then a short sample transcribes through the existing TokenMix backend. Dismissal/replay cannot produce duplicate credit; job retries cannot duplicate paid work; backend restart preserves awards/jobs; local modes remain ad-free. Test awards are isolated from production and are never presented as cash revenue.

## Part 5 — Release quality and store package

**Work**

- Run Android unit tests, instrumented tests for relevant flows, lint, and a signed minified release build. Use the repository's Gradle wrapper. Run backend `npm test` and `npm run check` only if backend code changed.
- Cover reward completion versus dismissal, duplicate callbacks, no-fill, offline state, consent refusal/change, rapid taps, lifecycle changes, credit expiry/reservation, replayed award requests, backend restarts, provider failures, and public online-access enforcement.
- Smoke-test Native STT, Offline Whisper/model download, recording/import, history, playback/export, all four locales, and install/update preservation of existing data. Test the oldest supported API and a current Android version; use a physical device for SDK behavior.
- Verify release signing, increasing version code, HTTPS configuration, native-library packaging, APK permissions, and absence of provider secrets. Keep signing credentials outside tracked files.
- Prepare an accurate Uptodown listing: signed APK, icon, screenshots, description, website/privacy links, supported languages, and ad disclosure. Describe the features actually enabled in this variant.
- Record APK SHA-256, version, build configuration, and validation results. Prepare rollback using the same signing identity; disabling ads must never accidentally enable unlimited online transcription. Preserve local features during an online-service incident.

**Exit gate:** No unresolved release-blocking defects; package and listing are reviewable. Identify any unavailable checks explicitly. Implementation authorization is not automatically store-publication authorization: obtain publication authorization only after these artifacts are ready if the owner has not already provided it.

## Part 6 — Publication and revenue validation

**Work**

- With publication authorized, publish the website, submit the APK to Uptodown, and resolve actual review feedback using the same inspect–fix–verify loop.
- After approval, enter the public listing URL in Start.io, verify package association and website details, and check `app-ads.txt` recognition. Escalate rejected URL/crawler issues to provider support; do not report them as verified merely because the URL saves.
- Keep developer testing on test ads. Use the approved live release for normal public usage; do not ask testers to generate clicks, installs, or repeated impressions to manufacture earnings.
- Record reporting period, app/placement, impressions, fill/no-fill information where available, and estimated revenue. A completion callback, an impression, and earnings are three different measurements.
- If impressions are absent, inspect ID/configuration and SDK delivery. If impressions exist but earnings are zero, check reporting delay, available campaigns, and traffic volume before assuming a code defect.
- Check initial results after a full reporting day and again after seven days of real usage. These are evaluation windows, not promises of earnings or unattended monitoring instructions.

**Exit gate:** Technical release success means the public APK supports rewarded online access and completes GPT-4o Mini transcription through TokenMix. Revenue-proof success additionally requires nonzero Start.io-reported earnings attributable to the app. Zero revenue means the experiment is still inconclusive, even if implementation is complete. Record estimated earnings separately from finalized/payable amounts and actual withdrawal; record API spending separately without making profitability a success requirement.

## Completion report

Provide the store and website links, APK identity/hash, SDK version, test evidence, provider approval/discovery status, rewarded-online-transcription evidence, measured earnings and API spending with reporting dates, known limitations, and rollback instructions. Never claim a payout that has not happened. Rewarded online access and its backend controls belong to this milestone; optimizing economics and comparing additional providers are deferred.

## Primary sources

- Start.io Android integration: https://support.start.io/hc/en-us/articles/360014774799-Integration-via-Maven
- Start.io seller-file setup: https://support.start.io/hc/en-us/articles/360007601619-How-To-Add-App-ads-txt-To-Your-Website
- Start.io alternative-store guidance: https://support.start.io/hc/en-us/articles/360007109314-Why-It-s-Important-to-Add-your-App-URL
- Start.io test integration: https://support.start.io/hc/en-us/articles/4401980822930-Test-Your-Android-Integration
- Start.io reporting troubleshooting: https://support.start.io/hc/en-us/articles/4404246898194-Troubleshooting-I-don-t-see-data-in-my-account
- Uptodown publishing overview: https://support.uptodown.com/hc/en-us/articles/4424141383181-Basic-guide-to-register-and-publish-apps-on-Uptodown
- Uptodown submission instructions: https://support.uptodown.com/hc/en-us/articles/360053260491-How-to-publish-an-app-on-Uptodown

Recheck provider requirements at implementation time. Source snippets and account-specific portal behavior can differ; record material discrepancies instead of guessing.
