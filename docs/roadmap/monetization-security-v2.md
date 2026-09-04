# TriSpeech V2 monetization and security roadmap

This roadmap preserves the pre-V1 production design. It is not active in the free-first release.

## Commercial model

- Google Play and the TriSpeech backend are authoritative for purchases.
- Future products may include regular monthly Plus (`X`), Offline AI Lifetime (`Y`), and a first-purchase bundle (`B`) where `B > X`, `B > Y`, and `B < X + Y`.
- Plus may provide a metered GPT-4o allowance and optional cloud history; Offline AI uses a periodically refreshed signed device lease.
- Refunds, chargebacks, voided purchases, grace periods, account hold, cancellation, and restoration are processed through Play Developer APIs and RTDN.

## Accounts and entitlements

- Firebase Authentication, immutable purchase-token ownership, server-side CreditGrant ledgers, three-device registration, controlled recovery, and audited support actions.
- Natural Plus expiration never removes independently earned lifetime Offline AI.
- Free local history remains on-device; cloud history and its retention policy are explicit opt-in subscription benefits.

## Security and operations

- Provider secrets remain server-only.
- Play Integrity/App Check, request binding, signed offline leases, idempotency, layered rate limits, provider budgets, circuit breakers, and immutable audit events.
- A role-based support console separates read-only support, billing, fraud, operations, privacy, security administration, and emergency access as staffing grows.
- No claim that local open-source models or a modified APK can be made literally uncrackable.

## Re-entry criteria

Do not start V2 until V1 has reliable retention, finalized ad economics, stable provider costs, legal documents, tested data migrations, incident runbooks, and enough operational capacity to support billing and privacy obligations.

## Preserved implementation contract

This section records the decisions that must survive the V1 simplification. It is a roadmap, not dormant code in the V1 APK.

### Products and pricing

- `X` is the regular monthly Plus price, `Y` is Offline AI Lifetime, and `B` is a one-time first-purchase bundle/offer. The required relationship is `B > X`, `B > Y`, and `B < X + Y`.
- Never let a very cheap introductory Plus month permanently unlock a more expensive lifetime product. A lifetime unlock requires the lifetime SKU, the bundle SKU, or a clearly disclosed qualifying paid-value/tenure rule enforced by the backend.
- Model actual GPT-4o and Mini invoice cost per audio minute, Cloud Run/Tasks/Storage/Firestore/networking cost, retry reserve, Play revenue share, taxes, refunds, rewarded eCPM after invalid traffic, and target margin before fixing prices or limits.
- The earlier planning values—600 GPT-4o minutes per billing cycle, six rewarded ads per day, ten Mini minutes per verified ad, ten daily Mini minutes for the first three account days, and 5 GB Plus backup—are hypotheses, not promises. Recalculate them from `net revenue / all-in unit cost` before launch.

### Entitlements

- Play purchase state and the TriSpeech backend are authoritative. Purchase tokens bind immutably to a Firebase UID and are never freely transferable.
- Store append-only purchase/verification events and derived entitlements. RTDN plus periodic reconciliation handles renewal, cancellation, grace, hold, expiry, refund, chargeback, and voided transactions.
- Offline AI ownership survives natural Plus expiry. Benefits originating solely from refunded/voided transactions may be revoked.
- A lifetime owner receives a signed offline lease that remains practically usable without a network. Prefer a clearly disclosed 90-day verification window; warn before expiry, keep models/history, restore on reconnect, and always leave Native STT available.
- Device registration is limited to three active devices by default, with self-service revocation and controlled, audited support recovery.

### Accounts, recovery, and cloud data

- Google Sign-In/Firebase UID and the Play purchasing account can differ. Show the current TriSpeech identity before purchase and require deliberate confirmation.
- Restore first verifies Play ownership, then checks the immutable UID binding. Conflicts enter an auditable recovery workflow using purchase evidence and account/device history; support never copies a token on request alone.
- Account deletion, lost devices, duplicate TriSpeech accounts, and deleted Firebase identities need explicit recovery/deletion policy, cooling-off periods for sensitive transfers, and immutable audit events.
- Cloud backup is opt-in. Encrypt objects, separate transcript/audio retention, show storage usage, support export/delete, and warn before the documented 90-day post-Plus retention deletion. Free local history is never removed merely because Plus expires.

### UX states

The V2 implementation must define and test anonymous/sign-in gates, Mini free/ad balances, GPT-4o paywalls, first-three-day grants, exhausted ad inventory, locked Models/Offline AI, purchase/restore/manage-subscription flows, Plus allowance exhaustion, cancellation/expiry/grace/hold/refund, reinstall/migration, fourth-device handling, lease warnings/expiry, sign-out, cloud activation, and cloud-retention warnings. Renewal price, automatic renewal, limits, device rules, cloud processing, retention, and periodic lifetime-license verification appear before purchase without dark patterns.

### Support and administration

Roles are permission boundaries and do not imply ten employees. Grow from separate owner and break-glass identities into `support_readonly`, `billing_support`, `fraud_analyst`, `operations`, `privacy_officer`, `security_admin`, and `break_glass` only when workload requires it.

An internal IAM/IAP-protected console may inspect entitlement source/state, billing lifecycle, verification events, balances/grants, devices, integrity/risk signals, cloud usage, sanitized job failures, RTDN state, and audit events. Safe actions are device revocation, entitlement refresh, Play resync, idempotent event retry, and narrowly scoped verified corrections. Raw audio, transcript text, access/purchase tokens, and secrets are hidden by default. Every action is least-privilege, reason-coded, and audited.

### Security and cost control

- All paid/online decisions remain server-side. APK booleans, hidden UI, obfuscation, and local receipts are never authority.
- Bind mutations to authenticated UID, registered device key, App Check/Play Integrity, timestamp/nonce, idempotency key, and replay protection. Apply account, device, endpoint, job, upload, entitlement, ad, purchase, sync, and recovery limits.
- Enforce provider concurrency, queue depth, outstanding jobs, duration/size limits, timeout/retry ceilings, duplicate prevention, spend reservations, budgets/alerts, circuit breakers, market/key/service kill switches, and backpressure.
- Use Secret Manager and short-lived service identities. Maintain audit trails, backup/restore drills, database migration tests, incident runbooks, dependency/SBOM and secret scans, staged rollout, and rollback procedures.

### Legal and Play readiness

Before V2 launch, update Privacy Policy, Terms, subscription terms, refunds/purchase help, Data Safety, ads/UMP consent, cloud/provider disclosure, retention/deletion, account deletion, usage/device limits, automatic renewal, and Offline AI verification language. Material purchase conditions must be displayed in the purchase UI, not hidden only in legal documents.
