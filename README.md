# sub2api image workbench

Vue image workbench backed by the Feng Admin Spring Boot service.

## Development

Required environment variables:

```text
MEDIA_DB_PASSWORD=...
SUB2API_DB_PASSWORD=...
```

The default databases are `sub2api_media` for application data and `sub2api` for billing on `5.104.85.25:5432`.

```powershell
npm install
npm run dev
```

The client runs on port `6655`; the backend runs on port `10102`.

## Verification

```powershell
npm test
npm run typecheck
npm run build
```

Database migrations live in `media-java/src/main/resources/db/migration` and are applied with Flyway CLI 12.11.0 before deploying the backend.
`npm run dev` applies pending migrations automatically; packaged deployments should run `npm run db:migrate` before starting the jar.
