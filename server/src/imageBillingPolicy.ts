export function failedImageBillingAction(chargeOnFailure: boolean): "settle" | "release" {
  return chargeOnFailure ? "settle" : "release";
}

export type FailedImageBillingContext = {
  jobId: string;
  apiKeyId: string;
  userId: string;
  accountId: string;
  amountUsd: string;
  count: number;
  size: string;
  operation: "generations" | "edits";
  durationMs?: number | null;
};

type FailureBillingDependencies = {
  chargeOnFailure: boolean;
  settle: (args: FailedImageBillingContext) => Promise<{ usageLogId: string }>;
  release: (args: Pick<FailedImageBillingContext, "jobId" | "apiKeyId" | "userId" | "amountUsd">) => Promise<"released" | "settled">;
};

type ImmediateBillingDependencies = {
  chargeImmediately: boolean;
  settle: (args: FailedImageBillingContext) => Promise<{ usageLogId: string }>;
};

type ImageBillingResult = {
  billingStatus: "CHARGED" | "RELEASED" | "RESERVED";
  billingUsageLogId?: string;
  billingSettledAt?: Date;
};

export async function finalizeFailedImageBilling(
  context: FailedImageBillingContext,
  dependencies: FailureBillingDependencies
): Promise<ImageBillingResult> {
  if (failedImageBillingAction(dependencies.chargeOnFailure) === "settle") {
    const settled = await dependencies.settle(context);
    return {
      billingStatus: "CHARGED",
      billingUsageLogId: settled.usageLogId,
      billingSettledAt: new Date()
    };
  }

  const released = await dependencies.release({
    jobId: context.jobId,
    apiKeyId: context.apiKeyId,
    userId: context.userId,
    amountUsd: context.amountUsd
  });
  return released === "settled"
    ? { billingStatus: "CHARGED", billingSettledAt: new Date() }
    : { billingStatus: "RELEASED" };
}

export async function settleReservedImageBilling(
  context: FailedImageBillingContext,
  dependencies: ImmediateBillingDependencies
): Promise<ImageBillingResult> {
  if (!dependencies.chargeImmediately) return { billingStatus: "RESERVED" };

  const settled = await dependencies.settle(context);
  return {
    billingStatus: "CHARGED",
    billingUsageLogId: settled.usageLogId,
    billingSettledAt: new Date()
  };
}
