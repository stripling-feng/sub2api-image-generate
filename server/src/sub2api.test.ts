import assert from "node:assert/strict";
import test from "node:test";
import { buildAsyncImageRequest, callImageEdit, parseImageTaskPayload } from "./sub2api.js";
import { generateSchema } from "./schemas.js";
import { billingUsageEntryDeltaSql, imageChargeUsd } from "./sub2apiBilling.js";

test("GPT-Image-2 charges USD 0.50 per requested image", () => {
  assert.equal(imageChargeUsd(1), "0.5000000000");
  assert.equal(imageChargeUsd(3), "1.5000000000");
  assert.equal(imageChargeUsd(10), "5.0000000000");
});

test("billing usage entry delta casts USD parameter before negating", () => {
  assert.equal(billingUsageEntryDeltaSql("$4"), "-($4::numeric)");
});

test("GPT-Image-2 async request contains only supported protocol fields", () => {
  assert.deepEqual(buildAsyncImageRequest({
    prompt: "orange cat",
    size: "3:2",
    quality: "high",
    response_format: "b64_json"
  }, 3), {
    model: "cy-img1-gpt-image-2",
    prompt: "orange cat",
    size: "3:2",
    n: 3,
    async: true,
    stream: false
  });
});

test("edit upload uses image for one reference and image[] for many", async () => {
  const originalFetch = globalThis.fetch;
  const fieldSets: string[][] = [];
  globalThis.fetch = async (_input, init) => {
    const form = init?.body as FormData;
    fieldSets.push([...form.keys()]);
    return new Response(JSON.stringify({ id: "task-1", status: "queued" }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };

  try {
    const body = buildAsyncImageRequest({ prompt: "edit", size: "1:1" }, 1);
    const image = { name: "one.png", mimeType: "image/png", data: "iVBORw0KGgo=" };
    await callImageEdit({ baseUrl: "https://example.com", apiKey: "sk-test-key", body, images: [image] });
    await callImageEdit({ baseUrl: "https://example.com/v1", apiKey: "sk-test-key", body, images: [image, image] });
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(fieldSets[0].filter((key) => key === "image").length, 1);
  assert.equal(fieldSets[0].includes("image[]"), false);
  assert.equal(fieldSets[1].filter((key) => key === "image[]").length, 2);
  assert.equal(fieldSets[1].includes("image"), false);
});

test("generation schema accepts ten references and rejects eleven", () => {
  const reference = { name: "ref.png", mimeType: "image/png", data: "a" };
  const base = {
    prompt: "test",
    model: "gpt-image-2",
    size: "1:1",
    count: 1,
    extraParams: { size_tier: "1k" }
  };
  assert.equal(generateSchema.safeParse({ ...base, referenceImages: Array(10).fill(reference) }).success, true);
  assert.equal(generateSchema.safeParse({ ...base, referenceImages: Array(11).fill(reference) }).success, false);
});

test("task payload normalizes progress, status and result URLs", () => {
  assert.deepEqual(parseImageTaskPayload({
    id: "task-2",
    status: "COMPLETED",
    progress: "100%",
    data: [{ url: "https://example.com/1.png" }, { url: "https://example.com/2.png" }]
  }), {
    id: "task-2",
    status: "completed",
    progress: 100,
    urls: ["https://example.com/1.png", "https://example.com/2.png"],
    error: undefined,
    raw: {
      id: "task-2",
      status: "COMPLETED",
      progress: "100%",
      data: [{ url: "https://example.com/1.png" }, { url: "https://example.com/2.png" }]
    }
  });
});
