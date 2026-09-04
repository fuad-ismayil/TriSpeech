# V1 single-owner operations runbook

Use two identities: the daily `owner_operator` identity and a separately protected `emergency_recovery` identity. Require phishing-resistant MFA/hardware keys and never share credentials.

Monitor provider request count/minutes/cost, key-alias health, rewarded impressions versus verified SSV grants, finalized eCPM and reward by market, coverage ratio, queue depth, job latency, App Check failures, storage cleanup, and cloud/provider budgets. Logs must not contain raw audio, transcript text, installation public keys, ad callback signatures, or credentials.

Safe incident order:

1. Disable new ad sessions at 95% of budget while honoring existing credit.
2. Reduce Cloud Tasks dispatch and Cloud Run capacity during abnormal traffic.
3. Pause the affected market or key alias.
4. At the hard ceiling, stop provider dispatch and retain credit.
5. Use the whole-service switch only for broad incidents; local features remain available.

Credential fallback is allowed only for definitive key-specific 401/403/429 responses. Never retry an ambiguous provider timeout. Quarantine compromised credentials and rotate through Secret Manager; do not add credentials to Android, Remote Config, source, docs, logs, or tickets.

The internal status/actions endpoint must sit behind Cloud Run IAM/IAP as well as its secret header. It exposes aliases and aggregate health only. Direct production database edits are forbidden outside a documented, audited emergency procedure.
