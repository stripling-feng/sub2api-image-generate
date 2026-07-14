ALTER TABLE "generation_jobs"
ADD COLUMN "billingStatus" TEXT,
ADD COLUMN "billingAmount" DECIMAL(20,10),
ADD COLUMN "billingApiKeyId" TEXT,
ADD COLUMN "billingUserId" TEXT,
ADD COLUMN "billingAccountId" TEXT,
ADD COLUMN "billingUsageLogId" TEXT,
ADD COLUMN "billingReservedAt" TIMESTAMP(3),
ADD COLUMN "billingSettledAt" TIMESTAMP(3),
ADD COLUMN "billingError" TEXT;

CREATE INDEX "generation_jobs_billingStatus_createdAt_idx"
ON "generation_jobs"("billingStatus", "createdAt");
