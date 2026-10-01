// Identifies one item from its cut-out and returns its real size, for the photo scan.
//
// The app sends only the crop of a single found object (never the whole photo), the detector's
// guess at what it is, and the size the photo measured. Gemini says what the item is and, when
// it recognises the exact product, the maker's dimensions; otherwise the usual size of that kind
// of thing, marked as such. Nothing is stored. The Gemini key lives here as a secret and never
// reaches the app.
//
// Secrets: GEMINI_API_KEY (required), GEMINI_MODEL (optional, default below).

const MODEL = Deno.env.get("GEMINI_MODEL") || "gemini-2.5-flash";
const MAX_IMAGE_BYTES = 600_000; // base64 of a ~450 KB JPEG; the app sends ≤ 512 px crops
const MAX_MM = 5000;

const json = (body: unknown, status = 200) => Response.json(body, {
  status, headers: { "Cache-Control": "no-store" },
});

const PROMPT = (hint: string, measured: number[] | null) => `
You are measuring one item for a packing app. The picture is a crop of a single object.
The on-phone detector thought it was: "${hint || "unknown"}".
${measured ? `The photo measured it roughly as ${measured.join(" x ")} mm (width x depth x height); that scale can be badly wrong.` : ""}

1. Say what the object is. If you can recognise the specific product (brand and model, from
   its shape, logo or printed text), name it.
2. Give its real outside size in millimetres: widthMm (left to right as it stands), depthMm
   (front to back) and heightMm (bottom to top).
   - If you recognised the exact product, use the maker's dimensions and set exact to true.
   - Otherwise give the typical size for this kind of object and set exact to false.
3. confidence (0 to 1): how sure you are of the sizes, not just the name.
Never invent a brand. If you cannot tell what it is, set identified to false.
`.trim();

const SCHEMA = {
  type: "OBJECT",
  properties: {
    identified: { type: "BOOLEAN" },
    name: { type: "STRING", description: "Short everyday name, e.g. 'Coffee mug', 'Wireless mouse'" },
    product: { type: "STRING", description: "Brand and model if recognised, else empty" },
    exact: { type: "BOOLEAN" },
    confidence: { type: "NUMBER" },
    widthMm: { type: "NUMBER" },
    depthMm: { type: "NUMBER" },
    heightMm: { type: "NUMBER" },
  },
  required: ["identified", "name", "product", "exact", "confidence", "widthMm", "depthMm", "heightMm"],
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return json({ error: "GEMINI_API_KEY is missing" }, 503);

  let image: string, hint: string, measured: number[] | null;
  try {
    const body = await req.json();
    image = String(body.image ?? "");
    hint = String(body.hint ?? "").slice(0, 60).replace(/[^\p{L}\p{N} '\-]/gu, "");
    measured = Array.isArray(body.measuredMm) && body.measuredMm.length === 3 &&
        body.measuredMm.every((v: unknown) => typeof v === "number" && v > 0 && v < 100_000)
      ? body.measuredMm.map((v: number) => Math.round(v)) : null;
  } catch {
    return json({ error: "Bad request" }, 400);
  }
  if (!image || image.length > MAX_IMAGE_BYTES || !/^[A-Za-z0-9+/=]+$/.test(image)) {
    return json({ error: "Image missing or too large" }, 400);
  }

  try {
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": key },
        body: JSON.stringify({
          contents: [{ parts: [
            { inline_data: { mime_type: "image/jpeg", data: image } },
            { text: PROMPT(hint, measured) },
          ] }],
          generationConfig: { temperature: 0, responseMimeType: "application/json", responseSchema: SCHEMA },
        }),
        signal: AbortSignal.timeout(15000),
        redirect: "error",
      },
    );
    if (!response.ok) return json({ error: "Lookup failed", upstream_status: response.status }, 502);
    const answer = await response.json();
    const text = answer?.candidates?.[0]?.content?.parts?.[0]?.text;
    const r = JSON.parse(text ?? "{}");
    const size = [r.widthMm, r.depthMm, r.heightMm];
    const sane = size.every((v) => typeof v === "number" && v > 0 && v < MAX_MM);
    if (!r.identified || !sane) return json({ identified: false });
    return json({
      identified: true,
      name: String(r.name ?? "").slice(0, 40),
      product: String(r.product ?? "").slice(0, 60),
      exact: r.exact === true,
      confidence: Math.max(0, Math.min(1, Number(r.confidence) || 0)),
      widthMm: Math.round(r.widthMm), depthMm: Math.round(r.depthMm), heightMm: Math.round(r.heightMm),
    });
  } catch {
    return json({ error: "Lookup could not complete" }, 502);
  }
});
