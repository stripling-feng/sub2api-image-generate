# GPT-Image-2 Temporary Charge-on-Failure Design

## Goal

Temporarily verify the sub2api billing path by charging GPT-Image-2 requests after a balance reservation succeeds, regardless of whether upstream image generation later succeeds or fails.

## Scope

- Applies only to `gpt-image-2`.
- Controlled by `IMAGE2_CHARGE_ON_FAILURE`.
- Defaults to `false`; the local test environment sets it to `true`.
- Keeps the existing `$0.50 * count` price.
- Does not change `gpt-image-2-4k`.

## Billing Rules

When the switch is disabled, the current behavior remains unchanged: successful jobs settle the reservation and failed jobs release it.

When the switch is enabled:

1. Invalid API keys, insufficient balance, quota failures, and other errors before a reservation succeeds are not charged.
2. Once reservation succeeds, every terminal outcome settles the reservation:
   - upstream creation errors, including HTTP errors and missing task IDs;
   - upstream-reported failure or cancellation;
   - polling timeout or terminal polling error;
   - successful completion.
3. Failed generation jobs remain `FAILED`, while their billing state becomes `CHARGED`.
4. Settlement writes the same standard `usage_logs`, `billing_usage_entries`, API-key usage, and balance updates as successful generation.
5. The generation failure message remains on the local job. Billing settlement failures are recorded separately and must not be reported as successful charges.

## Recovery And Idempotency

Existing settlement idempotency remains keyed by `image-workbench:<jobId>`. Retrying a failed path or restarting the service must not deduct twice.

The abandoned-reservation recovery worker follows the same switch:

- switch enabled: settle any recoverable GPT-Image-2 reservation, including jobs without an upstream task ID;
- switch disabled: preserve the existing release behavior.

## Implementation Shape

Extract one failure-billing decision/helper that receives the job billing fields and outcome metadata. Both the request creation error path and asynchronous poller failure path use it. Successful completion continues using the same settlement primitive.

The settlement detail uses the known operation (`generations` or `edits`), count, size, duration, and standard model mapping. If creation fails before operation is persisted, derive the operation from whether reference images were submitted.

## Tests

- Switch disabled: failed jobs release reservations.
- Switch enabled: creation failure settles and records `CHARGED`.
- Switch enabled: asynchronous failure and timeout settle and record `CHARGED`.
- Repeated handling is idempotent and does not deduct twice.
- Failure before reservation succeeds does not charge.
- Existing successful settlement behavior remains unchanged.

## Rollback

Set `IMAGE2_CHARGE_ON_FAILURE=false` or remove it, then restart the backend. No schema rollback is required.
