# Paddle connection checklist

Confirmed: Pro costs USD 15 per month. Initial distribution is Google Play only. No website exists yet. Alternative billing eligibility/enrollment and checkout hosting must be resolved before enabling Paddle links in the Play build.
The owner stored PADDLE_API_KEY in Supabase secrets. No checkout, subscription, or live charge has been created.

## Sandbox catalog supplied by the owner

- Product: `PADDLE_PRODUCT_ID (server environment)`
- Price: `PADDLE_PRICE_ID (server environment)`
- Intended price: USD 15.00 every month (1500 minor units).
- Environment: sandbox only. These identifiers must not be used in live checkout.
- Verified through the Paddle sandbox API on 17 September 2026: active product and price,
  matching IDs, USD 1500 minor units every one month, no trial and no country price overrides.
- Tax mode is `location`; checkout must display the final applicable tax treatment.

## Deployed diagnostic

The repository version reads `PADDLE_PRODUCT_ID` and `PADDLE_PRICE_ID` from the server
environment. The owner's current identifiers are kept in ignored `supabase/.env.local`;
the checked-in `.env.example` contains placeholders. Set both variables in the hosted
Edge Function environment before deploying this updated source. The already-deployed
diagnostic has not been changed during this credential cleanup.

`functions/paddle-sandbox-check/index.ts` is deployed in the hosted project. It is a
read-only administrative diagnostic, not checkout or a webhook handler. It only calls the
fixed sandbox price endpoint and returns allowlisted catalog fields. The Paddle key never
leaves the server except as authorization to Paddle's sandbox API.

Legacy gateway JWT verification is off for this function because the dashboard uses newer
secret keys. The function itself requires a configured Supabase server secret via `apikey`
or the legacy service-role bearer token. Publishable keys and ordinary user tokens cannot
authorize it. Do not call it from the Android app or embed server keys there.

Dashboard test returned HTTP 200 with `verified: true` and `checkout_enabled: false`.
The deployed function also returned HTTP 401 when the administrative header was removed.
Local tests: `node --test supabase/tests/paddle-sandbox-check.test.cjs` (Node 22.20+, five passed).
Tests cover unauthorized callers, sandbox restriction, catalog mismatches and error redaction.

## User setup

1. Continue using Paddle Billing sandbox first.
2. Product and price identifiers have been supplied above. Verify their price details
   through the Paddle API once the server key is configured.
3. Provide the approved checkout website/domain or confirm hosted checkout availability.
4. Put the server API key in Supabase Edge Function Secrets as PADDLE_API_KEY, never in the
   APK, repository, or chat. Separate sandbox and live environments.
5. After the handler is deployed, add its exact URL under Developer tools > Notifications.
   Store its endpoint secret as PADDLE_WEBHOOK_SECRET in Supabase, not in chat.
6. Confirm Google Play vs direct APK distribution. Play checkout availability must follow
   the applicable region/program enrollment, disclosure, API and transaction-reporting rules.

## Implementation requirements

- Authenticated server-created checkout, bound to the current Supabase user. Clients cannot
  choose another owner, arbitrary prices or their own entitlement.
- Server allowlist for the configured price. Amount 1500 USD minor units; final displayed
  price, period and tax treatment must reflect the actual Paddle price/checkout response.
- Verify webhook signature over the raw body and enforce timestamp tolerance.
- Durable event-id deduplication, ordering protection, and transactional subscription updates.
- Owner-readable billing records; client writes to entitlement/payment records denied.
- Active/trial/paused/past-due/canceled policy explicit. Cancellation at period end must
  retain access only through the confirmed paid-through date. Handle refunds/revocations.
- Refresh entitlement after returning to the app; no unlock from a deep-link parameter.
- Authenticated customer portal link and cross-device restore from server entitlement.
- Sandbox tests: success, declined payment, duplicate and out-of-order events, cancellation,
  refund, renewal, user isolation, tampered signature, expired signature and offline return.

## Official references

- https://developer.paddle.com/get-started/quickstart/
- https://developer.paddle.com/get-started/how-paddle-works/mobile/
- https://developer.paddle.com/webhooks/about/signature-verification/
- https://support.google.com/googleplay/android-developer/answer/9858738
