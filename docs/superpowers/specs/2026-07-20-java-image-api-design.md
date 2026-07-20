# Java Image API Design

## Goal

Expose the existing Java image workflow to third-party callers at `https://image.tcboys.de` while reusing their Sub2API API key for authentication. The Java service owns task persistence, polling, image storage, and result retrieval. Generation traffic must not use Sub2API's image task query endpoint.

## Scope

- Document the Java-native image generation and result APIs on the index page.
- Accept `X-API-Key: <SUB2API_KEY>` on Java image endpoints.
- Automatically create the Java-side API profile on the first valid API-key request.
- Keep the existing browser cookie binding flow working.
- Use the configured Java model provider for generation and upstream polling.
- Do not modify Sub2API, add a second API-key system, or expose upstream credentials.

## Public API

### Create a text-to-image task

```http
POST https://image.tcboys.de/api/images/generate
X-API-Key: <SUB2API_KEY>
Content-Type: application/json
```

The JSON body uses the existing Java contract: `model`, `prompt`, optional `count`, and optional generation fields such as `size`, `quality`, `negativePrompt`, `parameters`, and `extraParams`.

The service returns HTTP 202:

```json
{
  "requestId": "0123456789abcdef",
  "count": 1
}
```

### Create an image-edit task

```http
POST https://image.tcboys.de/api/images/generate
X-API-Key: <SUB2API_KEY>
Content-Type: multipart/form-data
```

Multipart requests contain a JSON `payload` part plus `image`, `image[]`, and optional `mask` file parts, matching the existing controller contract.

### Query results

```http
GET https://image.tcboys.de/api/images/results/{requestId}
X-API-Key: <SUB2API_KEY>
```

The response contains `jobs[]`. Callers continue polling while any job is `PENDING`; `SUCCEEDED` jobs expose saved images through `images[].publicUrl`; `FAILED` jobs expose `errorMessage`.

## Authentication

For an `X-API-Key` request, the Java service hashes the key and looks up `api_profiles.keyHash`. If no profile exists, it validates the key against the existing billing database, creates one profile using the configured `image.upstream-base-url`, and continues the original request. Concurrent first requests for the same key must converge on the unique profile rather than fail.

Invalid, disabled, or expired keys return an authentication error and do not create a profile. API keys and upstream credentials are never included in responses or logs.

## Task Flow

1. Authenticate or automatically create the caller's Java profile.
2. Create `generation_jobs` rows before contacting the configured image provider.
3. Submit the generation request directly through the Java provider configuration.
4. Save the returned upstream task ID in `generation_jobs.upstreamTaskId`.
5. Poll the configured upstream generation or edit endpoint in the background.
6. Download completed images, insert `generated_images`, and return their Java-hosted `publicUrl` values from the results endpoint.

Sub2API remains the source for API-key validation and billing data only; its `/v1/images/tasks/{task_id}` endpoint is not part of this flow.

## Documentation

The index dialog will show only `https://image.tcboys.de` endpoints and `X-API-Key` authentication. It will include text-to-image, image-edit, task-query, status, success, and error examples for `gpt-image-2` and `gpt-image-2-4k`. It will remove Sub2API image endpoint examples and the unsupported `/v1/images/tasks/{task_id}` instructions.

## Verification

- Unit test first-request profile creation and repeated-key reuse.
- Contract-test JSON generation, multipart generation, and result lookup with `X-API-Key`.
- Regression-test that index documentation contains Java endpoints and no Sub2API image-task endpoint.
- Run backend tests, client tests, client type checking, and production build.
- Render the index documentation and verify the examples, interaction, and browser console.
