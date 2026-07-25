# URL-Only Generation Inputs Design

## Goal

Image and video generation requests must not accept Base64 input or request Base64 output. Audit records must distinguish client-uploaded files from the actual URL sent upstream.

## Request Rules

- Reject JSON request values containing a Base64 data URL or a Base64 payload with HTTP 422.
- Remove the image endpoint's JSON Base64 compatibility for `referenceImages` and `mask`.
- Reject `responseFormat=b64_json` and `response_format=b64_json` with HTTP 422.
- Continue accepting multipart image, mask, first-frame, last-frame, and reference-image files.
- Continue accepting validated public HTTPS reference image, video, and audio URLs where the model contract supports them.

## Audit Shape

`raw_request` keeps both sides of the request boundary:

```json
{
  "client": {
    "model": "seedance-2.0-fast",
    "uploadedImages": [
      {"name": "ref.png", "mimeType": "image/png", "sizeBytes": 123456}
    ]
  },
  "upstream": {
    "model": "seedance-2.0-fast",
    "image_url": "https://media.example.com/ref.png"
  }
}
```

The client section records multipart files as metadata, never as `"base64"`. The upstream section records the exact structured request body after local files have been converted to URLs. Repeated multipart `input_reference` values are recorded as an array in the upstream section.

If validation or billing fails before an upstream body exists, `upstream` is `null`. Existing rows are not rewritten.

## Response Handling

Upstream response logging remains unchanged. Any Base64 or binary content returned by an upstream service is replaced with the literal `"base64"` before persistence to prevent oversized audit rows.

## Tests

- Reject image JSON Base64 references and masks.
- Reject image `b64_json` response format.
- Reject video Base64/Data URL values recursively.
- Verify multipart file metadata is stored in `client`.
- Verify Seedance and Omni uploaded-image URLs are stored in `upstream`.
- Verify Grok Video no longer has a Base64 request path.
- Run the complete backend test suite and client static tests affected by request construction.
