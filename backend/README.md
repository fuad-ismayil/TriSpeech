# TriSpeech V1 backend

The backend creates AdMob reward sessions, accepts Google SSV callbacks, and activates the existing credit-gated TokenMix path. A client-reported completion endpoint exists only for explicitly enabled Google sample test ads; it cannot be enabled for live ads.

This service is the security boundary for online transcription. Android never receives TokenMix credentials. The private preview permits development access when `DEVELOPMENT_ACCESS_MODE=true`; rewarded access instead requires credits with this flag false.

## Local development

1. Copy `.env.example` to an uncommitted `.env` and inject development-only provider credentials there.
2. Set `USE_MEMORY_STORE=true`, `DEVELOPMENT_ACCESS_MODE=true`, and a low `PROVIDER_DAILY_BUDGET_USD`, then run `npm install` and `npm run dev`.
3. The Android debug build uses `http://10.0.2.2:8080`. Release builds require an HTTPS `TRISPEECH_BACKEND_URL` Gradle property.

The local memory store is for development previews only. Its registrations and jobs disappear on restart. The repository-level `render.yaml` can deploy this preview to a free Render web service; add TokenMix values only through Render's secret environment-variable UI as `TOKENMIX_KEY_1`, `TOKENMIX_KEY_2`, and so on. Any positive numeric suffix is accepted, gaps are allowed, and lower numbers have higher priority. If numbered keys exist, they replace the legacy primary/secondary variables.

The Android release build must be compiled with the HTTPS URL and production AdMob identifiers:

```text
./gradlew :app:assembleRelease -PTRISPEECH_BACKEND_URL=https://your-service.example -PADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY -PADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

Never place a TokenMix credential in Gradle properties, BuildConfig, Android resources, source code, or the APK.

## Current preview safety

The Android build initializes Google Mobile Ads only after the user opens online access and makes an ad-privacy choice. The private preview does not require ads. For end-to-end testing with Google's sample rewarded ad, set `DEVELOPMENT_ACCESS_MODE=false`, `ADMOB_TEST_REWARDS=true`, and `ADMOB_CLIENT_REWARDS_ENABLED=true`; never use that bridge for production.

- Keep the preview URL private among testers.
- Set strict spending/quota limits in the TokenMix account itself. The in-memory development budget resets when a free host restarts and is not a billing guarantee.
- Keep `PROVIDER_MAX_CONCURRENCY` low (the included Render blueprint uses `2`) and disable the service immediately if abnormal use appears.
- Do not present this development mode as a secure public release. Anonymous installations and request signatures prevent accidental/replayed requests, but without App Check or ad verification a modified client can still create new installations.

## Future V1 production configuration

- Rotate every credential previously pasted into chat. Store only fresh keys as numbered `TOKENMIX_KEY_N` secrets.
- Configure the production AdMob rewarded unit to send SSV callbacks to `/callbacks/admob/ssv`.
- Set `REQUIRE_APP_CHECK=true`, Firestore TTL for `requestNonces`, a 24-hour Storage lifecycle backstop, and IAM-restricted Cloud Run service identities.
- Configure Cloud Tasks with a low dispatch ceiling, Cloud Run max instances, provider concurrency, per-provider/cloud budgets, and 50/70/85/95/100 percent alerts/actions.
- Put the internal operations routes behind IAM/IAP in addition to the internal task token.
- Populate market economics from finalized 30-day AdMob revenue and provider invoices. Markets below the 30-second affordability floor receive no ad session.
- If `TRUSTED_MARKET_HEADER` is used, configure the gateway to remove any client-supplied copy and inject the authoritative two-letter country value itself. Otherwise leave it unset and use `DEFAULT_MARKET`; the JSON body is never trusted for pricing.

The callback contains no transcript or audio access. Logs contain provider aliases only.
