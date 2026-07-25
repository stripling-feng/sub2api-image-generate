CREATE TABLE api_profiles (
    id text PRIMARY KEY,
    "baseUrl" text NOT NULL,
    "keyHash" text NOT NULL UNIQUE,
    "encryptedKey" text NOT NULL,
    "defaultModel" text,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" timestamp NOT NULL
);

CREATE TABLE api_sessions (
    id text PRIMARY KEY,
    "tokenHash" text NOT NULL UNIQUE,
    "profileId" text NOT NULL REFERENCES api_profiles(id) ON DELETE CASCADE,
    "expiresAt" timestamp NOT NULL,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX api_sessions_profile_id_idx ON api_sessions("profileId");

CREATE TABLE generation_jobs (
    id text PRIMARY KEY,
    "profileId" text NOT NULL REFERENCES api_profiles(id) ON DELETE CASCADE,
    prompt text NOT NULL, "negativePrompt" text, model text NOT NULL, size text NOT NULL, quality text, style text,
    count integer NOT NULL, "responseFormat" text NOT NULL, params jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING', "errorMessage" text, "durationMs" integer,
    "upstreamTaskId" text, "upstreamOperation" text, "upstreamStatus" text, progress integer NOT NULL DEFAULT 0,
    "nextPollAt" timestamp, "pollErrorCount" integer NOT NULL DEFAULT 0, "pollLeaseUntil" timestamp, "completedAt" timestamp,
    "billingStatus" text, "billingAmount" numeric(20,10), "billingApiKeyId" text, "billingUserId" text,
    "billingAccountId" text, "billingUsageLogId" text, "billingReservedAt" timestamp, "billingSettledAt" timestamp,
    "billingError" text, "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" timestamp NOT NULL
);
CREATE INDEX generation_jobs_profile_created_idx ON generation_jobs("profileId", "createdAt");
CREATE INDEX generation_jobs_status_poll_idx ON generation_jobs(status, "nextPollAt");
CREATE INDEX generation_jobs_upstream_task_idx ON generation_jobs("upstreamTaskId");
CREATE INDEX generation_jobs_billing_created_idx ON generation_jobs("billingStatus", "createdAt");

CREATE TABLE generated_images (
    id text PRIMARY KEY,
    "jobId" text NOT NULL REFERENCES generation_jobs(id) ON DELETE CASCADE,
    "filePath" text NOT NULL, "publicUrl" text NOT NULL, "mimeType" text NOT NULL,
    width integer, height integer, "sizeBytes" integer NOT NULL, "sourceIndex" integer,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT generated_images_job_source_unique UNIQUE("jobId", "sourceIndex")
);
CREATE INDEX generated_images_job_idx ON generated_images("jobId");

CREATE TABLE prompt_templates (
    id text PRIMARY KEY,
    "profileId" text NOT NULL REFERENCES api_profiles(id) ON DELETE CASCADE,
    title text NOT NULL, prompt text NOT NULL, params jsonb NOT NULL,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" timestamp NOT NULL
);
CREATE INDEX prompt_templates_profile_updated_idx ON prompt_templates("profileId", "updatedAt");
