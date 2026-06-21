-- CreateEnum
CREATE TYPE "JobStatus" AS ENUM ('PENDING', 'SUCCEEDED', 'FAILED');

-- CreateTable
CREATE TABLE "api_profiles" (
    "id" TEXT NOT NULL,
    "baseUrl" TEXT NOT NULL,
    "keyHash" TEXT NOT NULL,
    "encryptedKey" TEXT NOT NULL,
    "defaultModel" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "api_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "api_sessions" (
    "id" TEXT NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "api_sessions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "generation_jobs" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "negativePrompt" TEXT,
    "model" TEXT NOT NULL,
    "size" TEXT NOT NULL,
    "quality" TEXT,
    "style" TEXT,
    "count" INTEGER NOT NULL,
    "responseFormat" TEXT NOT NULL,
    "params" JSONB NOT NULL,
    "status" "JobStatus" NOT NULL DEFAULT 'PENDING',
    "errorMessage" TEXT,
    "durationMs" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "generation_jobs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "generated_images" (
    "id" TEXT NOT NULL,
    "jobId" TEXT NOT NULL,
    "filePath" TEXT NOT NULL,
    "publicUrl" TEXT NOT NULL,
    "mimeType" TEXT NOT NULL,
    "width" INTEGER,
    "height" INTEGER,
    "sizeBytes" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "generated_images_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "prompt_templates" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "params" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "prompt_templates_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "api_profiles_keyHash_key" ON "api_profiles"("keyHash");

-- CreateIndex
CREATE UNIQUE INDEX "api_sessions_tokenHash_key" ON "api_sessions"("tokenHash");

-- CreateIndex
CREATE INDEX "api_sessions_profileId_idx" ON "api_sessions"("profileId");

-- CreateIndex
CREATE INDEX "generation_jobs_profileId_createdAt_idx" ON "generation_jobs"("profileId", "createdAt");

-- CreateIndex
CREATE INDEX "generated_images_jobId_idx" ON "generated_images"("jobId");

-- CreateIndex
CREATE INDEX "prompt_templates_profileId_updatedAt_idx" ON "prompt_templates"("profileId", "updatedAt");

-- AddForeignKey
ALTER TABLE "api_sessions" ADD CONSTRAINT "api_sessions_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "api_profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "generation_jobs" ADD CONSTRAINT "generation_jobs_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "api_profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "generated_images" ADD CONSTRAINT "generated_images_jobId_fkey" FOREIGN KEY ("jobId") REFERENCES "generation_jobs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prompt_templates" ADD CONSTRAINT "prompt_templates_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "api_profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
