CREATE TABLE "video_generation_jobs" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "duration" INTEGER NOT NULL,
    "aspectRatio" TEXT NOT NULL,
    "resolution" TEXT,
    "sourceVideoUrl" TEXT,
    "params" JSONB NOT NULL,
    "upstreamTaskId" TEXT,
    "progress" INTEGER NOT NULL DEFAULT 0,
    "status" "JobStatus" NOT NULL DEFAULT 'PENDING',
    "errorMessage" TEXT,
    "durationMs" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "video_generation_jobs_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "generated_videos" (
    "id" TEXT NOT NULL,
    "jobId" TEXT NOT NULL,
    "filePath" TEXT NOT NULL,
    "publicUrl" TEXT NOT NULL,
    "mimeType" TEXT NOT NULL,
    "sizeBytes" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "generated_videos_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "video_generation_jobs_profileId_createdAt_idx" ON "video_generation_jobs"("profileId", "createdAt");
CREATE INDEX "video_generation_jobs_upstreamTaskId_idx" ON "video_generation_jobs"("upstreamTaskId");
CREATE INDEX "generated_videos_jobId_idx" ON "generated_videos"("jobId");

ALTER TABLE "video_generation_jobs" ADD CONSTRAINT "video_generation_jobs_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "api_profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "generated_videos" ADD CONSTRAINT "generated_videos_jobId_fkey" FOREIGN KEY ("jobId") REFERENCES "video_generation_jobs"("id") ON DELETE CASCADE ON UPDATE CASCADE;
