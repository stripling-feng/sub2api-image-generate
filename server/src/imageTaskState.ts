const completedStatuses = new Set(["completed", "succeeded", "success"]);
const failedStatuses = new Set(["failed", "error", "cancelled", "canceled"]);

export type ImageTaskDecision = "completed" | "failed" | "pending" | "timeout";

export function decideImageTaskState(status: string, elapsedMs: number, maxDurationMs: number): ImageTaskDecision {
  if (completedStatuses.has(status)) return "completed";
  if (failedStatuses.has(status)) return "failed";
  if (elapsedMs > maxDurationMs) return "timeout";
  return "pending";
}
