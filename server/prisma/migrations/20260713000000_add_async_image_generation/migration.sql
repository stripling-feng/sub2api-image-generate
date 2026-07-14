ALTER TABLE "generation_jobs"
ADD COLUMN "upstreamTaskId" TEXT,
ADD COLUMN "upstreamOperation" TEXT,
ADD COLUMN "upstreamStatus" TEXT,
ADD COLUMN "progress" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN "nextPollAt" TIMESTAMP(3),
ADD COLUMN "pollErrorCount" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN "pollLeaseUntil" TIMESTAMP(3),
ADD COLUMN "completedAt" TIMESTAMP(3);

ALTER TABLE "generated_images"
ADD COLUMN "sourceIndex" INTEGER;

CREATE INDEX "generation_jobs_status_nextPollAt_idx" ON "generation_jobs"("status", "nextPollAt");
CREATE INDEX "generation_jobs_upstreamTaskId_idx" ON "generation_jobs"("upstreamTaskId");
CREATE UNIQUE INDEX "generated_images_jobId_sourceIndex_key" ON "generated_images"("jobId", "sourceIndex");
