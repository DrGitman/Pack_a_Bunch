const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { stripTypeScriptTypes } = require('node:module');
const source = stripTypeScriptTypes(fs.readFileSync('supabase/functions/paddle-sandbox-check/index.ts', 'utf8'));
const price = {
  id: 'pri_00000000000000000000000000', product_id: 'pro_00000000000000000000000000',
  status: 'active', product: { id: 'pro_00000000000000000000000000', status: 'active' },
  unit_price: { amount: '1500', currency_code: 'USD' },
  billing_cycle: { interval: 'month', frequency: 1 }, trial_period: null, unit_price_overrides: [],
};
function setup(overrides = {}, payload = price, upstream = 200) {
  let handler, calls = 0;
  const env = { PADDLE_PRODUCT_ID: 'pro_00000000000000000000000000', PADDLE_PRICE_ID: 'pri_00000000000000000000000000', SUPABASE_SECRET_KEYS: '{"default":"test-admin"}', PADDLE_API_KEY: 'pdl_sdbx_apikey_test', ...overrides };
  vm.runInNewContext(source, {
    Deno: { env: { get: key => env[key] }, serve: f => handler = f }, Response, Request, AbortSignal,
    fetch: async (url, options) => {
      calls++;
      assert.equal(new URL(url).host, 'sandbox-api.paddle.com');
      assert.equal(options.redirect, 'error');
      return Response.json({ data: payload }, { status: upstream });
    },
  });
  return { run: (headers = { apikey: 'test-admin' }) => handler(new Request('https://example.test', { method: 'POST', headers })), calls: () => calls };
}
test('rejects public and missing credentials without calling Paddle', async () => {
  const s = setup();
  for (const headers of [{}, { apikey: 'sb_publishable_example' }]) assert.equal((await s.run(headers)).status, 401);
  assert.equal(s.calls(), 0);
});
test('accepts configured admin secret and validates monthly catalog', async () => {
  const result = await (await setup().run()).json();
  assert.equal(result.verified, true);
  assert.equal(result.checkout_enabled, false);
  assert.ok(!JSON.stringify(result).includes('test-admin'));
});
test('rejects live Paddle keys before any network request', async () => {
  const s = setup({ PADDLE_API_KEY: 'pdl_live_apikey_example' });
  assert.equal((await s.run()).status, 503);
  assert.equal(s.calls(), 0);
});
test('missing catalog configuration fails closed without calling Paddle', async () => {
  const s = setup({ PADDLE_PRICE_ID: undefined });
  assert.equal((await s.run()).status, 503);
  assert.equal(s.calls(), 0);
});
test('wrong currency, recurrence, product and amount cannot verify', async () => {
  for (const change of [{unit_price:{amount:'1500',currency_code:'EUR'}}, {unit_price:{amount:'150',currency_code:'USD'}}, {billing_cycle:null}, {product_id:'wrong'}, {status:'archived'}]) {
    assert.equal((await (await setup({}, { ...price, ...change }).run()).json()).verified, false);
  }
});
test('does not relay upstream error contents', async () => {
  const response = await setup({}, { sensitive: 'not-for-client' }, 403).run();
  assert.equal(response.status, 502);
  assert.ok(!(await response.text()).includes('not-for-client'));
});
