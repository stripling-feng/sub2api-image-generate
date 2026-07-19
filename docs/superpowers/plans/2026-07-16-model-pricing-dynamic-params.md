# Model Pricing and Dynamic Parameters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make image-model prices configurable per model, drive workbench controls from backend document configuration, and move both frontend development proxies to port 10102.

**Architecture:** Keep the existing `ai_models` configuration and `/api/images/models` endpoint. Add one `numeric` price column, update the seeded JSON schemas from the provider documentation, pass the model price into the existing reservation flow, and reuse the workbench's current schema-driven controls.

**Tech Stack:** PostgreSQL/Flyway, Spring Boot 3, MyBatis Plus, Java `BigDecimal`, Vue 3, TypeScript, Vite, JUnit 5.

---

## File map

- Create `backend/src/main/resources/db/migration/V4__model_pricing_and_document_params.sql`: price column, constraint, existing-model backfill, and provider-document parameter schemas.
- Modify `backend/src/main/java/com/feng/system/module/image/entity/AiModel.java`: map `unitPriceUsd`.
- Modify `backend/src/main/java/com/feng/system/module/image/ImageModelConfigService.java`: validate price and expose it publicly.
- Modify `backend/src/main/java/com/feng/system/module/image/Sub2apiBillingService.java`: accept the selected model price instead of global configuration.
- Modify `backend/src/main/java/com/feng/system/module/image/ImageGenerationService.java`: reserve using the runtime model price and use fixed reference safety limits.
- Modify `backend/src/main/resources/application.yml`: remove the obsolete global image price property.
- Modify `backend/src/test/java/com/feng/system/config/PostgresSchemaTest.java`: verify migration content.
- Modify `backend/src/test/java/com/feng/system/module/image/ImageTaskRulesTest.java`: verify per-model price multiplication and limits.
- Modify `frontend/src/views/model/ImageModelView.vue`: remove JSON/limit fields and add price editing/list display.
- Modify `client/src/types.ts`: add `unitPriceUsd` to public model configuration.
- Modify `client/src/App.vue`: calculate estimates from the selected model and retain schema-driven controls.
- Modify `client/src/DocsDialog.vue`: remove the fixed `$0.50` claim.
- Modify `frontend/vite.config.js` and `client/vite.config.ts`: default proxies to port 10102.

### Task 1: Add model pricing and document schemas

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__model_pricing_and_document_params.sql`
- Modify: `backend/src/main/java/com/feng/system/module/image/entity/AiModel.java`
- Test: `backend/src/test/java/com/feng/system/config/PostgresSchemaTest.java`

- [ ] **Step 1: Extend the migration test and verify it fails**

Add the V4 resource and assertions:

```java
String pricing = read("db/migration/V4__model_pricing_and_document_params.sql");
assertTrue(pricing.contains("unit_price_usd numeric(20,10)"));
assertTrue(pricing.contains("check (unit_price_usd >= 0)"));
assertTrue(pricing.contains("gpt-image-2-4k"));
assertTrue(pricing.contains("\"quality\""));
```

Run:

```powershell
mvn -f backend/pom.xml -Dtest=PostgresSchemaTest test
```

Expected: FAIL because the V4 migration does not exist.

- [ ] **Step 2: Create the V4 migration**

Start with:

```sql
ALTER TABLE ai_models
    ADD COLUMN unit_price_usd numeric(20,10) NOT NULL DEFAULT 0.5,
    ADD CONSTRAINT ai_models_unit_price_usd_check CHECK (unit_price_usd >= 0);

UPDATE ai_models SET unit_price_usd = 0.5 WHERE unit_price_usd IS NULL;
```

In the same migration, update the four existing `model_key` rows. Store:

- `gpt-image-2`: `size` select with `1:1`, `3:2`, `2:3`, `auto`; `max_count = 10`; `supports_mask = 0`.
- `gpt-image-2-1k`: `size` field with 1K pixel bounds, `quality` select, `max_count = 1`.
- `gpt-image-2-2k`: `size` field with 2K pixel bounds, `quality` select, `max_count = 1`.
- `gpt-image-2-4k`: `size` field with 4K pixel bounds, `quality` select, `max_count = 1`.
- All 1K/2K/4K schemas include the eleven documented ratios and default `quality = medium`.
- Keep `max_reference_images = 9` as a server safety limit.

Use the existing `parameter_schema` format (`key`, `label`, `type`, `options`, bounds) so no new renderer is required.

- [ ] **Step 3: Map the price field**

Add to `AiModel`:

```java
private BigDecimal unitPriceUsd;
```

and import `java.math.BigDecimal`.

- [ ] **Step 4: Run the migration test**

```powershell
mvn -f backend/pom.xml -Dtest=PostgresSchemaTest test
```

Expected: PASS.

### Task 2: Expose and validate model prices

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageModelConfigService.java`
- Test: `backend/src/test/java/com/feng/system/module/image/ImageTaskRulesTest.java`

- [ ] **Step 1: Add price rule assertions**

Extend `ImageTaskRulesTest`:

```java
assertEquals(new BigDecimal("0.1200000000"), ImageTaskRules.charge(3, new BigDecimal("0.04")));
assertEquals(new BigDecimal("0E-10"), ImageTaskRules.charge(1, BigDecimal.ZERO));
```

Run:

```powershell
mvn -f backend/pom.xml -Dtest=ImageTaskRulesTest test
```

Expected: PASS for the existing reusable money calculation.

- [ ] **Step 2: Validate prices on admin writes**

In `validateModel` add:

```java
if (model.getUnitPriceUsd() == null || model.getUnitPriceUsd().signum() < 0) {
    throw new BusinessException("单张价格必须大于或等于 0");
}
```

Do not accept `parameterSchema`, `defaultParams`, `maxCount`, or `maxReferenceImages` from admin create/update as configurable values. For create, assign the documented defaults by `modelKey`; for update, retain the stored values before calling `updateById`.

- [ ] **Step 3: Return prices to the workbench**

Add to `publicModel`:

```java
result.put("unitPriceUsd", model.getUnitPriceUsd());
```

Keep the existing `parameters`, `defaults`, `maxCount`, `maxReferenceImages`, and `supportsMask` response fields because the client already consumes them.

- [ ] **Step 4: Run focused backend tests**

```powershell
mvn -f backend/pom.xml -Dtest=ImageTaskRulesTest,PostgresSchemaTest test
```

Expected: PASS.

### Task 3: Use the selected model price for billing

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/image/Sub2apiBillingService.java`
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageGenerationService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/feng/system/module/image/ImageTaskRulesTest.java`

- [ ] **Step 1: Change the reservation signature**

Replace:

```java
public Reservation reserve(String apiKey, int count)
```

with:

```java
public Reservation reserve(String apiKey, int count, BigDecimal unitPrice)
```

and calculate:

```java
BigDecimal amount = ImageTaskRules.charge(count, unitPrice);
```

Delete `@Value("${image.unit-price-usd:0.5}")` and its field.

- [ ] **Step 2: Pass the runtime model price**

In `ImageGenerationService.createAsync` use:

```java
reservation = billing.reserve(
        profile.getEncryptedKey(),
        input.count,
        input.runtime.model().getUnitPriceUsd()
);
```

Keep the reservation amount on `GenerationJob.billingAmount`; poll settlement and release already use that frozen amount.

- [ ] **Step 3: Remove obsolete configuration**

Delete only this line from `application.yml`:

```yaml
unit-price-usd: ${GPT_IMAGE_2_UNIT_PRICE_USD:0.5}
```

- [ ] **Step 4: Run backend tests**

```powershell
mvn -f backend/pom.xml test
```

Expected: all tests PASS.

### Task 4: Simplify image-model administration

**Files:**
- Modify: `frontend/src/views/model/ImageModelView.vue`

- [ ] **Step 1: Replace configurable parameter fields**

Remove the form items for parameter JSON, default JSON, maximum count, and reference-image limit. Add:

```vue
<el-form-item label="单张价格（USD）" prop="unitPriceUsd">
  <el-input-number v-model="form.unitPriceUsd" :min="0" :precision="4" :step="0.01" style="width:100%" />
</el-form-item>
```

Add the rule:

```js
unitPriceUsd: [{ required: true, message: '请输入单张价格' }]
```

Set the create default to `unitPriceUsd: 0.5` and remove client-side JSON parsing from `submit()`.

- [ ] **Step 2: Show the price in the table**

Add a compact column:

```vue
<el-table-column label="单价" width="105">
  <template #default="{ row }">${{ Number(row.unitPriceUsd).toFixed(2) }} / 张</template>
</el-table-column>
```

Replace the existing “张数” column with this price column, so the table width does not increase.

- [ ] **Step 3: Build the admin frontend**

```powershell
npm --prefix frontend run build
```

Expected: Vite build succeeds.

### Task 5: Drive workbench estimates from backend model configuration

**Files:**
- Modify: `client/src/types.ts`
- Modify: `client/src/App.vue`
- Modify: `client/src/DocsDialog.vue`

- [ ] **Step 1: Extend the public model type**

Add to `ImageModelConfig`:

```ts
unitPriceUsd: number | string;
```

- [ ] **Step 2: Replace the fixed estimate**

Replace the fixed `0.5` computed value with:

```ts
const estimatedChargeUsd = computed(() => {
  const price = Number(activeModel.value?.unitPriceUsd ?? 0);
  const count = Math.max(1, Number(form.count) || 1);
  return (price * count).toFixed(2);
});
```

Do not add a second parameter renderer. Continue using `activeModel.parameters`, which already renders `select`, `number`, and text/size controls and applies `activeModel.defaults`.

- [ ] **Step 3: Remove fixed-price documentation**

Replace statements such as “每张固定 $0.50” with “按所选模型后台配置的单张价格计费”.

- [ ] **Step 4: Build the workbench**

```powershell
npm --prefix client run build
```

Expected: Vue/TypeScript build succeeds.

### Task 6: Change development proxies and verify end to end

**Files:**
- Modify: `frontend/vite.config.js`
- Modify: `client/vite.config.ts`

- [ ] **Step 1: Update proxy defaults**

Change every admin proxy target from `http://localhost:10101` to `http://localhost:10102`.

Change the workbench default only:

```ts
const apiProxy = process.env.VITE_API_PROXY ?? "http://localhost:10102";
```

Keep the environment override.

- [ ] **Step 2: Run all automated checks**

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix client run build
```

Expected: all commands exit with code 0.

- [ ] **Step 3: Browser verification with Java on port 10102**

Verify:

1. Image-model create/edit shows price but no JSON or limit fields.
2. The list shows the configured price.
3. Switching between the four models changes aspect-ratio, quality, custom-size, and count controls according to the document schemas.
4. Estimated charge changes with model price and count.
5. A generation job stores `billingAmount = unitPriceUsd × count`.

- [ ] **Step 4: Review the final diff**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only scoped implementation files plus the user's existing unrelated changes are present.
