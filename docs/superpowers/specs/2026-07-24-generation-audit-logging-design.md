# Generation Audit Logging Design

Image and video generation jobs persist the original accepted request in `raw_request` and every upstream response in chronological order in `raw_responses`. The response entries contain `phase`, `at`, and the original structured `payload`.

One shared JSON sanitizer recursively preserves objects and arrays while replacing binary values, data URLs, and large Base64 strings with the literal `"base64"`. Credential-shaped fields are stored as `"redacted"`.

The audit snapshot is written when the job is inserted. Create, poll, content-download, and error payloads are appended before later parsing, storage, or billing work, so both successful and failed jobs retain the upstream evidence.
