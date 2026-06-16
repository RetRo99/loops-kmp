# Loops KMP SDK — Full API Implementation Plan

## Context

`loops-kmp` is a Kotlin Multiplatform SDK for the [Loops.so](https://loops.so/docs/api-reference)
email API, with this surrounding infrastructure in place: dual trusted/untrusted construction modes,
Ktor engine per platform, `kotlinx.serialization`, `LoopsException` mapping, SKIE Swift interop, and
a KSP processor that generates JVM `CompletableFuture` wrappers.

The goal is to grow the SDK to cover the **entire** Loops OpenAPI surface, **stage by stage**,
organizing the endpoints as **grouped sub-APIs** (e.g. `client.contacts.create(...)`) rather than
flat methods on `LoopsClient`. Phases 1–7 reach **official `loops-js` parity** (24 methods); Phases
8–9 add endpoints **beyond** the official SDK (transactional-email management, uploads).

**Progress so far:** Phase 0 (infra: `LoopsHttp` request helper, sub-API wiring, KSP `@JvmAsync` via
Option B) is complete except 0d (`RateLimit`/429); Phase 1 has Stage 1.0 (`LoopsValue` +
custom-property serializer), Stage 1.3 (`contacts.find`), and Stage 1.1 (`contacts.create`) done.
Remaining: 0d, the rest of Phase 1 (1.2/1.4/1.5/1.6), and Phases 2–7. See the status legend in
"Staged rollout".

This document is a living design/spec: the sections below define the architecture, conventions,
per-stage endpoint breakdown, model field lists, and the tricky serialization decisions so the work
can be executed predictably. Stages are marked ✅ Done / 🔵 In progress / ⬜ Todo.

The intended outcome: a complete, idiomatic, security-conscious Loops SDK usable from
Android/Kotlin (suspend), JVM/Java (`CompletableFuture`), and iOS/Swift (async/await via SKIE).

---

## Existing conventions to follow (do not reinvent)

All discovered in the current codebase — new code must match these:

- **Package layout**: `com.retro99.loops.sdk` (client + infra), `com.retro99.loops.sdk.model` (DTOs).
- **Models**: `data class`, `@Serializable`, `@SerialName` on every field, nullable+default for
  optionals. Response suffix `*Response`; request bodies suffix `*Request`. (Current example:
  `model/ApiKey.kt` → `ApiKeyResponse`.)
- **Error mapping**: every network call goes through `LoopsHttp.execute { }`, which maps
  `ResponseException` → `LoopsException.Api`, `SerializationException` →
  `LoopsException.Serialization`, anything else → `LoopsException.Network`. No Ktor/third-party
  exception ever escapes the public API. **To add (see Decision 6):** intercept HTTP **429** →
  `LoopsException.RateLimit(limit, remaining)` before it falls through to `Api`.
- **HTTP**: single shared `HttpClient` on `LoopsClient` (`LoopsClient.kt:57`). `expectSuccess=true`,
  base URL with enforced trailing slash, JSON content negotiation. Relative paths only
  (`client.get("api-key")`), resolved against `config.baseUrl`.
- **Construction**: `direct(...)` / `proxy(...)` factories + Swift-native secondary constructors.
  Auth is injected centrally (bearer for direct, `LoopsProxyAuth` plugin for proxy) — **sub-APIs
  must not touch auth**, they only build requests.
- **JSON config** (`LoopsClient.kt:163`): `ignoreUnknownKeys=true`, `isLenient=true`,
  `encodeDefaults=false`, `explicitNulls=false`. Reuse this single `Json` instance for any manual
  (de)serialization.
- **KSP `@JvmAsync`**: annotation on `LoopsClient` generates `*Async()` JVM wrappers via reflection
  over suspend functions. **See "Async wrappers & sub-APIs" below — this is the one piece of
  infrastructure that needs a decision when introducing sub-APIs.**
- **Tests**: `commonTest` with Ktor `MockEngine`, Given/When/Then, asserting on captured
  request path/headers/body and on parsed response (`LoopsClientTest.kt`).
- **Base URL**: `https://app.loops.so/api/v1/` (`LoopsClient.LOOPS_BASE_URL`). All paths in the spec
  are `/v1/...`; since the base already includes `/api/v1/`, **relative paths drop the `v1/`**
  (e.g. spec `POST /v1/contacts/create` → `client.post("contacts/create")`).

---

## Architectural decisions

### 1. Grouped sub-APIs

Introduce one class per resource group, each holding a reference to the shared `HttpClient` (and the
shared `request{}` wrapper). Exposed as `val` properties on `LoopsClient`:

```kotlin
class LoopsClient private constructor(...) {
    val contacts: ContactsApi
    val events: EventsApi
    val transactional: TransactionalApi
    val lists: MailingListsApi
    val campaigns: CampaignsApi
    val emailMessages: EmailMessagesApi
    val themes: ThemesApi
    val components: ComponentsApi
    val uploads: UploadsApi
    val sendingIps: DedicatedSendingIpsApi
    // testApiKey() stays directly on LoopsClient (account-level, not a resource group)
}
```

- **Sharing the request wrapper**: `LoopsClient.request{}` is currently `private inline`. To let
  sub-APIs reuse it, extract the request/error-mapping concern into an `internal` helper both the
  client and sub-APIs call. Recommended shape: an `internal class LoopsHttp(val client: HttpClient)`
  (or an `internal` function `executeRequest`) holding the `request{}` logic; pass one instance into
  every sub-API constructor. Sub-APIs never construct or configure the `HttpClient` themselves.
- **Usage** (identical ergonomics across platforms thanks to SKIE):
  ```kotlin
  client.contacts.create(CreateContactRequest(email = "a@b.com"))   // Kotlin suspend
  client.events.send(EventRequest(eventName = "signup", email = "…"))
  ```

### 2. Custom contact properties (the one hard serialization problem)

`contacts/create`, `contacts/update`, and `events/send` accept **arbitrary top-level custom
properties** (string | number | boolean | date) merged alongside the known fields via OpenAPI
`additionalProperties`. A plain `@Serializable data class` cannot emit unknown top-level keys.

**Decision**: model the known fields as typed properties **plus** a
`customProperties: Map<String, LoopsValue> = emptyMap()` field, and write a small custom
`KSerializer` for the request type that flattens `customProperties` into the top-level JSON object
(and, on the way in for responses where relevant, collects unknown keys back into the map).

- `LoopsValue` (as built in Stage 1.0) is a sealed type covering the **full JSON shape**, a superset
  of every type the official SDK declares: `StringValue` / `NumberValue` / `BooleanValue` /
  `NullValue` / `ListValue` / `ObjectValue`, with `of(...)` factory helpers. `NullValue` is required:
  the official SDK uses JSON `null` to **reset** a custom property, and `LoopsValueSerializer` emits
  `JsonNull` for it. Modelling the full shape makes decoding **total** (every value the API returns
  maps to a case, so the serializer never throws). Pass mixed properties as
  `mapOf("plan" to LoopsValue.of("pro"))`.
- `mailingLists` is a `Map<String, Boolean>` (list ID → subscribed) — serializes natively, no custom
  work.
- `eventProperties` / `dataVariables` are free-form objects → model as
  `Map<String, LoopsValue>` (same approach, but they are nested objects, not flattened — simpler).

This serializer lives in `model/` next to the request types and is unit-tested directly (encode a
request with mixed custom props → assert exact JSON shape).

**Exact flattening shape (the one thing to get right).** `customProperties` are NOT nested under a
`customProperties` key — they are merged as **top-level** siblings of the known fields:

```kotlin
CreateContactRequest(
    email = "a@b.com",
    firstName = "A",
    customProperties = mapOf("plan" to LoopsValue.of("pro"), "age" to LoopsValue.of(3.0)),
)
// CORRECT  → {"email":"a@b.com","firstName":"A","plan":"pro","age":3}
// WRONG    → {"email":"a@b.com","firstName":"A","customProperties":{"plan":"pro","age":3}}
```

`eventProperties` / `dataVariables`, by contrast, ARE nested under their own key (they are real
nested objects, not flattened). Same literal-JSON assertion applies to Stages 1.1, 1.2, and 3.0.

### 3. Pagination

Many list endpoints share one envelope. Define once and reuse:

```kotlin
@Serializable
data class Pagination(
    val totalResults: Int? = null,
    val returnedResults: Int? = null,
    val perPage: Int? = null,
    val totalPages: Int? = null,
    val nextCursor: String? = null,
    val nextPage: String? = null,
)

@Serializable
data class Page<T>(val pagination: Pagination, val data: List<T>)
```

**Decision (committed):** use the generic `Page<T>` as written. At Stage 4.0, run the cross-target
compile (`compileKotlinIosArm64` + build the XCFramework) **before** wiring any paginated endpoint.
If — and only if — SKIE fails to export generic `Page<T>` to ObjC, fall back to concrete
per-resource page types (`CampaignPage`, `ThemePage`, `TransactionalEmailPage`, …), each
`data class XPage(val pagination: Pagination, val data: List<X>)`. Record the outcome here and use one
style consistently for all paginated stages.

### 4. Identifying a contact by email OR userId

Several endpoints (`find`, `delete`, `suppression`) take **exactly one** of `email`/`userId`.

**Decision (committed — adopt from Stage 1.4 onward):** model this as a sealed
`ContactIdentifier` with `ByEmail(val email: String)` / `ByUserId(val userId: String)`, and translate
to the correct query param / body field internally. This prevents the "both or neither" misuse at the
type level and reads well as a Swift enum. The already-shipped `ContactsApi.find` keeps its two
nullable params for now; **migrate it to `ContactIdentifier` when Stage 1.4 lands** so all four
identifier endpoints share one signature style.

### 5. Async wrappers & sub-APIs (KSP)

`@JvmAsync` currently annotates `LoopsClient` and walks its suspend functions. Once endpoints move
onto sub-API classes, the generator must produce `*Async` wrappers for the **sub-API** suspend
functions too (JVM/Java callers need `client.contacts.createAsync(...)`).

**Resolved (Option B, done).** Each sub-API is annotated `@JvmAsync`; the processor generates
`${fn}Async()` extensions per sub-API type. The single `asyncScope` lives on **`LoopsHttp`** (not on
each sub-API), and the processor emits `http.asyncScope.future { … }` — so `LoopsClient` and every
sub-API share one scope reached via their internal `http`. Confirmed building on JVM.

### 6. Rate limiting (HTTP 429)

The official `loops-js` SDK throws a dedicated `RateLimitExceededError` on **429**, reading the
`x-ratelimit-limit` / `x-ratelimit-remaining` response headers. We currently fold 429 into
`LoopsException.Api`, forcing callers to string-match the status to detect throttling.

**Decision:** add `LoopsException.RateLimit(val limit: Int, val remaining: Int)`. In
`LoopsHttp.execute`, catch `ResponseException` and, when `status == 429`, read the two headers
(defaulting to a sane fallback if absent) and throw `RateLimit` instead of `Api`. Everything else
maps as before. *Tests:* a 429 MockEngine response with the headers → `RateLimit` with parsed values;
a 429 without headers → `RateLimit` with fallbacks (never crashes).

### 7. Contact-property type

The official SDK types the **create** property type as a closed set: `"string" | "number" |
"boolean" | "date"`. Model the *create request* `type` with a `ContactPropertyType` enum over those
four values (with `@SerialName` lowercase). For the *list/read* `ContactProperty.type`, keep a plain
`String` — the server may return additional/derived types and reads must never fail on an unknown
value.

### 8. Date custom-property values (live-validated 2026-06-17)

`LoopsValue` has **no dedicated date case** — a date is whichever JSON primitive Loops accepts for a
`date`-typed custom property. Verified by live smoke test against `app.loops.so` (created a
`date`-typed property, POSTed contacts in five wire forms, read back the stored value):

| Wire form sent | Accepted? | Stored as |
|---|---|---|
| ms as JSON number `1705486871000` | ✅ | `2024-01-17T10:21:11.000Z` |
| ms as JSON number, scientific `1.705486871E12` (what `NumberValue` emits) | ✅ | `2024-01-17T10:21:11.000Z` |
| ms as JSON **string** `"1705486871000"` | ❌ **HTTP 400** | — |
| ISO-8601 string `"2024-01-17T10:21:11Z"` | ✅ | `2024-01-17T10:21:11.000Z` |
| **seconds** as JSON number `1705486871` | ✅ (but wrong) | `1970-01-20T…` — read as ms, off by 1000× |

**Decision:** two distinctly-named companion factories (NOT overloads — SKIE mangles overloaded
`of(...)` into unusable `of(value_:)` / `of(value__:)` Swift selectors; distinct names generate clean
ones, verified in the generated `LoopsSdk.h`):

```kotlin
fun ofDateMillis(epochMilliseconds: Long) = NumberValue(epochMilliseconds.toDouble())  // ms timestamp
fun ofDateString(iso8601: String) = StringValue(iso8601)                                // ECMA-262 string
```

- `Long→Double` is lossless for any real date (`< 2^53` ms ≈ ±285k years); proven exact on JVM **and**
  Kotlin/Native, both serializing to identical output.
- Swift selectors: `ofDateMillis(epochMilliseconds:)` (bridges as `int64_t`) and
  `ofDateString(iso8601:)` — confirmed in the generated header.
- **Two traps the testing exposed, encoded in the factories:** never send a numeric ms timestamp as a
  *string* (400), and never send **seconds** (silently stored as 1970). `ofDateMillis` takes ms only.
- No `kotlinx-datetime` dependency, no new sealed case, no serializer change. (Reaffirms the
  "no date lib" choice at Stage 4.2 for `createdAt`/`updatedAt`.)

**Why no `DateValue` case (decode is the blocker, live-verified).** On read-back a date property and a
plain string property are returned **byte-identical** — `contacts/find` gives `"sdkDateTest":
"2024-01-17T10:21:11.000Z"` and `"sdkPlainStr": "2024-01-17T10:21:11.000Z"` with no distinguishing
tag. The type (`"date"`) exists **only** in the separate `contacts/properties` endpoint
(`{"key":"sdkDateTest","type":"date"}`), never inline with the contact. So a self-contained
`LoopsValueSerializer` (which sees only the `JsonElement`) genuinely cannot decode a date as anything
but `StringValue`. A `DateValue` case would therefore either (a) never appear on decode → asymmetric
round-trip (`ofDate` encodes, decodes back as `StringValue`), or (b) require stateful, network-joined
decoding against the property-type map → breaks the "decode is total / self-describing value"
invariant (Decision 2). **If first-class read-side dates are ever needed, do it as a higher-level
schema-aware accessor** (joins a contact with the `contacts/properties` key→type map), **not** a
`LoopsValue` case.

---

## Staged rollout

**One endpoint per stage.** Each stage adds (at most) a single endpoint plus the models it needs,
with its own MockEngine tests, and is independently mergeable. Foundational infrastructure (Phase 0)
is split into three micro-steps. Versioning/tagging need not happen every stage — batch a few into a
release; the existing CI handles Maven Central + SPM.

**Status legend:** ✅ Done · 🔵 In progress · ⬜ Todo. **A stage is not ✅ Done until `./gradlew
allTests` passes** — this unit-test gate is mandatory per stage and non-negotiable. The heavier
checks (cross-target compile, Swift smoke, live smoke) may still be batched per release (see
"Verification"). **Note on naming:** response type names below (`ContactWriteResponse`,
`DeleteResponse`, `SuccessResponse`, …) are deliberate idiomatic-KMP renames — they do NOT match the
official `loops-js` names (`ContactSuccessResponse`, etc.); do not "correct" them back to the
official names. Conventions in "Existing conventions" apply to
every model below: `@Serializable` data class, `@SerialName` per field, nullable fields default to
`null`. All field names are the **exact JSON keys** (camelCase) from the spec. Response/request types
that are essentially `{success: Boolean}` reuse a shared **`SuccessResponse`** (introduced Stage 2.1).
Every stage's tests use Ktor `MockEngine` and assert: **method + relative path + query/body/headers**
sent, and the **parsed response**; plus one `LoopsException.Api` (non-2xx) and (per sub-API, once) a
`LoopsException.Network` case.

> **Identifier convention (committed — see Decision 4).** `find`/`delete`/`suppression` take exactly
> one of email/userId, modelled as the sealed `ContactIdentifier` (`ByEmail` / `ByUserId`). Adopt it
> from Stage 1.4 onward, and in the same change migrate the already-shipped `ContactsApi.find` (which
> currently uses two nullable params) to take a `ContactIdentifier`. Every identifier endpoint below
> takes `identifier: ContactIdentifier`; internally it maps `ByEmail`→`email`, `ByUserId`→`userId` on
> the query (GET) or body (POST).

### Phase 0 — Infrastructure (no new endpoints)

- ✅ **0a — Shared request helper.** Extracted into `internal class LoopsHttp(client)` with
  `suspend fun <T> execute(block)` doing the `ResponseException`→`Api` /
  `SerializationException`→`Serialization` / else→`Network` mapping. `LoopsClient.testApiKey()` calls
  `http.execute { get("api-key").body() }`.
- ✅ **0b — First sub-API wired.** `api/` package created; `ContactsApi internal constructor(http)`
  exposed as `LoopsClient.contacts`.
- ✅ **0c — KSP `@JvmAsync` on a sub-API.** Resolved via **Option B**: `asyncScope` lives on
  `LoopsHttp` (not each sub-API); the processor emits `http.asyncScope.future { … }`. Confirmed:
  `ContactsApiAsync.kt` + `LoopsClientAsync.kt` generate and compile on JVM. (Supersedes the plan's
  "pass `asyncScope` into each sub-API" note.)
- ⬜ **0d — `LoopsException.RateLimit` (Decision 6).** Add the subtype; in `LoopsHttp.execute`,
  map HTTP 429 → `RateLimit(limit, remaining)` (from `x-ratelimit-*` headers, with fallbacks) before
  the generic `Api` mapping. *Tests:* 429 with/without headers → `RateLimit`.

### Phase 1 — Contacts (`client.contacts`)  ← validates the hard serialization parts

- ✅ **1.0 — `LoopsValue` + custom-property serializer (no endpoint).** Sealed `LoopsValue` with six
  cases — `StringValue`, `NumberValue(Double)`, `BooleanValue`, `ListValue`, `ObjectValue`,
  `NullValue` — covering every type the official SDKs declare (contact props `string|number|boolean|
  null`; data variables `string|number|Array<Record<…>>`). `LoopsValueSerializer` bridges recursively
  through `JsonElement`, so **decode is total and never throws**. `of(...)` factories for each case,
  plus `ofDateMillis(epochMilliseconds: Long)` / `ofDateString(iso8601: String)` for date properties
  (live-validated wire forms — see Decision 8).
  `ContactPropertiesSerializer.merge(known, custom, json)` / `extract(full, knownKeys, json)`
  flatten/collect a `Map<String, LoopsValue>` at the top level. *(19 `LoopsValueTest` cases.)*
  Shared `sdkJson` config extracted to `SdkJson.kt`. *Tests:* 14 in `LoopsValueTest` (round-trips,
  unicode/special chars, large/exponent numbers, 1000-prop stress).
- ✅ **1.3 — `GET contacts/find`** (query `email` | `userId`) → `List<Contact>`.
  `Contact(id, email, firstName?, lastName?, source?, subscribed?, userGroup?, userId?,`
  `mailingLists: Map<String,Boolean>?, optInStatus?)`. *Tests:* parse list; non-2xx→`Api`;
  transport→`Network` (`ContactsApiTest`, 3). *(Spec marks source/subscribed/userGroup required; code
  keeps them nullable for resilience — fine given `ignoreUnknownKeys`.)*
- ✅ **1.1 — `POST contacts/create`** → `CreateContactRequest` → `ContactWriteResponse(success, id)`.
  Request fields: `email` (required) + `firstName?`, `lastName?`, `subscribed?`, `userGroup?`,
  `userId?`, `mailingLists: Map<String,Boolean>?`, **`customProperties: Map<String,LoopsValue> =
  emptyMap()`** flattened to top level via the Stage 1.0 serializer. `ContactsApi.create` wired;
  `CreateContactRequestSerializer` delegates the known fields to a private `@Serializable`
  surrogate (`CreateContactKnownFields`) so encode/decode share one source of `@SerialName` keys and
  null-dropping is `sdkJson`'s job (`explicitNulls=false`), not hand-written. *Tests
  (`ContactsCreateApiTest`, 7):* exact flattened JSON; custom-prop-cannot-shadow-known; encode→decode
  round-trip across every `LoopsValue` case; decode collects unknown keys; **real Ktor wire body**
  asserted (not just `encodeToString`); POST path + parse; non-2xx→`Api`; transport→`Network`.
  `./gradlew allTests` green (JVM + Android + iOS sim).

  **Review findings folded in (2026-06-17):**
  - *`knownKeys` drift (decode):* the set of known keys used by `extract` is now **derived from
    `CreateContactKnownFields.serializer().descriptor`**, not a hand-maintained literal — adding a
    field can no longer let it leak into `customProperties` on decode.
  - *Custom prop shadowing a known field (encode, real bug):* `ContactPropertiesSerializer.merge`
    previously did `known + customObject`, letting a custom key (e.g. `"email"`) **overwrite** the
    typed field on the wire — and `extract` then dropped it on decode, so it wasn't round-trip
    stable. Fixed: known fields are authoritative and keep declaration order
    (`known + custom.filterKeys { it !in known }`). Covered by a dedicated test.
  - *Hand-rolled `buildJsonObject`:* removed in favor of serializing the surrogate (see above).
- ⬜ **1.2 — `PUT contacts/update`** → `UpdateContactRequest` → `ContactWriteResponse`. Same shape as
  create but `email`/`userId` both optional (one required at runtime). *Tests:* update by email vs by
  userId; custom-prop flatten; non-2xx→`Api`.
- ⬜ **1.4 — `POST contacts/delete`** (body `{email}` | `{userId}`) → `DeleteResponse(success, message)`.
  *Tests:* body carries the chosen identifier only; success parse; non-2xx→`Api`. **Identifier
  decision applies here.**
  **This stage ALSO migrates the already-shipped `ContactsApi.find`:** change its signature from
  `find(email: String?, userId: String?)` → `find(identifier: ContactIdentifier)`, map
  `ByEmail`→`email` / `ByUserId`→`userId` on the query, and update `ContactsApiTest` accordingly. The
  stage is not complete while `find` and `delete` use different identifier styles.
- ⬜ **1.5 — `GET contacts/suppression`** (query `email` | `userId`) →
  `SuppressionStatusResponse(contact: SuppressionContact(id, email, userId?), isSuppressed: Boolean,`
  `removalQuota: RemovalQuota(limit: Int, remaining: Int))`. *(new: `SuppressionContact`,
  `RemovalQuota`)* *Tests:* parse all fields; non-2xx→`Api`.
- ⬜ **1.6 — `DELETE contacts/suppression`** (query `email` | `userId`) →
  `SuppressionRemovalResponse(success, message, removalQuota: RemovalQuota)`. *Tests:* DELETE verb +
  query; parse; non-2xx→`Api`.

### Phase 2 — Contact Properties (`client.contactProperties`)  ⬜

- ⬜ **2.0 — `GET contacts/properties`** (query `list`: `all` | `custom`, default `all`) →
  `List<ContactProperty>`. `ContactProperty(key, label, type: String)` — **read `type` as `String`**
  (server may return unknown/derived types; reads must never fail). See Decision 7. *Tests:* `list`
  query forwarded; parse list; non-2xx→`Api`.
- ⬜ **2.1 — `POST contacts/properties`** →
  `CreatePropertyRequest(name: String, type: ContactPropertyType)` where `ContactPropertyType` is the
  closed enum `String | Number | Boolean | Date` (Decision 7) → **`SuccessResponse(success: Boolean)`**
  *(new, shared by later `{success}` endpoints)*. *Tests:* body fields (`type` serialized lowercase);
  success parse; non-2xx→`Api`.

### Phase 3 — Events & Transactional  ⬜

- ⬜ **3.0 — `POST events/send`** (`client.events`) → `EventRequest` → `SuccessResponse`.
  Fields: `eventName` (required), one of `email`/`userId`, `eventProperties: Map<String,LoopsValue>?`
  (nested object — not flattened), `mailingLists: Map<String,Boolean>?`, plus top-level flattened
  `customProperties` (reuses Stage 1.0 serializer). Method signature carries optional
  `idempotencyKey: String? = null` → set `Idempotency-Key` header (≤100 chars) per call. *Tests:*
  event body shape (flattened custom props vs nested `eventProperties`); idempotency header present
  when passed / absent when null; non-2xx→`Api`; (network case for this sub-API).
- ⬜ **3.1 — `POST transactional`** (`client.transactional`) → `TransactionalSendRequest` →
  `SuccessResponse`. Fields: `email` (required), `transactionalId` (required), `addToAudience?`,
  `dataVariables: Map<String,LoopsValue>?` (nested), `attachments: List<Attachment>?` where
  `Attachment(filename, contentType, data)` (`data` = base64). Same `idempotencyKey` param. *Tests:*
  full body incl. attachments array; idempotency header; non-2xx→`Api`.

### Phase 4 — Mailing Lists + Campaigns  ← first pagination  ⬜

- ⬜ **4.0 — `Pagination` / `Page<T>` (no endpoint).**
  `Pagination(totalResults?, returnedResults?, perPage?, totalPages?, nextCursor?, nextPage?)` (all
  `Int?`/`String?`). `Page<T>(pagination, data: List<T>)`. **Resolve the SKIE generics question here:**
  if generic `Page<T>` doesn't export cleanly to ObjC, fall back to concrete `CampaignPage`,
  `TransactionalEmailPage`, etc. (each `(pagination, data: List<Concrete>)`). *Tests:* decode a
  representative envelope.
- ⬜ **4.1 — `GET lists`** (`client.lists`) → `List<MailingList>`.
  `MailingList(id, name, description, isPublic: Boolean)`. *Tests:* parse list; non-2xx→`Api`; network.
- ⬜ **4.2 — `GET campaigns`** (`client.campaigns`, query `perPage` 10–50 default 20, `cursor`) →
  `Page<Campaign>` (or `CampaignPage`). `Campaign(id, name, status: String, createdAt, updatedAt,`
  `emailMessageId?)`; **list items also carry `subject`** — model `subject: String? = null` so the
  one `Campaign` type serves both list and single-get. `status` as `String` (values: `Draft`,
  `Scheduled`, `Sending`, `Sent`) — keep `String` for forward-compat. `createdAt`/`updatedAt` as
  `String` (ISO-8601; no date lib dependency). *Tests:* `perPage`/`cursor` query forwarded; envelope
  parsed; non-2xx→`Api`.
- ⬜ **4.3 — `POST campaigns`** → **`NameRequest(name: String)`** *(new, shared)* → `Campaign`. *Tests:* body; parse.
- ⬜ **4.4 — `GET campaigns/{id}`** → `Campaign`. *Tests:* path interpolation; parse; non-2xx→`Api`.
- ⬜ **4.5 — `POST campaigns/{id}`** → `NameRequest` → `Campaign`. *Tests:* path + body; parse.

### Phase 5 — List Transactional Emails (`client.transactional.list`)  ⬜

> **Path note (vs. official `loops-so/loops-js`).** The official SDK's `getTransactionalEmails`
> hits **`GET v1/transactional`** (not `/transactional-emails`). Both paths exist in the raw spec; we
> use `v1/transactional` to match official behavior. The full transactional-email **management CRUD**
> (create / update / draft / publish) is **beyond** the official SDK and is deferred to **Phase 8**
> (extended coverage) — see below. This phase is just the list read, on the existing
> `client.transactional` group from Stage 3.1.

- ⬜ **5.0 — `GET transactional` (list)** (`client.transactional`, query `perPage` default 20,
  `cursor`) → `Page<TransactionalEmail>` *(new: `TransactionalEmail`)*.
  `TransactionalEmail(id, name, lastUpdated, dataVariables: List<String>)`. *Tests:* `perPage`/`cursor`
  forwarded; envelope parsed; one `Api` case.

### Phase 6 — Email Messages, Themes, Components  ⬜

- ⬜ **6.0 — `GET email-messages/{id}`** (`client.emailMessages`) → `EmailMessage`.
  `EmailMessage(id, campaignId?, subject, previewText, fromName, fromEmail, replyToEmail, lmx,`
  `contentRevisionId?, updatedAt, warnings: List<EmailMessageWarning>? = null)` where
  `EmailMessageWarning(rule, severity, message, path)`. *Tests:* parse incl. `lmx` + optional warnings.
- ⬜ **6.1 — `POST email-messages/{id}`** → `UpdateEmailMessageRequest(expectedRevisionId: String,`
  `subject?, previewText?, fromName?, fromEmail?, replyToEmail?, lmx?)` → `EmailMessage`. *Tests:*
  body sends only set fields (`encodeDefaults=false`); `expectedRevisionId` always present; parse.
- ⬜ **6.2 — `GET themes`** (`client.themes`, paginated) → `Page<Theme>`.
  `Theme(id, name, styles: ThemeStyles, isDefault: Boolean, createdAt, updatedAt)`.
  `ThemeStyles` = the ~40 optional style fields (`backgroundColor`, `borderRadius`, `buttonTextColor`,
  `heading1Color`, … full list in spec). **Decision (committed):** type every style field
  `LoopsValue? = null` — values are colors/sizes that arrive as either JSON strings or numbers, and
  `LoopsValue` already preserves both losslessly without per-field guesswork. *Tests:* parse a theme
  with a partial `styles` object mixing string and numeric values.
- ⬜ **6.3 — `GET themes/{id}`** → `Theme`. *Tests:* path; parse.
- ⬜ **6.4 — `GET components`** (`client.components`, paginated) → `Page<Component>`.
  `Component(id, name, lmx)`. *Tests:* envelope parse.
- ⬜ **6.5 — `GET components/{id}`** → `Component`. *Tests:* path; parse.

### Phase 7 — Dedicated Sending IPs  ⬜

- ⬜ **7.0 — `GET dedicated-sending-ips`** (`client.sendingIps`) → `List<String>`. *Tests:* parse
  string array; non-2xx→`Api`; network.

---

## Extended coverage (beyond the official SDK)

These endpoints exist in the OpenAPI spec but are **not** exposed by the official `loops-js` SDK. We
deliberately cover **more** than official — these phases ship **after** the official-parity surface
(Phases 1–7) is complete, as lower priority. Models/conventions are unchanged.

### Phase 8 — Transactional Emails management (`client.transactional`)  ⬜  *(extended)*

Extends the `TransactionalEmail` model from Stage 5.0 with the management fields
(`draftEmailMessageId?`, `publishedEmailMessageId?`, `createdAt`, `updatedAt`,
`draftEmailMessageContentRevisionId?`). Methods land on the existing `client.transactional` group.

- ⬜ **8.0 — `POST transactional-emails`** → `NameRequest` → `TransactionalEmail` (create draft).
- ⬜ **8.1 — `GET transactional-emails/{id}`** → `TransactionalEmail`.
- ⬜ **8.2 — `POST transactional-emails/{id}`** → `NameRequest` → `TransactionalEmail` (update metadata).
- ⬜ **8.3 — `POST transactional-emails/{id}/draft`** → `TransactionalEmail` (ensure draft exists).
- ⬜ **8.4 — `POST transactional-emails/{id}/publish`** → `TransactionalEmail` (publish draft).
- *Tests (each):* method + path (+ body where present); parse; one `Api` case.

### Phase 9 — Uploads (`client.uploads`)  ⬜  *(extended)*

- ⬜ **9.0 — `POST uploads`** →
  `CreateUploadRequest(contentType: String, contentLength: Int)` (contentType ∈
  image/jpeg|png|gif|webp; contentLength ≤ 4_000_000) → `UploadUrlResponse(emailAssetId,`
  `presignedUrl)`. **Doc note:** the SDK only obtains the presigned URL — the consumer PUTs the file
  bytes to `presignedUrl` themselves; the SDK does **not** proxy the binary. *Tests:* body; parse.
- ⬜ **9.1 — `POST uploads/{id}/complete`** (`{id}` = `emailAssetId`) →
  `UploadCompleteResponse(emailAssetId, finalUrl)`. *Tests:* path; parse.

---

## Critical files

**Modify:**
- `src/commonMain/kotlin/com/retro99/loops/sdk/LoopsClient.kt` — extract shared request helper, add
  sub-API `val`s, wire constructors.

**New (per stage), all under `src/commonMain/kotlin/com/retro99/loops/sdk/`:**
- `api/ContactsApi.kt`, `api/EventsApi.kt`, … one file per sub-API (new `api` sub-package).
- `model/Contact.kt`, `model/LoopsValue.kt` (+ serializer), `model/Pagination.kt`,
  `model/Campaign.kt`, etc. — one file per logical model group, matching existing `model/ApiKey.kt`.
- `internal` request-helper file (e.g. `LoopsHttp.kt`) if extracted as a class.

**Tests** under `src/commonTest/kotlin/com/retro99/loops/sdk/`:
- One test file per sub-API (`ContactsApiTest.kt`, …) following `LoopsClientTest.kt`'s MockEngine
  pattern.
- A dedicated serializer test for `LoopsValue` / custom-property flattening (encode → exact JSON).

**Possibly modify:**
- `ksp-processor/src/main/kotlin/.../JvmAsyncProcessor.kt` — only if Stage 0 reveals it doesn't
  handle sub-API classes; expected to work as-is since it scans by annotation.
- `build.gradle.kts` / `gradle/libs.versions.toml` — version bump per stage; no new deps expected
  (everything builds on existing Ktor + kotlinx.serialization).
- `README.md` — document each new sub-API group as it lands.

---

## Verification

### What every stage must test (checklist)

Derived from the Stage 1.1 review — apply per endpoint, scaled to what the endpoint actually does.
Skip a row only when the endpoint has no such surface (e.g. no body on a GET).

**Request side — what we send:**
- [ ] **Method + relative path** (`POST contacts/create` → `/api/v1/contacts/create`).
- [ ] **Real wire body**, not just `sdkJson.encodeToString(...)`. Capture the MockEngine
  `request.body` (`(body as OutgoingContent.ByteArrayContent).bytes().decodeToString()`) and assert
  the exact JSON. *This is the one that bites:* `encodeToString` and Ktor's content-negotiation path
  can diverge (different `Json` config), so a green direct-serialize test can hide a broken wire body.
- [ ] **Custom-property flattening** — known fields + custom props as **top-level siblings** (not
  nested), exact-JSON assertion. And **custom props must not shadow known fields** (a custom `"email"`
  key must not overwrite the typed `email`).
- [ ] **Query params** forwarded for GETs (`email` | `userId`, `perPage`/`cursor`, `list`, …).
- [ ] **Headers**: auth untouched (don't re-assert per stage; covered once); `Idempotency-Key`
  present when passed / absent when null (Stages 3.0, 3.1).
- [ ] **Optional/null fields dropped** from the body (`explicitNulls=false`) — assert a minimal body.

**Response side — what we parse:**
- [ ] **Representative success** parsed into the typed model (every field that matters).
- [ ] **Decode collects unknown top-level keys** back into `customProperties` (round-trip:
  encode→decode→assert equal), and **`ignoreUnknownKeys`** tolerates extra server keys.
- [ ] **`LoopsValue` coverage** in context — String / Number / Boolean / **Null (reset)** / List /
  Object, not just the string+number happy path.

**Error mapping:**
- [ ] **non-2xx → `LoopsException.Api`** with `statusCode` + `body`.
- [ ] **transport failure → `LoopsException.Network`** (at least once per sub-API).
- [ ] **429 → `LoopsException.RateLimit`** with/without `x-ratelimit-*` headers (once Phase 0d lands).

**Cross-target (the mandatory gate):**
- [ ] `./gradlew allTests` green — runs `commonTest` on **JVM + Android + iOS sim**. A stage is not
  ✅ Done until this passes. (`jvmTest` alone is faster for the inner loop but is **not** the gate.)

The numbered items below are the broader per-release checks (compile matrix, async, Swift, live).

Each one-endpoint stage carries the checks below (scaled to that endpoint); the heavier items
(cross-target compile, Swift smoke, live smoke) can be batched per release rather than per stage.

1. **Unit tests (primary)** — `./gradlew allTests` (or `jvmTest`/`testDebugUnitTest`). Each new
   endpoint gets MockEngine tests asserting: correct method + relative path, correct body/query for
   the identifier and custom-property cases, correct header (auth untouched; idempotency key when
   passed), and correct parse of a representative success response. Add at least one
   `LoopsException.Api` (non-2xx) and one `LoopsException.Network` test per sub-API, plus the
   `LoopsException.RateLimit` (429) cases once Phase 0d lands.
2. **Serializer test** — encode a `CreateContactRequest`/`EventRequest` with mixed custom properties
   and assert the exact top-level-flattened JSON string; decode a `find` response with extra keys.
3. **Compile across targets** — `./gradlew compileKotlinJvm compileKotlinIosArm64
   assembleDebug` (Android) to confirm `expect/actual` and SKIE export still build on every target.
4. **JVM async** — confirm the KSP processor generated `${endpoint}Async()` for the new sub-API
   (inspect generated sources under the jvm build dir, or a small JVM test calling `...Async().get()`).
5. **Swift smoke (manual, optional per stage; required before a release covering iOS)** — build the
   XCFramework and call one new endpoint from a Swift snippet to confirm SKIE exposes it as
   `async`/clean enums (sealed `ContactIdentifier`, `LoopsValue`).
6. **Live smoke (optional, manual)** — with a real test API key in `direct` mode, exercise one
   read-only endpoint (`contacts.find`) and one write (`contacts.create`) against
   `app.loops.so` to confirm the real contract matches the models. Never commit the key.

---

## Open questions / risks

Resolved decisions are committed above (KSP/async = Decision 5, Option B, done; `ContactIdentifier`
= Decision 4; generic `Page<T>` = Decision 3; `LoopsValue` shape = Decision 2; custom-property wire
types = Decision 8, **validated live**).

- ✅ **Custom-property value types over the wire (RESOLVED — see Decision 8).** Live-smoke-tested
  against `app.loops.so` on 2026-06-17: Loops accepts numeric custom properties as **JSON numbers**
  (including the scientific-notation form `1.705486871E12` that `NumberValue`'s `Double` backing
  emits — the API parses it to the correct value). No `LoopsValueSerializer` change was needed.
