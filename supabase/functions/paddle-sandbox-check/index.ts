// Administrative, read-only sandbox diagnostic. Never returns credentials or creates payments.
const PRICE = Deno.env.get("PADDLE_PRICE_ID") || "";
const PRODUCT = Deno.env.get("PADDLE_PRODUCT_ID") || "";
const json = (body: unknown, status = 200) => Response.json(body, {
  status, headers: { "Cache-Control": "no-store" },
});

Deno.serve(async (req: Request) => {
  const admin = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  let secretKeys: string[] = [];
  try { secretKeys = Object.values(JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}")); } catch { /* Fail closed. */ }
  const apiKey = req.headers.get("apikey");
  const isAdmin = (admin && req.headers.get("Authorization") === `Bearer ${admin}`) ||
    (apiKey && secretKeys.some((key) => typeof key === "string" && key.length > 0 && key === apiKey));
  if (!isAdmin) {
    return json({ error: "Unauthorized" }, 401);
  }
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  if (!/^pri_[a-z0-9]{26}$/.test(PRICE) || !/^pro_[a-z0-9]{26}$/.test(PRODUCT)) {
    return json({ error: "Paddle catalog configuration is missing" }, 503);
  }
  const key = Deno.env.get("PADDLE_API_KEY");
  if (!key) return json({ error: "PADDLE_API_KEY is missing" }, 503);
  if (!key.startsWith("pdl_sdbx_apikey_")) {
    return json({ error: "A Paddle Billing sandbox API key is required" }, 503);
  }
  try {
    const response = await fetch(`https://sandbox-api.paddle.com/prices/${PRICE}?include=product`, {
      headers: { Authorization: `Bearer ${key}`, "Paddle-Version": "1" },
      signal: AbortSignal.timeout(10000), redirect: "error",
    });
    if (!response.ok) return json({ error: "Paddle price lookup failed", upstream_status: response.status }, 502);
    const { data: p } = await response.json();
    const checks = {
      price: p?.id === PRICE,
      product: p?.product_id === PRODUCT && p?.product?.id === PRODUCT,
      active: p?.status === "active" && p?.product?.status === "active",
      amount: p?.unit_price?.amount === "1500",
      currency: p?.unit_price?.currency_code === "USD",
      monthly: p?.billing_cycle?.interval === "month" && p?.billing_cycle?.frequency === 1,
      no_trial: p?.trial_period === null,
      no_country_overrides: Array.isArray(p?.unit_price_overrides) && p.unit_price_overrides.length === 0,
    };
    return json({
      environment: "sandbox", verified: Object.values(checks).every(Boolean), checks,
      price_id: p?.id, product_id: p?.product_id,
      amount: p?.unit_price?.amount, currency: p?.unit_price?.currency_code,
      billing_cycle: p?.billing_cycle, tax_mode: p?.tax_mode,
      price_status: p?.status, product_status: p?.product?.status,
      checkout_enabled: false,
    });
  } catch {
    return json({ error: "Paddle sandbox check could not complete" }, 502);
  }
});
