# PostgreSQL migration

The application uses two fixed PostgreSQL data sources:

- `sub2api_media`: primary MyBatis data source for admin and image workbench data.
- `sub2api`: billing-only `JdbcTemplate` data source.

Production passwords are supplied through environment variables. Development credentials are currently stored in `application-dev.yml`.

## Schema

V1 creates admin tables, V2 creates the workbench tables, and V3 creates model providers/model configuration with the four GPT Image 2 variants.

Apply migrations before starting the backend:

```powershell
docker run --rm `
  -v "${PWD}/backend/src/main/resources/db/migration:/flyway/sql" `
  flyway/flyway:12.11.0 `
  -url="$env:MEDIA_DB_URL" `
  -user="$env:MEDIA_DB_USERNAME" `
  -password="$env:MEDIA_DB_PASSWORD" migrate
```

After admin data exists, apply the idempotent model menus:

```powershell
$env:MEDIA_DB_PASSWORD = "..."
npm run db:menus
```

## Final workbench copy

Run only after the old Express service has stopped accepting writes:

```sh
PGPASSWORD="$SOURCE_PASSWORD" pg_dump \
  -h 5.104.85.25 -U root -d url_key_sub2api --data-only \
  --table=public.api_profiles --table=public.api_sessions \
  --table=public.generation_jobs --table=public.generated_images \
  --table=public.prompt_templates \
| PGPASSWORD="$MEDIA_DB_PASSWORD" psql -1 \
  -h 5.104.85.25 -U root -d sub2api_media -v ON_ERROR_STOP=1
```

Before importing, verify there are no pending jobs without an upstream task ID. Compare all five source and target table counts after the copy.

## Admin data

After applying V1 to an empty target database, migrate the MySQL admin tables once:

```powershell
$env:MYSQL_PASSWORD = "..."
$env:MEDIA_DB_PASSWORD = "..."
npm run db:migrate:admin
```

The command refuses to merge into non-empty `sys_*` tables and writes all target rows in one transaction.
