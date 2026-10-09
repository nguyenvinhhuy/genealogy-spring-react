# CLAUDE.md — Genealogy (Gia phả) Web App

Engineering guide for this repository. Read this before writing code. It defines the locked architecture, the tech
stack (with pinned versions), and the coding conventions.

Conventions are inherited from the sibling project `D:\Me\charity-spring-react` (same author, same stack) — where this
file is silent on a convention, that project's `CLAUDE.md` is the fallback authority. Domain analysis and the reasoning
behind the locked decisions in §3 live in [docs/analysis.md](docs/analysis.md).

---

## 1. What this project is

A genealogy web app for **one Vietnamese clan** (single-clan, private). Members record người trong họ, their
relationships, life events, photos/scans of the old gia phả, and ngày giỗ (lunar death anniversaries); the app renders
the family tree and reminds the family of upcoming giỗ.

- **Not multi-tenant.** One clan, one tree. No tenant isolation anywhere.
- **No payments**, no DNA matching, no external record-search integrations.
- Architecture: **modular monolith**, package-by-feature — same as charity.
- Roles: `ADMIN` (trưởng tộc), `EDITOR` (biên tập chi), `MEMBER` (con cháu), `GUEST` (anonymous; the public read-only
  surface is **off** at MVP).

---

## 2. Tech stack (pinned — verified against npm / Maven Central on 2026-09-14)

### Backend

| Thing | Version | Notes |
|---|---|---|
| Java | **26** | Latest GA (27 not yet released; 25 is the latest LTS). `<release>26</release>` |
| Spring Boot | **4.1.0** | Spring Framework 7.0.x, Jakarta EE 11 |
| Jackson | **3.x** | Managed by Boot. Package is `tools.jackson.*` (NOT `com.fasterxml.jackson.*`) |
| Build | **Maven** | `backend/pom.xml`; no Gradle |
| DB | **PostgreSQL 18** | + `unaccent` and `pg_trgm` extensions (§4.3) |
| JDBC driver | 42.7.13 | Managed by the Boot BOM — do not pin manually |
| Migrations | **Flyway 13.x** | Version managed by Boot. Use the `spring-boot-flyway` module, not bare `flyway-core` |
| Auth | Spring Security + **JJWT 0.13.0** | access 15m / refresh 7d |
| Storage | **Cloudinary 2.4.0** | Behind one `StorageService` interface — see §3.9. MinIO was removed on 2026-10-09 |
| PDF | **iText Core 9.7.1** | Artifact is `com.itextpdf:itext-core` — see the note below |
| Mapping | **MapStruct 1.6.3** | + `lombok-mapstruct-binding` 0.2.0 |
| Boilerplate | **Lombok 1.18.48** | |
| API docs | **springdoc-openapi 3.1.1** | Swagger UI at `/swagger-ui.html` |
| Tests | JUnit 5 + Mockito + **Testcontainers 2.0.5** | `testcontainers-bom` |

> ⚠️ **iText is a major jump from charity.** Charity pins `itext7-core:7.2.5`; the artifact has since been renamed to
> `itext-core` and is now on 9.7.1, with API changes across 7 → 8 → 9. Added at P5 with the book export; the artifact
> is a `<type>pom</type>` aggregate that pulls in `kernel`, `layout`, `io` and the rest.

> ⚠️ **The book must embed a Unicode font, and the fonts are in the repo for that reason.** iText's built-in
> Helvetica is WinAnsi and cannot encode ế, ộ or ữ — which is most of a Vietnamese gia phả — and it fails by
> dropping them, not by throwing. `backend/src/main/resources/fonts/DejaVuSans*.ttf` are loaded with
> `PdfEncodings.IDENTITY_H` and `FORCE_EMBEDDED`. ❌ Do not switch to a font path on the host: the base image's
> font set is not part of this project's contract, and a PDF of empty boxes is a silent failure.

### Frontend

| Thing | Version | Notes |
|---|---|---|
| React / react-dom | **19.3.0** | Functional components only |
| TypeScript | **6.0.3** | Strict mode, no `any`. **Not 7.0.2** — see the note below |
| Build | **Vite 8.3.0** | + `@vitejs/plugin-react` 6.1.1 (both are majors above charity) |
| Styling | **TailwindCSS 4.3.3** + Shadcn/ui | `radix-nova` preset, Radix base. Primitives live in `@/shared/ui` (see `components.json`) |
| Data | **TanStack Query 5.102.8** | |
| Tables | **TanStack Table 9.2.4** | Major above charity's v8 |
| Forms | **React Hook Form 7.88.0** + **Zod 4.6.5** | |
| Auth state | **Zustand 5.0.15** | Access token in memory only |
| HTTP | **Axios 1.20.0** | Single-flight 401→refresh→retry interceptor |
| Routing | **react-router 8.3.1** | |
| i18n | **i18next 26.4.2** / **react-i18next 17.0.14** | `vi` default/fallback, `en` secondary |
| Lint | **ESLint 10.10.0** + **typescript-eslint 8.70.0** | |
| Format | **Prettier 3.9.6** | |
| **Tree drawing** | **d3-hierarchy 3.1.2** + **d3-zoom 3.0.0** | New vs charity — see §3.5 |
| Node (CI + local) | **24 LTS** ("Krypton") | 26.x is current but does not become LTS until Oct 2026 |

> ⚠️ **Why TypeScript 6.0.3 and not 7.0.2.** TypeScript 7.0.2 (the native compiler) is GA, but
> `typescript-eslint@8.70.0` — including its `canary` tag — declares `peerDependencies.typescript: ">=4.8.4 <6.1.0"`,
> so TS 7 drops the entire type-aware lint layer (`no-floating-promises`, `no-unsafe-*`, `consistent-type-imports`, …)
> and leaves only `tsc --noEmit`. TS 6.0.3 is inside that range. Revisit when typescript-eslint ships TS 7 support;
> until then 6.0.3 is the newest version that keeps lint working.

> When adding a dependency, verify the current version against the registry rather than copying charity's `pom.xml` /
> `package.json` — that project is now behind on several of the versions above.

**Naming:** groupId `com.genealogy`, artifactId `genealogy`, base package `com.genealogy`.

---

## 3. Locked architecture decisions (STRICT — do not relitigate per-task)

These are the decisions that are expensive or impossible to reverse once data exists. They are locked. Changing one is
a deliberate, explicit conversation with the user — never a drive-by choice inside an unrelated task. Rationale for
each is in [docs/analysis.md](docs/analysis.md) §4.

### 3.1 Relationship model is GEDCOM-aligned — NOT `person.father_id/mother_id`

```
person          ── one individual
person_name     ── 1..n names per person (BIRTH|HUY|TU|HIEU|THUY|SAINT|ALIAS)
family          ── one couple/union: partner1_id, partner2_id, status, order_index
family_child    ── links a child INTO a family, carrying relation_to_p1 / relation_to_p2
                   (BIRTH|ADOPTED|STEP|FOSTER) + birth_order
```

- ❌ **Never** add a `father_id` / `mother_id` column to `person`, not even "just as a shortcut for the common case".
  It breaks on tái hôn, con nuôi, con riêng, anh em cùng cha khác mẹ, and unknown-one-parent — all of which are normal
  in a Vietnamese gia phả.
- Use `partner1`/`partner2`, **not** `husband`/`wife` — old records frequently lack gender. "Chồng/vợ" is derived at
  the DTO layer from `person.gender`.
- A child with only one known parent still gets a `family` row (the other partner is null). Do not model that as a
  direct person→person edge.

### 3.2 Dates are fuzzy — NEVER a bare `LocalDate`

All genealogical dates use the embeddable `GenealogyDate` (in `common/model/`), persisted as a column group:

```
<prefix>_modifier   ENUM  EXACT|ABOUT|BEFORE|AFTER|BETWEEN|ESTIMATED|CALCULATED
<prefix>_calendar   ENUM  SOLAR|LUNAR
<prefix>_year       INT   nullable      -- year-only is a first-class case
<prefix>_month      INT   nullable
<prefix>_day        INT   nullable
<prefix>_year2/_month2/_day2            -- second endpoint for BETWEEN
<prefix>_sort_date  DATE  derived       -- ORDER BY / comparisons ONLY
<prefix>_raw        TEXT                -- verbatim user input, never discarded
```

- `sort_date` is computed on write by `GenealogyDate.deriveSortDate()`, which services call — the entity never does
  it for itself on persist, so the rule stays somewhere readable and testable. All sorting and comparison uses it.
  The exact rule:

| Modifier | `sort_date` |
|---|---|
| `EXACT`, `ABOUT`, `ESTIMATED`, `CALCULATED` | the date, with a missing month/day filled to the **first** of the period (year-only → 1 Jan) |
| `BEFORE`, `AFTER` | the bound it names — that is the only date actually given |
| `BETWEEN` | the **midpoint** of endpoint 1's start and endpoint 2's end (`1918`–`1922` → 1920-07-01) |
| no year at all | `null` |

- An impossible day (31 February shows up in real records) is **clamped** to the end of its month rather than
  throwing: the row must stay sortable, and `date_raw` preserves what was actually written.
- All **display** uses the modifier + y/m/d parts. Never render `sort_date` to a user — it is a lie about precision.
- ❌ Never fabricate a missing month/day to make a `LocalDate` fit.
- ⚠️ **A lunar date may have a day and month and no year** (2026-09-29, §8.10 D1): "giỗ 12 tháng 3" is the
  commonest record of a thuỷ tổ. It sorts nowhere (`sort_date` null, the row above), bounds nothing, renders
  "12/3 (âm lịch, không rõ năm)", and is reminded every year with no năm thứ. A solar date still needs its year.
- A `BETWEEN`'s second endpoint carries its own leap flag (`date_leap_month2`, `V13`), and a range prints
  "từ … đến …", never "khoảng", which is `ABOUT`'s word.

### 3.3 Lunar calendar — Vietnamese algorithm only

- Vietnamese âm lịch is computed at **UTC+7 from 1 January 1968, and at UTC+8 before it** — the day the North moved
  its calendar; Tết Mậu Thân fell on 29 January in the North and 30 in the South. Chinese is UTC+8 in every year, so
  they differ on some dates after 1967. ❌ **Do not** add `cn.6tail:lunar-java` or any other Chinese-lunar library —
  it will silently produce wrong ngày giỗ. ⚠️ The pre-1968 rule was decided by the user on 2026-09-29 (§8.10 D2);
  until then every year was UTC+7.
- Implement the **Hồ Ngọc Đức** algorithm as a dependency-free class in `common/util/LunarCalendar.java`, with unit
  tests pinned to known solar↔lunar date pairs — Tết on both sides of 1968 and two known leap months, never a pair
  found by asking the code under test. Dates are proleptic Gregorian at every year, like `LocalDate`: a Julian
  branch before 1582 read one calendar's fields as the other's and made a Julian 29 February a 500.
- A lunar year is rendered with its can-chi ("10/3/1950 (âm lịch, năm Canh Dần)"), derived by
  `LunarCalendar.canChi`, never stored and never parsed from input.
- Leap months (tháng nhuận) and day-30 in a 29-day month are explicit cases, not afterthoughts. Convention for day 30
  in a short month: fall back to day 29.
- ⚠️ **The leap flag is stored** (`events.date_leap_month`, `V9`, 2026-09-24). Until then no layer had one, so a giỗ
  in a tháng nhuận was filed and reminded a month early. It is carried by `GenealogyDate`, the request and response,
  `GenealogyDateText` ("10/4 nhuận/2020"), the GEDCOM phrase and the fuzzy input ("10/4 nhuận/2020"). A leap flag on
  a year with no such leap month falls back to the ordinary month; on a solar date it is refused. Rows written
  before `V9` had their flag restored from `date_raw` by `V14`, a Java migration.
- A giỗ of a death in a tháng nhuận is kept in the **ordinary** month of that number, even in a year that repeats
  it — the custom, pinned by `LunarCalendarTest.leapMonthGioIsInTheOrdinaryMonth`.
- Giỗ is stored as a **lunar** date; the next solar occurrence is computed for reminders, never stored as the source of
  truth.

### 3.4 Derived fields

| Field | Rule |
|---|---|
| `person.generation` (đời) | Denormalized column; the **whole graph** is recomputed inside the same transaction by `family/service/impl/GenerationCalculator` whenever a union or a parent/child link is created, changed or removed — never patched incrementally, and never hand-edited. Each connected family is counted from its **founder**: the parentless root with the most men in its male line, then the most descendants, then the lowest id. The founder and their descendants (the clan) sit one below their **deepest** clan parent. Everyone else takes their đời from a clan member, breadth first: a spouse at their partner's đời (because "bà X, đời 5" means the wife of someone at đời 5), a child one below, a parent one above but **never above đời 1**. A person in no union, or on an ancestry cycle, has no đời. ⚠️ **Only a `BIRTH` link is blood** (decided 2026-10-08, §8.12 D1): the clan, the founder and the male line follow birth links alone, as kinship (§5.3) and quality (§5.1) do. A con nuôi, con riêng or con nuôi dưỡng is placed one below their adoptive or step parent like a spouse is placed beside a partner, is never a founder, and their own children follow them. |
| `person.living` | **No DEATH, BURIAL or REBURIAL recorded, no recorded grave (`GRAVE` kind — a `LIVING_PLOT` counts for nothing), and some recorded birth could fall within the last 100 years** — each birth read as its latest possible day (§5.1), so "sau 1900", a missing year or a second recorded birth keep the person living. Fails closed: nothing recorded is living. Recomputed on every event write, for the previous subject too when an event moves, and **swept at startup and nightly** (`LivingRefresh`, 03:40 Vietnam time), because the 100-year line moves with the calendar. Drives §3.6. |
| nội/ngoại | Computed at render time relative to the focus person. Never stored. |
| relationship term (xưng hô) | Computed per request via LCA. Never stored. |

### 3.5 Tree rendering — focus person + bounded depth, always

A gia phả is a **DAG**, not a tree (marriages add horizontal edges; remarriage adds branches; cousin marriages create
undirected cycles).

- ❌ Never build an endpoint that returns the whole tree. The tree API takes a **focus person id + depth** and returns
  a **BFS subgraph**.
- ❌ Do not adopt a packaged "family tree" component library — none of the common ones model multiple unions per person
  correctly, and forking one costs more than drawing it. Use `d3-hierarchy` for layout + `d3-zoom` for pan/zoom over
  plain SVG.
- Chart types: descendant (couples collapsed into one node), pedigree, hourglass, fan. Descendant + pedigree are the P2
  baseline.

### 3.6 Living-person privacy is enforced in the SERVICE layer

- A person who is `living` (§3.4) has their details redacted for roles below `EDITOR`.
- Enforcement lives in the **service**, returning a different DTO (`PersonRedactedResponse` vs `PersonDetailResponse`)
  — ❌ **not** via a MapStruct `@AfterMapping` null-out and ❌ **not** via `@JsonView`. Both hide the rule where it
  cannot be read or tested, and a leak there is silent.
- Every endpoint returning person data has a test asserting the redacted shape for a `MEMBER` caller.
- **`PersonService.isLiving(Long)` is the one guard every feature calls**, and it is the only place the
  fail-closed-on-unknown-id rule lives. ❌ Never re-inline a living check into a feature: private `isLiving`
  copies in `event` and `media` are exactly how five endpoints ended up unguarded for four phases (§8, F9).
- The rule is **not** about people only. A fact that names a living person is theirs wherever it is stored: a
  union's ngày cưới, a citation quote, a sinh phần, a wedding photo on the union. A guard keyed on
  `targetType == PERSON` looks complete and is not.
- **A union's status and a child's relation type are structure, not details** (decided 2026-09-23). A MEMBER may
  see that a living couple divorced or that a living child is a con nuôi or con riêng, exactly as they see who is
  whose child. Hiding them was considered and rejected: the kinship endpoint answers "không có quan hệ huyết thống"
  for a step link, so the fact leaks by elimination unless kinship also refuses — a redaction that half-works is
  worse than a decision that is written down. `TreeServiceImplTest` pins every field the tree hands a MEMBER, so a
  date or a note cannot join them unnoticed.
- Client-side hiding is **not** enforcement. `{!hidden && <Card/>}` in React is defence in depth on top of the
  service rule, never instead of it — the API is reachable without the UI.

### 3.7 Places are structured, not free text

`place` is hierarchical (`WARD → DISTRICT → PROVINCE → COUNTRY`) with `parent_id` and optional lat/lng. Vietnamese địa
danh are renamed and merged constantly; a free-text column cannot be aggregated or mapped later.

### 3.8 Every write is auditable

A `revisions` table records who changed which entity, when, the before/after payload, and a free-text
`change_note` — the "on what basis". Genealogical facts get disputed; "who changed cụ's ngày mất, and why"
must be answerable years later.

- `AuditService.record(...)` **joins the caller's transaction**. ❌ Never `REQUIRES_NEW`: with it, a rolled-back
  edit still left a revision behind and the trail claimed a change that never happened — worse than no trail.
  A test pins the propagation so this cannot be undone by accident.
- **Every write method takes an `actorId` and records one.** `person`, `family` and `event` all do. Until
  2026-09-18 only `person` did, so a ngày cưới could be changed from the UI with nothing recording who (§8.3).
  ❌ Do not add a write path to a feature that owns auditable rows without threading the acting member to it.
- A payload that fails to serialise is written as an error marker, never as `null`: a row with both payloads
  null trips `ck_revisions_has_payload` and would roll the caller's own edit back over a formatting problem.
- The trail outlives its subject — deleting a person keeps their revisions, including the last known state.

### 3.9 Storage is Cloudinary, behind one narrow interface

⚠️ **Changed 2026-10-09 at the user's explicit decision** (charity already did the same): MinIO, `MinioStorageService`,
the `STORAGE_PROVIDER` switch and the `io.minio` / `okhttp-jvm` dependencies are gone. Development and production both
use **Cloudinary**, so a developer needs the three `CLOUDINARY_*` values in `.env` to upload a photo; without them the
rest of the gia phả runs and an upload answers 503. Family photos therefore leave the machine even in development —
use a throwaway Cloudinary account, not the clan's, for tests. §8 keeps the history of the MinIO years as it was written.

Photos are kept for Cloudinary's on-the-fly image transforms: this app is photo-heavy (portraits plus A3 scans of the old
gia phả) and hand-rolling a thumbnailer is real work.

- `media/service/StorageService` stays narrow: `upload`, `delete`, `resolveUrl` and `download`, nothing more. ❌ Do not
  leak Cloudinary types (transformation strings, `Uploader`) through it — those stay inside the implementation.
- ⚠️ **`download` exists because the book embeds portraits in a PDF and iText needs the bytes.** Cloudinary fetches its own
  CDN URL, which needs no signing, and returns `Optional.empty()` on failure and logs — a portrait that will not load
  must not stop the whole book.
- ⚠️ **A size is a parameter, not a new method.** `upload` takes an `InputStreamSource` and returns a `StoredFile`;
  `resolveUrl` and `download` take an `ImageSize` (`THUMBNAIL` | `ORIGINAL`). Only the size crosses the interface.
- ⚠️ **The type of a stored file is read from its bytes** (`FileTypes`), never from the upload's header or name, and a
  storage fault is a 503 with a fixed Vietnamese message: the SDK's own text named the internal host.
- `CloudinaryStorageService` is the one `StorageService` bean. `CloudinaryStorageServiceIT` runs against real Cloudinary
  and is skipped via `@EnabledIfEnvironmentVariable` when credentials are absent.
- A Cloudinary URL is **permanent and unsigned**, so §3.6 is applied before one is ever handed out:
  `MediaService.findByTarget` returns nothing for a living person to a caller below EDITOR.

---

## 4. Backend conventions

Package base `com.genealogy`. **Package-by-feature**; each feature is self-contained and references other features
**by id only** (no cross-feature JPA relations, no cross-feature entity imports).

```
com.genealogy/
├── common/                 # shared kernel (no feature logic)
│   ├── config/             # @ConfigurationProperties, beans (OpenAPI, storage)
│   ├── security/           # SecurityConfig, JWT provider/filter, AuthPrincipal
│   ├── exception/          # ApiException hierarchy + GlobalExceptionHandler
│   ├── model/              # GenealogyDate, shared discriminator enums (§4.1)
│   └── util/               # LunarCalendar, VietnameseName, SlugUtil
├── person/                 # person, person_name, gender, generation
├── family/                 # family, family_child, union status
├── event/                  # polymorphic events (PERSON|FAMILY subjects) + giỗ
├── place/                  # hierarchical places
├── branch/                 # chi / phái / nhánh
├── media/                  # photos & document scans; StorageService + impls (§3.9)
├── source/                 # sources & citations
├── tree/                   # subgraph API + relationship calculator
├── quality/                # consistency checks + duplicate detection
├── audit/                  # the revision trail (§3.8)
├── suggestion/             # member-offered edits awaiting review (F17)
├── gedcom/                 # GEDCOM 5.5.1 import / 7.0 export (F13)
├── book/                   # the printable gia phả PDF (F14)
├── merge/                  # folds a duplicate person into another (F11) — see the note below
├── search/                 # advanced person search across features (F20) — same note applies
├── auth/ member/           # app accounts — lifted near-verbatim from charity
└── GenealogyApplication.java
```

**Rules**

- A feature package must not import another feature's `domain`, `repository`, or `service/impl`. Cross-feature needs go
  through the other feature's `service` interface, passing/returning ids or DTOs.
- `common` never depends on a feature. Discriminator enums that several features must reference by design
  (`EventSubjectType`, `MediaTargetType`, `RelationType`, `DateModifier`, `CalendarType`) live in `common/model/`, not
  in the "owning" feature.
- **`tree`, `quality`, `gedcom` and `book` are read-across features.** They traverse the whole graph, so the naive
  "call `PersonService.findById` per node" is an N+1 across thousands of rows. They read through **purpose-built
  projections** exposed by `family`/`person`/`event`/`source` (`findNodes`, `findUnionsBy*`, `findAllFacts`, and the
  whole-clan `findAllDetails`/`findAllUnions`/`findAll`), each fetched in one query. Those projections are part of the
  service interface — they are **not** a license to import the entities.
- **`merge` and `search` exist as their own packages for a mechanical reason.** `event`, `family` and `grave` already
  depend on `person`, so a `PersonService` method that called them back would close a Spring bean cycle. Both
  features depend on the others and nothing depends on them. Each feature still owns **its own** rows and answers
  through a narrow method on its own service — `merge` only decides the order of a merge, and `search` only decides
  which id sets to intersect. ❌ Do not "simplify" either back onto `person`: it compiles right up until Spring
  refuses to start.

Each feature follows `domain/ dto/{request,response}/ repository/ mapper/ service/ service/impl/ controller/`, with a
`package-info.java` per package.

### 4.1 Java / Spring conventions

Identical to charity — the short version:

- **DTOs are `record`s.** No Lombok on records.
- **Entities**: `@Getter @Setter @NoArgsConstructor`. ❌ Never `@Data` on an entity.
- **DI**: `@RequiredArgsConstructor` + `private final`. **Logging**: `@Slf4j`.
- **MapStruct** for all entity↔DTO mapping — `@Mapper(componentModel = "spring",
  unmappedTargetPolicy = ReportingPolicy.IGNORE)`. No hand-written mapping.
- **Services**: interface in `service/`, `@Service` impl in `service/impl/`. `@Transactional(readOnly = true)` at class
  level, `@Transactional` on writes. Business rules live here, not in controllers.
- **Controllers**: thin, `/api/v1/...`, `@Valid` inbound, `@Operation` on every handler. Return the DTO **directly** —
  ❌ no success envelope. `201` create, `204` delete. Paginated: service returns `Page<T>`, controller wraps in
  `new PagedModel<>(page)`.
- **Errors**: throw a `common/exception/ApiException` subclass; `GlobalExceptionHandler` renders RFC 9457
  `ProblemDetail`. ❌ Never construct `ProblemDetail` in a service/controller (sole exception: security's
  `AuthenticationEntryPoint`/`AccessDeniedHandler`).
- **Migrations**: Flyway in `src/main/resources/db/migration/`, `ddl-auto=validate`. ❌ **Never edit an applied
  migration** — add `V{n}__*.sql`.
- **Comments (STRICT — this applies to `.ts` and `.tsx` exactly as it does to `.java`)**: every method, function,
  component, type and record gets **one sentence of WHAT it is, and no why** (≤120 cols, unwrapped), then one blank
  doc line, then `@param`/`@return`/`@returns`. WHY goes in a `//` comment **outside the doc block** — inside the body
  for a function, directly above the declaration for a type — and is **exactly one line, never wrapped across two or
  more `//` lines**, only for genuinely non-obvious logic. ❌ **No second paragraph inside a doc comment, ever**, and
  ❌ **no `<p>`** — Javadoc does not need it here and TSDoc is markdown, where it means nothing. ❌ Don't document
  Lombok accessors, and don't restate the obvious.
- **Tests**: JUnit 5 + Mockito; mock repositories but use the **real** MapStruct mapper (`Mappers.getMapper(...)`).
  Unit tests must not need a DB.

### 4.2 Domain logic that MUST have unit tests

Not optional — these are the parts where a silent wrong answer is worse than a crash, and where a regression is
invisible in a build:

- `LunarCalendar` solar↔lunar conversion, incl. leap months (§3.3)
- `GenealogyDate` → `sort_date` derivation for every modifier (§3.2)
- `generation` recomputation after a parent/child link change (§3.4)
- Relationship term resolution (LCA → Vietnamese xưng hô)
- Living-person redaction per role (§3.6)
- Consistency-check rules (§5.1)

### 4.3 Search

Enable `unaccent` + `pg_trgm` in the first migration and index names with a trigram index. "Nguyen Van An" must match
"Nguyễn Văn An". ❌ No Elasticsearch — the dataset is a few thousand rows at most.

⚠️ `unaccent()` ships as **STABLE**, and Postgres rejects it in an index expression outright. `V3` defines an
`immutable_unaccent(text)` wrapper around the two-argument form (which is genuinely immutable) — index and query
through **that**, never `unaccent()` directly, or the index is silently bypassed.

---

## 5. Domain rules

### 5.1 Consistency checks (feature `quality`)

Rules produce **warnings, never hard validation failures** — real gia phả data is messy and a blocking validator makes
the app unusable. Each rule has a severity and is listed on a data-quality page.

- Death before birth; burial before death
- Born after mother's death, or after father's death + 10 months
- Mother younger than 12 or older than 55 at a child's birth
- Marriage before age 13
- Lifespan > 110 years
- **Person is their own ancestor** (cycle) — this one is an `ERROR`, and the write that would create it is rejected
- Child born more than 9 months before the parents' marriage (info only — common, and not an error)
- Two recorded births, or two deaths, that cannot both be true — a merge keeps both on purpose (§8 F11)
- Born before a birth parent was born

How the rules read the data (2026-09-23, §8.4):

- **A rule fires only when the data is wrong whichever way its vague parts are read.** Each date is the span it
  allows (`GenealogyDate.earliestPossible`/`latestPossible`), not its `sort_date`: "1945" is the whole of 1945,
  `BEFORE 1900` ends on 31 Dec 1899, `AFTER` has no upper bound, and `ABOUT`/`ESTIMATED`/`CALCULATED` are widened
  by five years. ❌ Do not compare `sort_date` in a rule — it turned a burial "1945" into 1 January and flagged it.
- **Only birth links are checked as parentage.** A con riêng, con nuôi or con nuôi dưỡng says nothing about when a
  step, adoptive or foster parent could have had a child.
- **An unrecorded sex is never read as male.** Such a parent gets only the father's looser posthumous bound, which
  holds for either parent, under its own code, and none of the mother-age rules.

### 5.2 Vietnamese naming

- `person_name` splits `surname` / `middle` / `given`; display order is surname-first (`Nguyễn Văn An`), and the app
  never reorders to Western order.
- One name per person is `is_primary`; all others are alternates (§3.1).
- Sorting people by name sorts by `given` name, then `middle`, then `surname` — Vietnamese convention, not by surname.

### 5.3 Kinship terms (feature `tree`)

Vietnamese kinship words vary by region. This project uses the **Northern** convention, the one gia phả are
conventionally written in:

| Relation | Term |
|---|---|
| Father's **elder** sibling, either gender | bác |
| Father's younger brother | chú |
| Father's younger sister | cô |
| Mother's **elder** sibling, either gender | bác |
| Mother's younger brother | cậu |
| Mother's younger sister | dì |

- Telling bác from chú needs birth order — that is what `family_children.birth_order` is for (§3.1).
- ❌ Where birth order is unrecorded, **answer "bác/chú/cô", never guess**. Calling a bác "chú" is a real rudeness,
  and a plausible-looking wrong word is worse than an honest ambiguous one.
- In-laws are out of scope: only blood paths and a direct spouse get a term. **A blood path is a `BIRTH` link**: a
  stepfather is not "cha", and two con riêng of one union are not "anh em ruột".
- **Seniority between cousins follows the branch, not their ages** (decided 2026-09-23): con nhà bác is anh or chị
  to con nhà chú, and a parent's cousin is bác họ or chú họ by the same comparison of the common ancestor's
  children. Unrecorded or equal birth order there gives the ambiguous word, exactly as for bác/chú.
- ❌ **An unrecorded sex is never read as male**: "cha/mẹ", "anh/chị", "vợ/chồng", "chú/cậu". When the linking
  parent's sex is unrecorded, or both parents lead to the ancestor (a cousin marriage), `side` is null rather than
  "bên nội".

---

## 6. Frontend conventions

**Feature-sliced from day one** — this project has no legacy flat layout to migrate, so there is no excuse for one:

```
frontend/src/
├── features/<name>/        # person, family, tree, event, media, place, branch,
│   ├── api.ts              # source, quality, auth, member
│   ├── types.ts
│   ├── components/
│   ├── pages/
│   └── hooks.ts            # only if the feature needs its own
├── shared/{ui,components,layouts,hooks,lib,config,store,types,i18n}/
├── router/
└── App.tsx / main.tsx
```

- **Boundary rule**: a feature must not reach into another feature's internals. Its public surface is `api.ts`,
  `types.ts` and `components/` — a page may render another feature's dialog or card (`MergeDialog` on the quality
  page, `AddRelationDialog` on the tree), which is how the add-relation flow reaches the chart (§6.1). `lib/`,
  `hooks.ts` and `pages/` are internal. Written down 2026-09-23, when the old wording ("`api` functions only") was
  found broken by four features; moving those dialogs to `shared` would have taken them away from the feature whose
  rules they carry. ❌ Never add a new flat file to a top-level `types/`, `api/`, or `components/`.
- **Strict TS, no `any`.** `interface` for object shapes, `type` for unions. Response types mirror backend JSON exactly
  (camelCase).
- Axios instance: `baseURL: '/api/v1'`, `withCredentials: true`, single-flight 401→`/auth/refresh`→retry. Access token
  in memory (Zustand), **never** localStorage.
- Every function in a feature's `api.ts` has a JSDoc comment.
- Errors surface `ProblemDetail.detail` via toast. No magic numbers.
- **i18n mandatory app-wide**, including admin pages. All user-facing text — labels, toasts, placeholders, empty
  states, validation messages — goes through `t()`, backed by `shared/i18n/locales/{vi,en}.json`. `vi` is
  default/fallback. Enum label maps are **functions calling `t()`**, not static `Record<Enum, string>`. Zod schemas
  needing translated messages are built by `buildXxxSchema(t)` invoked via `useMemo(() => buildXxxSchema(t), [t])`.

### 6.0 App shell and theme (2026-10-01)

The UI is built on **shadcnstore/shadcn-dashboard-landing-template** (MIT), the template charity was cut down from —
read from the template itself, not from charity, which dropped most of it. Primitives are the **radix-nova**
registry versions (`ui.shadcn.com/r/styles/radix-nova/*.json`), written into `shared/ui` by hand.

- **Every signed-in page renders inside `shared/layouts/AppLayout`**: an inset sidebar that folds to icons
  (`app-sidebar`, groups in `nav-items.ts`, filtered by `usePermissions`), a header with the breadcrumb, language and
  light/dark (`site-header`), and the account menu with "Đổi mật khẩu" and "Đăng xuất" (`nav-user`). The router has
  one `RequireAuth` around the layout, not one per page, and each route declares its breadcrumb in `handle.crumbs`.
  `shared/layouts` is the one part of `shared` that may use features' public surface (the pending-suggestion count,
  the password dialog): it is the app's composition root, and everything else in `shared` imports no feature (§8.11
  #50). Every page, sign-in and home included, is a lazy route.
- **The session lives in `shared/lib/session` and `shared/lib/query-client`** (2026-10-02, §8.11). One
  `refreshSession`, under a Web Lock so two tabs never spend one cookie, stores the member as well as the token; the
  401 interceptor skips `/auth/*` and signs out only when the refresh itself is refused; and the query cache is
  cleared whenever the viewer or their role changes. Writes that reach across the clan call `invalidateClanData()`.
- **"Chữ lớn hơn"** (`shared/lib/text-size`) scales the root font size, remembered in `localStorage`; the inline
  script in `index.html` applies it and the theme before the first paint.
- **A page starts with `PageContainer` + `PageHeader`** (`shared/components/page-header`): `narrow` for one record or
  a form, `wide` for tables. ❌ No page draws its own `<main>`, centred column or "Quay lại" button — the sidebar and
  breadcrumb are the way back.
- **A list is a `TableCard`** (toolbar, table, footer) with `TableMessage` for loading, empty and failed, `Pager` for
  pages and `SearchInput` for the search box. Card-header "add" buttons are `variant="outline" size="sm"`.
- **Theme gia phả** (`index.css`): giấy ngà, mực nâu, đỏ son (`primary`), vàng đồng (`bronze`); dark is nâu trầm.
  Headings are **Noto Serif** (`--font-heading`, `@fontsource-variable/noto-serif`), body Geist; both carry
  Vietnamese. `seal`/`seal-foreground` is the con dấu red with ivory on it in both modes — ❌ never put
  `primary-foreground` on `bg-seal`: in dark it is a dark ink, and the seal went black.
- **The theme provider is hand-written** (`shared/components/theme-provider`, `shared/lib/theme`), not next-themes,
  whose inline script React 19 reports as an error on every load; the switch keeps the template's circular reveal.
  Theme and language are remembered in `localStorage` (viewer conveniences, wrapped in try/catch).
- **Playwright on this machine** needs `--disable-gpu --disable-dev-shm-usage`: with little free memory Chromium
  fails requests with `ERR_INSUFFICIENT_RESOURCES`. From Git Bash, run it with `MSYS_NO_PATHCONV=1`, or a `/` argument
  becomes `C:/Program Files/Git/`.

### 6.1 Data-entry UX is a first-class requirement

The single biggest risk to this project is that entering people is tedious and the tree dies at 30 người
([docs/analysis.md](docs/analysis.md) §8). Therefore:

- The "add a child / add a spouse" flow must be reachable **from the tree node itself**, not only from a separate admin
  table.
- Every field except a primary name is optional. A person with nothing but a name is a valid save.
- The date input accepts fuzzy input directly (`1890`, `khoảng 1890`, `trước 1900`) and parses it into §3.2 — it is
  **not** a date picker requiring a full date.

### 6.2 UI verification (STRICT — no exceptions)

- **"It renders and the click works" is NOT "verified."** DOM checks (`getComputedStyle`, `read_page`, text assertions)
  prove presence, not that it looks right. Any change affecting visual layout MUST be checked against a real rendered
  **screenshot** before being reported done.
- If the Browser tool's screenshot capability fails, fall back to Playwright (`npx --yes playwright@1.61.1`) with a
  throwaway `.mjs` script in the scratchpad directory, then read the PNG.
- Wait **~500ms after a dialog opens** before screenshotting — Radix's open animation makes a mid-transition capture
  look broken when it is fine.
- Look critically at the screenshot: do same-purpose rows share identical alignment/spacing/border treatment across the
  whole component?

### 6.3 Component patterns

- ❌ **Never** a generic "..." (`MoreHorizontal`) overflow menu for row actions. Every action gets its own specific
  icon button. A dropdown from an icon is fine only when the icon itself means "pick one of several" (e.g.
  `ArrowRightLeft` for a status change).
- Consolidate edits to different fields of the **same** record into one dialog.
- Within one dialog, every field row uses the **same** layout primitive — no mixing `items-start` and `items-center`
  across rows.
- **Every icon-only control** (`Button`, `Switch`, `Toggle`, …) gets an `aria-label` **and** a shared `Tooltip` (never
  HTML `title=`), added at creation time, not in a later audit pass.
- ❌ Never make a `data-state`-styled component (`Switch`/`Toggle`/`Tabs`/`Accordion`) the **direct** child of
  `TooltipTrigger asChild` — the wrapper's own `data-state` silently overwrites it and breaks its colors. Wrap it in a
  plain `<span className="inline-flex">` first.
- **Pointer cursor is handled globally, not per component.** `shadcn init --pointer` put
  `button:not(:disabled), [role="button"]:not(:disabled) { cursor: pointer }` in `src/index.css`'s base layer, so
  every `<button>` and `[role="button"]` is covered without a class. ❌ Do not add `cursor-pointer` to a primitive's
  className — it is redundant, and charity's per-component rule (which this replaces) existed only because it had no
  global rule. **The gap that remains**: clickables that are neither — `DropdownMenu`/`Select`/`Command` items render
  as `[role="menuitem"]`/`[role="option"]` on a `div`. Those still need `cursor-pointer` explicitly; check any new
  primitive of that shape.

---

## 7. Build & run

```bash
docker compose up -d
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

> ⚠️ **MANDATORY: run all `docker build` / `docker run -v` commands via PowerShell, never Bash.**

> ⚠️ PowerShell `${PWD}` can silently point at the wrong directory if the session cwd drifted — always use an explicit
> absolute path for docker volume mounts.

> ⚠️ Never chain multi-command bash strings through PowerShell → `docker run` → `bash -c "..."`. Escaping breaks
> silently and the command can report exit 0 having done nothing. Write a script file and run that instead.

> ⚠️ **`GenealogyApplicationTests` runs in CI and only there.** It is a Testcontainers context load, so it needs a
> Docker daemon — and CI gives it one: `actions/setup-java` puts JDK 26 on the runner itself and `mvn -B clean
> verify` excludes nothing, so the test really does run on every push.
>
> It fails locally only because this repo's local workflow runs Maven *inside* a `maven:…` container, which makes
> the postgres container Testcontainers starts a **sibling**: its port is published to the host, not to the Maven
> container. Mounting the Docker socket does not fix that, and it was tried properly on 2026-09-15 so nobody need
> try again — Ryuk is unreachable from a sibling (`TESTCONTAINERS_RYUK_DISABLED` gets past that), postgres then
> starts fine, and the JDBC connect is still refused because `--network host` on Docker Desktop for Windows does
> not reach a published port; `TESTCONTAINERS_HOST_OVERRIDE` does not help either.
>
> So locally, exclude it (`mvn -Dtest='!GenealogyApplicationTests' test`) — and do not read that as missing
> coverage. To run it on this machine you would install JDK 26 and Maven on Windows (the host has JDK 21 in PATH
> and JAVA_HOME pointing at JDK 8) and run Maven there; Docker is still required either way.

> ⚠️ Keep `eclipse-temurin:26-jdk` cached (it is reused constantly), but remove any other one-off image created for a
> single test/inspection and run `docker builder prune -f` after a verification pass. Diff `docker images` against
> `docker-compose.yml` before reporting what is leftover.

> ⚠️ Delete any stray `*;C` junk folder left in the repo root after a `docker build`.

---

## 8. Roadmap position

Phases are defined in [docs/analysis.md](docs/analysis.md) §7.

**P0 — done and verified** (2026-09-14). Frontend: install, type-check, lint, build, rendered screenshot.
Backend: image builds, Flyway applies V1+V2 against PostgreSQL 18.6, `ddl-auto=validate` passes, the app boots,
and the auth surface answers correctly across ten checks (login, `/members/me`, 401/403/409/400, refresh
rotation, replay revocation) — except that **the replay-revocation check passed against a revocation that was
rolled back**, which nobody noticed until 2026-09-17 (§8.2).

**P1 backend — done and verified** (2026-09-14). `V3` adds branches, places, persons, person_names, families,
family_children and events; the `branch`, `place`, `person`, `family` and `event` features are complete. 24 unit
tests and 21 end-to-end checks pass, including Vietnamese text round-tripping, đời numbering across three
generations, accent-insensitive search, ancestry-cycle rejection and the living-person flag.

**P1 frontend — done and verified** (2026-09-14): person list with accent-insensitive search, person create/edit,
person detail with relations and events, and an add-spouse/add-child dialog reachable from the person itself
(§6.1). The date field takes `1890`, `khoảng 1890`, `trước 1900`, `15/3/1890` or `1918-1922` and keeps the raw
text when it cannot parse. Type-check, lint and build are clean; every screen was screenshot-checked against the
live API.

> ⚠️ **Corrected 2026-09-23: there was no person edit page, and the date field was rendered nowhere.** No screen
> could edit a person, add or change an event, edit or delete a union, or unlink a child, so a family could not
> enter a single date from the app. The review of §8.6 found it; building those screens is its tier 2.

**P2 — descendant and pedigree charts done and verified** (2026-09-14). `GET /api/v1/tree` takes a focus person,
a direction and a depth and returns a BFS subgraph — there is no whole-tree endpoint, and depth is clamped to 8
server-side. The walk expands one projection call per layer, never one per person. The frontend draws it as plain
SVG laid out by `d3-hierarchy` and panned with `d3-zoom`: couples collapse into one box, the deceased carry †,
clicking a name re-centres the chart, and a truncated walk says so instead of implying the family ends there.
31 backend unit tests pass.

All four chart types from §3.5 now exist: **descendant, pedigree, hourglass and fan**. The hourglass lays each
half out separately and mirrors one, because d3's tree only knows one direction; the fan is a polar pedigree
where parents split their child's arc evenly rather than each taking a fixed half, so a person with one
recorded parent fills the ring instead of leaving a gap that reads as missing data.

**P3 — âm lịch and ngày giỗ done and verified** (2026-09-14). `common/util/LunarCalendar` is a dependency-free
port of Hồ Ngọc Đức's algorithm at UTC+7, with `LunarDate`, `toLunar`, `toSolar` and `nextAnniversary`. 13 tests
cover six known Tết dates (2021–2026), a day-by-day round trip across twenty years, leap-month handling, the
day-30 fallback, and an assertion that UTC+8 genuinely disagrees — the test that makes the "no Chinese lunar
library" rule falsifiable rather than folklore. `GET /api/v1/events/anniversaries` lists upcoming giỗ with both
calendars and the năm thứ; the UI renders them soonest-first. 44 backend unit tests pass.

**Mộ phần and the kinship calculator are done and verified** (2026-09-14). `V4` adds `graves`, kept separate from
the BURIAL event because cải táng moves a grave without changing when the burial happened — rewriting the event
to track that would falsify the record. `GET /api/v1/tree/kinship` resolves the Vietnamese term via the lowest
common ancestor; the **Northern** convention is used (father's elder sibling → bác, younger brother → chú,
younger sister → cô; mother's elder sibling → bác, younger brother → cậu, younger sister → dì), and where
`birth_order` is unrecorded it answers "bác/chú/cô" rather than guessing, because calling a bác "chú" is a real
rudeness. 56 backend unit tests pass.

**P4 — consistency checks, duplicate detection and the audit trail done and verified** (2026-09-15). `V5` adds
`revisions`; person create/update/delete write to it with the acting member and a `changeNote`. The `quality`
feature implements every §5.1 rule as a warning, plus accent-insensitive duplicate detection keyed on name and
birth year. 77 backend unit tests and 22 end-to-end checks pass.

**P4 sources and UI done and verified** (2026-09-15). `V6` adds `sources` and `citations`; a citation hangs off any
target by `(type, id)`, and the uniqueness constraint uses `UNIQUE NULLS NOT DISTINCT` so a second citation of the
same source with no locator is rejected rather than silently duplicated — plain `UNIQUE` treats two NULLs as
different and would let it through. The frontend adds `/quality`, and a citation card plus a change-history card on
the person page. Every screen was screenshot-checked against the live API; the revision entries are separated by a
rule because entries are multi-line and plain spacing let one entry's note read as belonging to the entry above.

**P5 — done** (2026-09-15), **and one half of F17 was broken the whole time** (found 2026-09-17).
`GET /api/v1/gedcom/export` writes GEDCOM 7.0 and `POST /api/v1/gedcom/import` (ADMIN only) reads 5.5.1 or
7.0; `GET /api/v1/book` renders the printable PDF; `V7` adds `suggestions`, where a MEMBER proposes and an
ADMIN or EDITOR approves.

> ⚠️ **`POST /api/v1/suggestions` with `kind: CREATE` returned 500 from the day it was written until
> 2026-09-17.** A CREATE suggestion carries no `targetId` until it is approved, so `personNames(...)` collected
> no ids and returned `Map.of()` — and `Map.of()` throws on `get(null)`. Every "đề xuất thêm một cụ nữa" a
> con cháu tried to file died with an NPE. The 48 end-to-end checks this phase was signed off with exercised only
> `UPDATE` and `NOTE`, so nothing caught it, and no unit test passed a null `targetId` either. The fix is a
> `nameOf(map, id)` helper that returns null for a null id; `createdBy` and `reviewedBy` are both
> `ON DELETE SET NULL` and the latter is null for everything pending, so all three lookups go through it.
> The lesson is in §9: an end-to-end pass that never exercises an enum's other arm is not coverage of that arm.

> ⚠️ **The suggestion queue is scoped in the service, not blocked at the matcher** (2026-09-17). Until then any
> signed-in caller could read every pending suggestion, and a payload is a whole `PersonRequest` — notes, chi
> and every alternate name about a living person. Raising the GET to EDITOR+ would have been one line, but it
> would also stop a con cháu seeing what became of their own đề xuất, which is most of what F17 is for. So
> `search`, `getById` and `countPending` take the caller's role and id, and below EDITOR the query narrows to
> `createdBy = caller`; `getById` answers "không có đề xuất" rather than 403, because a refusal would confirm
> that somebody proposed something about that person.

Three decisions in P5 are worth not relitigating:

- **A lunar date is exported as the Gregorian day it fell on, with the lunar original in a `DATE PHRASE`.**
  GEDCOM 7 knows only Gregorian, Julian, Hebrew and French Republican; writing lunar parts as if they were
  Gregorian moves a ngày giỗ by weeks in every other program. Import reads the phrase back, so our own round
  trip is lossless, while a foreign file is honestly read as solar because it carries no lunar information.
- **`HUSB`/`WIFE` are filled from each partner's recorded sex, not from `partner1`/`partner2`.** The model has
  no husband and wife (§3.1); filling the slots by position labels a wife recorded first as the husband.
- **Import adds, it never merges.** Silently merging two people who share a name is worse than a duplicate,
  so the import says what it created and the `quality` page (§5.1) is where duplicates get judged. The UI says
  this next to the import counts rather than leaving a family to discover it.

**All five phases in [docs/analysis.md](docs/analysis.md) §7 are done. There is no P6 — the plan ends at P5.**
Four features scheduled inside P1–P4 were skipped as those phases were built and finished afterwards. The table
is kept as a record of how the gap happened, not as an open list:

| Feature | Was scheduled in | State |
|---|---|---|
| **F9** — living-person privacy | P1 | Built 2026-09-15; **five more leaks of the same rule closed 2026-09-17**. See below. |
| **F11** — duplicate merge | P4 | Built 2026-09-15; **two silent data-loss bugs fixed 2026-09-17**. See below. |
| **F3** — ảnh & scans | P2 | Built 2026-09-15; media redaction extended to FAMILY and GRAVE 2026-09-17. See below. |
| **F20** — advanced filtering | P2 | Built 2026-09-15; the review's findings against it closed 2026-09-17. |

❌ Do not report a phase "done and verified" while a feature listed for it is missing. P1 was reported done
with F9 absent, which is how a locked §3 decision went unimplemented for four phases.

**F9 — living-person privacy.** First pass 2026-09-15; **five more leaks of the same rule were found and
closed on 2026-09-17**, so the first sign-off was wrong and is recorded here rather than rewritten away.

`PersonService.getById` returns a sealed `PersonView`: either `PersonDetailResponse` or
`PersonRedactedResponse`, which has **no field** for notes, branch, alternate names or timestamps rather than
nulling them — there is nothing to forget to blank. The unguarded overload was removed outright so a caller
cannot reach the full record by accident.

Two gaps in the same family were closed with it: a MEMBER could **write** to the gia phả directly, which made
F17 pointless, and could read the GEDCOM export, the book, the audit trail and the quality findings — none of
which can be redacted without lying about what they contain. Writes are now EDITOR+, deletes and merges ADMIN.

> ⚠️ **What the 2026-09-15 pass missed, and why.** `Role.maySeeLivingDetails` was a shared *predicate*, not a
> shared *guard*: each service hand-wrote the lookup and the `if` around it, so a feature that simply forgot
> was indistinguishable from one that complied, and nothing in the build could tell. Five readers had forgotten:
> `EventService.getById` (no role at all — id enumeration handed a MEMBER every living person's birth date),
> `EventService.findBySubject` for `FAMILY` subjects (a living couple's ngày cưới, with a test that *blessed*
> the gap as deliberate), `SourceService.findCitations` and `GraveService.findByPerson` (guarded only by
> `{!hidden && …}` in React, which §3.6 forbids in as many words), and `MediaService.findByTarget` for `FAMILY`
> and `GRAVE` targets.

**`PersonService.isLiving(Long)` is now the single guard**, and the fail-closed-on-unknown-id rule lives there
and nowhere else. ❌ Do not re-inline a living check into a feature: a private `isLiving` copy is how this
happened, and the copy always drifts. A new endpoint returning person-derived data calls it, or it leaks.

A grave is **not** automatically public: a sinh phần is a plot built for someone still alive, so the media
guard resolves the grave's owner rather than assuming the dead. 22 unit tests, plus 12 live HTTP checks run
against the compose stack — the boot itself is part of the check, because `event` now depends on `family` and
`source` on `person`, and a mock can never catch the bean cycle that would make.

**F11 — duplicate merge done and verified** (2026-09-15). `POST /api/v1/merges/persons` (ADMIN only) folds one
person into another. The order is not negotiable: everything pointing at the duplicate is repointed **before**
the person row is deleted, because `family_children` and `graves` cascade on delete and the other order loses
them silently. A test pins that order. 17 unit tests and 27 end-to-end checks.

A merge has to survive six constraints, and each one has a test:

| Constraint | What the merge does |
|---|---|
| `uq_person_names_one_primary` | The duplicate's names are copied as **alternates**; the survivor keeps their own primary |
| `ck_families_distinct_partners` | A union recording the two as married to each other is **dropped and reported** when childless, and the whole merge is **refused** when it has children — see below |
| `uq_family_children` | A child recorded under both unions is kept once |
| `graves.person_id UNIQUE` | The survivor's grave wins; the other is named in the result, because cải táng means it may be the right one |
| `uq_citations` (NULLS NOT DISTINCT) | A citation of the same source at the same place is kept once |
| ancestry cycle (§5.1) | Merging an ancestor with a descendant is refused **before** anything moves |

- Two unions naming the same spouse are folded into one and the children come across; a union whose spouse is
  unrecorded is left alone, because two unknown spouses are not evidence of one marriage.
- Events are moved, never deduplicated: two recorded birth dates is a contradiction for the `quality` page to
  surface, not something a merge should quietly resolve by picking one.
- The reason is required and lands in the audit trail on both rows, and the deleted duplicate keeps their own
  trail including their last known state (§3.8).

> ⚠️ **Two silent data-loss bugs, fixed 2026-09-17. Both were in code that had a passing test.**
>
> **Dropping the self-union took its children's parents with it.** `family_children.family_id` is
> `ON DELETE CASCADE` (`V3`), so deleting that union deleted every child link hanging off it. If a bad GEDCOM
> import recorded the two duplicates as married *and* gave that union children, the merge orphaned real people:
> `recomputeGenerations()` then pushed them to đời 1 as if they were thuỷ tổ, `childLinksMoved` still reported
> 0, and the only note said "bỏ union #7". The merge now **refuses** when the shared union has children
> (`FamilyService.findSharedUnions` → `SharedUnion`), naming the union and the count so an ADMIN fixes the
> marriage record first. ❌ Do not "improve" this into converting the union to a single-parent family: that is
> the app rewriting blood relationships on a guess, in a case rare enough that a wrong guess goes unnoticed.
>
> **Moving events did not recompute `living`.** `EventService.reassignPerson` set `subjectId` and flushed, while
> every other event write calls `recomputeLiving`. Absorb a duplicate carrying a DEATH into a survivor with no
> events and the survivor stays `living = true` for ever — so a cụ who died in 1955 became invisible to every
> con cháu behind §3.6, with no screen able to reset the flag. `living` is derived (§3.4): **every write that
> moves or changes an event must recompute it.**

**F3 — photos and scans done and verified** (2026-09-15). `V8` adds `media`, polymorphic over person, union and
grave like `citations`. `StorageService` is the three methods §3.9 allows and nothing else; `MinioStorageService`
and `CloudinaryStorageService` are both `@ConditionalOnProperty` on `app.storage.provider`. MinIO is no longer
idle — it holds real objects for the first time since P0. 14 unit tests, 25 end-to-end checks, and the Cloudinary
integration test that §3.9 asks for, skipped without credentials rather than mocked: a mocked Cloudinary would
prove only that the mock works.

- **`uq_media_one_portrait` allows exactly one portrait per record**, so the tree and the book pick a photo
  without guessing. Setting a new one demotes the old rather than being refused — choosing a different portrait
  is a normal thing to want.
- The end-to-end check **fetches the signed URL and compares the bytes**, because an upload that returns 201 and
  a URL no browser can open still looks like success from the API alone.
- A PDF is a first-class upload: an A3 scan of the gia phả is usually one, and rejecting it for not being an
  image would refuse the most valuable thing a family has to add.

> ⚠️ **`spring.servlet.multipart.max-file-size` must stay set, or the 20 MB limit is a lie.** Boot's default is
> **1 MB**, and the multipart resolver rejects the request *before* the controller runs — so `MediaServiceImpl`'s
> own `MAX_BYTES` check and its "Tệp quá lớn, tối đa 20 MB" message were unreachable code from P2 until
> 2026-09-17, and the A3 scan this whole feature exists for was refused with an opaque error. `application.yml`
> now sets 20 MB / 25 MB to match `MAX_BYTES`. ❌ Do not raise `MAX_BYTES` without raising these too.

> ⚠️ **The living-person guard covers all three target types, not just `PERSON`.** A wedding photo attached to
> the union of a living couple, or a photo of a sinh phần, is the same private detail as a portrait — and the
> URL handed out is a bearer link (§3.9). `GRAVE` resolves the grave's owner through `GraveService.personIdOf`
> rather than assuming a grave belongs to the dead.

**F20 — advanced search done and verified** (2026-09-15). `GET /api/v1/search/persons` filters by name, chi, đời,
living status, birth-year range, death-year range and place. 10 unit tests and 22 end-to-end checks.

- **The filters used to replace each other.** `PersonService.search` was an `if/else if` chain, so
  `?query=X&generation=3` silently ignored the generation and returned every X in the clan. They are now one
  query where every given filter is ANDed and a null one is simply not applied.
- Results are ordered by **tên, then tên đệm, then họ** (§5.2). The old query had no `ORDER BY` at all.
- A place filter matches that place **and everything beneath it** (§3.7), or it fails on exactly the records
  that were entered most precisely — a birth recorded against a xã would not be found by its tỉnh.
- The year compared is the **recorded year**, not `sort_date`: a "khoảng 1890" birth is a 1890 birth to anyone
  searching, and `sort_date` turns a `BETWEEN` range into its midpoint (§3.2).
- ⚠️ **A date filter is a privacy channel.** A MEMBER who may not see a living person's dates could otherwise
  recover them by elimination — ask for 1990–1995 and see who comes back. So for callers below EDITOR a date or
  place filter only ever matches the deceased, and asking for the living *and* a date range returns nothing.

Keep this line updated when a phase completes.

### 8.1 The review of 2026-09-16/17

A max-effort review over the F9/F11/F3/F20 code reported 30 findings. Most are fixed and written up in the
feature sections above; **one is still open and listed at the end of this section**. The decisions worth not
relitigating are:

- **Rows that name a person or a union by id, with no FK, are now reached deliberately.** `merge` follows the
  unions it folds (rows move to the survivor) and the ones it drops (rows are deleted and the counts reported),
  and a new `purge` feature deletes a person's `events`, `citations`, `media` and `suggestions` — objects
  included — before the person row goes. ❌ Do not delete a person through `PersonService.delete` from a
  controller again: it cascades nothing, and four tables are left naming somebody who is gone.
- **`purge` refuses to delete the only recorded parent of a union that has children**, the same way `merge`
  refuses the equivalent merge. Deleting them used to null both partner slots and trip
  `ck_families_has_partner` — a 500 in the middle of an ordinary delete.
- **The search `LIKE` has an `ESCAPE '\'` clause and the query is escaped before binding.** Without it `?query=%`
  returned the whole clan. The name join is **LEFT**, or a person with no primary name is in the database and in
  no list.
- **The restrictive GET matchers are method-less**, because bound to `HttpMethod.GET` a `HEAD` request matches
  nothing and falls through to `anyRequest().authenticated()`.
- **Efficiency**: `findPersonIdsByEvent` is one JPQL query instead of a stream over `findAll()`;
  `findWithDescendants` is one `WITH RECURSIVE`; the ancestry walk loads the graph in two queries; and
  `Person.names` carries `@BatchSize(100)`. Measured on the live stack: `/quality` went from one query per
  person to a fixed handful for the whole page. ⚠️ This line said "**one**", which was never true — mẻ 2 (§8.4)
  counted about 37 at 3,000 people. It is now **four** by construction (dated events, unions, child links, people
  with their names), counted from the code rather than measured.

**Still open.** Two are deliberate; the other five are simply not done yet. ❌ Do not treat this section as
closed until the table is empty — an earlier draft of it listed only eleven of the thirty findings and was
read, by its own author, as the whole list.

| Where | Defect | State |
|---|---|---|
| `CloudinaryStorageService.resolveUrl` | Returns an unsigned, permanent CDN URL while MinIO's expires, so a leaked prod link never dies | Deliberate — switching to `type: authenticated` cannot be verified without a real Cloudinary account and breaks every image in production if it is wrong. Do it with credentials in hand, not blind. |

**`EventType` and `CitationTargetType` now live in `common/model`**, beside `MediaTargetType` and the other
discriminators §4 names. `book`, `quality`, `gedcom`, `search`, `merge` and `purge` all reference them, which is
exactly the case §4 says belongs in the shared kernel rather than in one feature's `domain`. Nothing about the
values changed, so `@Enumerated(EnumType.STRING)` reads every existing row unchanged.

> ⚠️ **A 4xx is never retried.** `new QueryClient()` defaults to three retries with backoff, so a deleted
> person's page sat on "Đang tải…" for **7.4 seconds and four requests** before admitting the person was gone
> — measured, not guessed. `App.tsx` now refuses to retry any 4xx, because a 404 does not become a 200 on the
> third attempt; the same page now settles in **0.6 seconds and one request**. ❌ Do not replace this with a
> bare `retry: 0`: a 5xx or a dropped connection is still worth retrying.

`branchId` is now off `PersonSummaryResponse` entirely rather than blanked per row — a list is served to every
role unredacted, so the only safe shape is one that never carried chi. The frontend's `PersonSummary` lost it
too and `PersonDetail` gained it, and a test asserts the record has no `branchId`, `notes`, `names` or
`createdAt` component, so the field cannot creep back.

> ⚠️ **Deleting a person left orphans until 2026-09-17, and the old rows are still there.** A dev database
> checked that day held eight `media` rows naming people who no longer exist, and re-checked on 2026-09-18 it
> also held **42 orphan `events`** from the same era — `events.subject_id` is `ON DELETE SET NULL` against the
> member, not the subject, so nothing cascaded. `purge` stops new ones and was re-verified that day: deleting
> several hundred people through `DELETE /api/v1/persons/{id}` added **no** orphan. Nothing cleans up the old.
> The queries are `SELECT m.* FROM media m LEFT JOIN persons p ON p.id = m.target_id WHERE m.target_type =
> 'PERSON' AND p.id IS NULL` and the same shape over `events` on `subject_type`/`subject_id` — and the storage
> objects have to go with the media rows.

### 8.2 The review of 2026-09-17 — auth, member, security

A second max-effort review, this time over `auth`, `member` and `common/security`, reported 17 findings. Nine
are fixed and verified; the password work below closed four more. What is worth not relitigating:

> ⚠️ **Replay revocation was undone by the exception that reported it.** `refresh` revoked every session for the
> member and then threw `UnauthorizedException` — a `RuntimeException`, so Spring rolled the transaction back and
> the revocation with it. Reproduced live before the fix: token B still refreshed with **200** after the replay of
> token A was "detected". §8's P0 sign-off lists "replay revocation" among ten verified checks, and it had never
> worked. The method is now `@Transactional(noRollbackFor = UnauthorizedException.class)`. ❌ Do not tidy that
> annotation away: without it the whole guard is decorative.

**Everyone changes their own password; only an ADMIN changes someone else's.** `POST /api/v1/members/me/password`
takes the current password and is open to any signed-in role; `POST /api/v1/members/{id}/password` takes only the
new one and is ADMIN-only, for a con cháu who has forgotten theirs and asks the trưởng tộc. **No middle role was
added**: a resetter can take any account they reset, so a "chi editor who helps with passwords" is an ADMIN in
everything but name, and three roles are already the most a single clan will keep straight.

- Both paths call `AuthService.revokeAllSessions`, or the old password's sessions outlive it and the change
  protects nobody from whoever knew it.
- The `me` matcher is listed **before** `/api/v1/members/*/password` in `SecurityConfig`, because `*` matches
  `me` too — reversed, a MEMBER could never change their own password at all.
- `changeOwnPassword` re-checks the current password even though the caller is already authenticated: otherwise
  whoever borrows an open session locks the owner out of their own account.

**`auth` no longer imports anything of `member`'s but its service interface.** It held `MemberRepository`,
`MemberMapper` and the `Member` entity, which is the §4 boundary broken in the one place where the thing crossing
it is a password hash. `MemberService` gained `verifyCredentials`, `findActive`, `changeOwnPassword` and
`resetPassword`, and the dummy-hash constant moved with them. `findActive` is called on **every** rotation, or
disabling an account never ends the sessions already open.

> ⚠️ **A stray `HttpMessageConverter` bean had broken Swagger UI for the whole project's life** (found
> 2026-09-17, while switching the docs on for dev). `HttpMessageConverterConfig` exposed a
> `JacksonJsonHttpMessageConverter` as a `@Bean` so two security handlers could inject it — and Boot **prepends**
> context converters to MVC's list, so Jackson then claimed springdoc's `byte[]` return on
> `produces = application/json` and base64-encoded the entire document. `/v3/api-docs` answered 200 with a
> quoted base64 string and the UI said "Unable to render this definition". The PDF and GEDCOM endpoints survived
> only because Jackson cannot write `application/pdf`. The converter is now a **static factory**, not a bean.
> ❌ Never publish an `HttpMessageConverter` as a bean merely to use it yourself — it silently re-orders content
> negotiation for every endpoint in the app.

**Swagger is off unless switched on.** `springdoc.api-docs.enabled` is `${API_DOCS_ENABLED:false}` and
`docker-compose.yml` sets `"true"`, so forgetting to configure production leaves the whole contract private
rather than published. `JWT_SECRET` likewise has **no fallback**, and `JwtProperties`' compact constructor
refuses a null, blank, placeholder or short secret at startup rather than signing tokens with a value from a
tutorial.

> ⚠️ **Rotation was read-then-write, so two concurrent refreshes both won** (fixed 2026-09-18). Both passed
> `isUsableAt`, both set `revokedAt`, and both were issued a new pair from one stolen cookie.
> `revokeIfLive(id, now)` is a conditional `UPDATE ... WHERE revoked_at IS NULL` and **the row count is the
> lock**: 0 means someone else got there first, and the caller is refused. Proved by firing four concurrent
> refreshes of one token at the running stack: exactly one 200, three 401. ❌ Do not replace it with a field
> set — the check and the write have to be the same statement.

> ⚠️ **The rotation fix made a reload in dev sign the user out, and would do the same to two tabs** (found
> 2026-09-23). `SessionBootstrap` restored the session in a `useEffect`, and StrictMode runs effects twice — two
> refreshes of one cookie. Since `revokeIfLive`, the loser gets 401, and if it arrives after the winner commits
> it looks exactly like a replay, so `revokeAllForMember` ends the winner's fresh session too. The backend is
> right to do that (§8.2 above). `refreshSession` now shares one in-flight request among its callers, as the
> 401 interceptor already did. ❌ Do not "fix" this with a reuse window on the server: that is two sessions from
> one stolen cookie again.

**Spent refresh tokens are swept nightly.** `RefreshTokenCleanup` deletes rows expired or revoked longer ago
than one refresh TTL, which leaves a revoked row in place long enough to still explain a replay that happened
just before the sweep. `@EnableScheduling` is on `GenealogyApplication`.

**The password UI is done** (2026-10-01), and building it found three defects in the backend it sat on:

- **A wrong current password signed the caller out.** It answered 401, which the client's interceptor reads as an
  expired session: refresh, retry, 401 again, clear the store. It is a 400 now, counted by the same
  `LoginThrottle` as sign-in (keyed on the account's email), so a borrowed session cannot guess the current password
  without limit; the sixth guess is 429 even when it is right.
- **BCrypt reads 72 bytes and Spring Security 7 throws past that**, while `@Size` counted characters (`max = 100`
  on create, `72` elsewhere) — 30 accented letters are 90 bytes and were a 500. `MemberServiceImpl.encode` refuses
  more than 72 UTF-8 bytes with a Vietnamese 400 on create, change and reset alike; the form counts bytes too.
- **An ADMIN could reset their own password**, which skips the current-password check `changeOwnPassword`
  exists for — a borrowed ADMIN session took the account. `resetPassword` takes the actor and refuses its own id.

`GET /api/v1/members` (ADMIN, method-less matcher, guarded again in the service) lists the accounts for the new
`/members` page, where each row but one's own has "Đặt lại mật khẩu". One's own is "Đổi mật khẩu" in the sidebar's
account menu, which signs out on success because the server has already ended every session. 27 HTTP checks with
three roles; note that `curl.exe` turns a non-ASCII argument into the ANSI codepage, so a Vietnamese body must be
sent with `--data-binary @file` (§9).

**Still open.** ❌ Do not treat this section as closed until the table is empty.

| Where | Defect | State |
|---|---|---|
| — | — | Empty since 2026-10-01 |

The two-sentence doc comments that stood here were fixed on 2026-09-18; re-scanned repo-wide on 2026-09-23 with a
scanner first proved on a file holding one of each violation (two sentences, a second paragraph, `<p>`): zero.

### 8.3 The review of 2026-09-18 — `gedcom` and `book`

The first batch of a package-by-package sweep, and the first review in this repo where **all eleven angles ran
and every one of them reported**: line-by-line, dropped behaviour, cross-file, language pitfalls,
wrapper/orchestrator, security, efficiency, altitude, reuse, simplification, conventions. 113 raw findings, **41
distinct defects** after merging. ❌ Do not record an angle as run until its result is in hand — the previous
review claimed eleven and had run seven, and nobody could afterwards say which.

`gedcom` and `book` held **1,032 lines with no test at all** (`BookServiceImpl`, `GedcomExporter`,
`GedcomImporter`, `BookFonts`, `GedcomServiceImpl`) — the whole parse path, the whole write path and the whole
PDF render. Only four small helper classes were covered.

> ⚠️ **Exporting the gia phả and importing it back turned every con nuôi into a con đẻ.** GEDCOM 7 puts
> `PEDI` under `INDI.FAMC`, which is where the exporter wrote it; the importer read `PEDI` from `FAM.CHIL`,
> where nothing ever wrote it. So `relationOf(null)` answered `BIRTH` for every child on our own file. `PEDI`
> also cannot hold the §3.1 asymmetry (`BIRTH` to one partner, `STEP` to the other) and its enumset has no
> `STEP` at all, so both now ride in a `PEDI.PHRASE` as `TO_P1/TO_P2`, which the importer reads first. A
> foreign file's `FAM.CHIL.PEDI` is still read as a fallback.

> ⚠️ **Every lunar date was exported as an exact date.** The `LUNAR` branch of `GedcomDates.format` returned
> before the modifier `switch`, so `ABOUT`, `BEFORE` and `BETWEEN` lost their keyword, and a `BETWEEN` lost its
> second endpoint. §8 P5 claims "our own round trip is lossless" and it was not: a giỗ recorded "khoảng 1890"
> came back as a hard 1890. The phrase also **replaced** `date_raw` with machine text, against §3.2's
> "verbatim user input, never discarded" — it now carries the family's words after a ` — ` separator, and a
> phrase whose lunar part will not parse falls back to solar instead of discarding the date.

> ⚠️ **`1 DEAT Y` was dropped, and the cụ stayed alive for ever.** `1 DEAT Y` is the GEDCOM 5.5.1 idiom for
> "died, date unknown"; the fact is the node's own value, which `createEvent` never read, so with no `DATE`
> and no `NOTE` it returned early. `living` is derived from events only (§3.4) and `recomputeLiving` runs only
> on an event write — so a cụ who died in 1920 imported as living, was redacted from every MEMBER by §3.6, and
> **no screen in the app could clear the flag**. An event node is now created when its value is `Y` or it has
> any child at all, not only when it carries a date or a note.

**Import is per-record, not all-or-nothing — and that took the right annotation, not the absence of one.**
Both `catch (ApiException)` blocks in `GedcomImporter` were decorative: `FamilyService.addChild` and
`EventService.create` are `@Transactional` and joined `importFile`'s transaction, so Spring set rollback-only
before the `catch` ever ran and the commit threw `UnexpectedRollbackException` — a 500, nothing imported, and
the warning list lost. One duplicated `CHIL` line destroyed a whole file. `importFile` is now
`@Transactional(propagation = Propagation.NOT_SUPPORTED)`.

> ⚠️ **Removing `@Transactional` from the method was not enough and made it worse.** The class carries
> `@Transactional(readOnly = true)`, so the method inherited a **read-only** transaction and every insert
> failed with `cannot execute INSERT in a read-only transaction`. 225 unit tests passed with that bug in
> place, because mocks cannot see Spring's transaction wiring (§9) — it was found by uploading a file to the
> running stack. ❌ Do not "simplify" the propagation back to a bare removal.

This is a deliberate semantic change, recorded here so it is not undone: **a GEDCOM import now adds what it
can and reports what it refused.** §8 P5 already locked "import adds, it never merges" and the response type
exists to say what happened, so all-or-nothing was never the intent. A partly-imported file can be undone by
deleting the people it added; a 500 with an empty database leaves the trưởng tộc nothing.

The rest of the tier-1 and tier-2 fixes, each verified over HTTP against the running stack:

| Defect | Fix |
|---|---|
| A surname-less name gained a họ on its own round trip | `format` writes the slashes even when empty (`Văn An //`), and the surname-first heuristic is skipped when slashes were present — empty slashes say "no họ", which is not the same as saying nothing |
| A structured writer's `1 NAME //` + `GIVN`/`SURN` was skipped as nameless | `GedcomNames.parse` reads the subtags, which win over the payload |
| `31 FEB 1890` was **stored** as 28 February | The GEDCOM-side clamp is gone; §3.2 clamps `sort_date` only and keeps the day as recorded. A day of `0` (some exporters' "unknown") is dropped rather than crashing `LocalDate.of` |
| `FROM 1918 TO 1922` became "after 1922" | Read as `BETWEEN`; a bare `TO 1922` is `BEFORE`; a range missing its upper bound is `AFTER` rather than a `BETWEEN` whose null endpoint made `deriveSortDate` unbox null and kill the import |
| `BET AND 1900`, `0 CONT x`, `-1 INDI`, `1 HUSB @` each 500'd the whole file | Guarded: a malformed `BET` refuses the date rather than inventing one, a continuation index is floored at 0, a negative level is not a GEDCOM line, and a pointer needs three characters — `"@"` satisfies both `startsWith` and `endsWith` |
| A BOM cost the `HEAD` line and faked the skipped count | `parse` strips U+FEFF, which `String.strip()` does not — it is not whitespace |
| `NOTE`/`SNOTE` pointers were stored as the literal `"@N1@"` and printed in the book | Top-level `NOTE` records are collected first and pointers resolved through them |
| Both wives landed at `order_index = 0` | File order per partner becomes the union's `orderIndex` — the only sequence a GEDCOM carries |
| A name cut at 50 chars could be split inside a surrogate pair | The cut backs off a char when it lands on a high surrogate: half a chữ Hán is not encodable as UTF-8, and the insert failed at commit |
| Picking the wrong file answered **200** with `0/0/0/0` and a success toast | A parse yielding no records is a `BadRequestException` |

**`birth_order` is left null when the file does not record it** (decided 2026-09-18). CHIL line order is
arrival order — a researcher's typing order, or alphabetical — and writing it as `birth_order` made
`KinshipServiceImpl.comparesElder` stop returning null, so the app answered "chú" with full confidence on an
ordering it had invented. §5.3 is explicit that a plausible wrong word is worse than an honest ambiguous one.
❌ Do not reintroduce it without a flag that says where the order came from.

Three defects the review ranked as the most dangerous of what tiers 3–6 held were fixed next, out of tier order
(2026-09-18), because "efficiency" understated one of them and §3.8 was being broken from every surface, not
just the import:

> ⚠️ **Folding a long NOTE was quadratic, and it was reachable by any ADMIN.**
> `GedcomNode.appendContinuation` did `value = value + separator + line`, copying the whole accumulated payload
> on every `CONT` line. Simulating the same algorithm: 5,000 lines ≈ 0.4 s, 20,000 ≈ 1.6 s, 60,000 ≈ **71 s** of
> pure string copying — and the upload cap allows roughly 300,000. Continuations now accumulate in a
> `StringBuilder` materialised once at the end of `parse`. Measured on the live stack afterwards: 5,000 lines
> 0.06 s, 20,000 0.12 s, 60,000 0.21 s — twelve times the input for three and a half times the time. ❌ Do not
> "tidy" the builder back into a concatenation.

**`PLAC` is written on export and matched on import.** Export renders the full §3.7 path most specific first
(`Xã A, Huyện B, Tỉnh C`), built from one `PlaceService.findAll()` — a birth recorded against a xã means
nothing to another program without its tỉnh. Import reads the leading component and resolves it through
`PlaceService.findIdByName`, which goes through `immutable_unaccent` (§4.3) so a foreign file's "Ha Noi" still
finds "Hà Nội". **A name the clan does not hold now produces a warning** instead of vanishing with every
counter at zero — that silent loss was the actual defect. ❌ Do not make the importer *create* places to close
the gap: guessing WARD/DISTRICT/PROVINCE from a component's position in a comma list is the app inventing
§3.7 structure from a stranger's file.

> ⚠️ **Superseded twice.** `findIdByName` was replaced by `findOrCreatePath` later the same day (below), and that
> method's positional guess — the very thing this paragraph warned against — was replaced in mẻ 5 (§8.8 #10):
> levels now come from `PLAC.FORM`, a path with no `FORM` is positional only at exactly four components, and
> anything else is `OTHER`. `findIdByName` no longer exists.

> ⚠️ **Unions, child links and events were unaudited from every surface, not only from the import.**
> `AuditService` was injected in `PersonServiceImpl` and nowhere else, so changing a ngày cưới through the UI
> left no revision at all — §3.8 says the trail must answer "who changed cụ's ngày mất, and why", and for
> everything except a person it could not. `FamilyService.create/update/delete/addChild/removeChild` and
> `EventService.create/update/delete` now all take an `actorId` and record inside their own transaction, so the
> §3.8 guarantee (a rolled-back edit leaves no revision) still holds. `addChild` and `removeChild` record
> against the **union**, because the union's child list is what a reader sees change. The GEDCOM import threads
> the acting ADMIN through as its actor. ❌ Do not add a write path to either feature without an `actorId`.

**Sources, citations and honest counters** (2026-09-18). The importer read no `SOUR` record and no citation
pointer, so exporting the gia phả and importing it back lost every "on what basis" (§3.8) with all four
counters reading zero. It now reads `SOUR` records, `REPO` records, and the `SOUR` pointers under INDI and FAM,
with `PAGE` as the locator and `DATA.TEXT` as the quote.

> ⚠️ **`skipped` meant three different things, and the UI picked the wrong one.** It counted unmodelled
> top-level records, an INDI whose name would not parse, and a FAM with no valid partners — while `vi.json`
> rendered it "Bỏ qua {{n}} bản ghi không thuộc phạm vi app". So a trưởng tộc was told a **discarded cụ**
> was "outside the app's scope". The response separates `skipped` (nothing here to put it in) from `dropped`
> (we model this and still could not keep it), every drop writes a warning, and the UI renders `dropped` in
> the destructive colour. ❌ Do not fold them back together.

**GEDCOM 7 conformance** (2026-09-18). The file claimed `VERS 7.0` and then wrote payloads 7.0 does not define:

| Was | Now |
|---|---|
| `1 REPO <free text>` | A `0 @R1@ REPO` record with a `NAME`, and `1 REPO @R1@` — g7 defines `SOUR.REPO` as a pointer |
| `1 TEXT <source's date>` | `1 PUBL` — a source's `TEXT` is what it *says*, not when it dates from |
| `1 TYPE CLAN_BOOK` | `1 _STYPE CLAN_BOOK`, declared in `HEAD.SCHMA` — an extension has to be documented in the file |
| `2 TYPE HUY` | `2 TYPE OTHER` + `3 PHRASE HUY` — g7's `NAME.TYPE` enumset has no tên húy, and `PHRASE` is what it provides for exactly this |
| `1 NOTE @Nhà thờ họ` | `1 NOTE @@Nhà thờ họ` — an unescaped leading at-sign is a malformed pointer to a conforming reader |

> ⚠️ **The at-sign escape must not touch a real pointer.** The first version doubled every payload beginning
> with `@`, which turned `1 REPO @R1@` into `@@R1@` and would have broken `HUSB`, `WIFE`, `CHIL` and `FAMC`
> across the whole file. It escapes only a payload that is **not** a well-formed `@XREF@`. Caught because the
> check asserted the pointer was written, not merely that the export returned 200 — a round trip of the whole
> clan (465 people, 125 unions, 0 dropped) is now part of the verification.

**One Vietnamese date renderer, on the server** (2026-09-18). `book/VietnameseDateText` and the frontend's
`formatFuzzyDate` were two hand-written renderers of the same `GenealogyDateResponse`, and they had already
drifted: the book appended " (âm lịch)" and printed a `BETWEEN`'s month and day, the web page did neither — so
one giỗ read as `10/3/1950` on screen and `10/3/1950 (âm lịch)` in print. `GenealogyDateResponse` now carries a
**`display`** component rendered once by `common/model/GenealogyDateText`, following the
`PersonNameResponse.display` precedent, and both surfaces print it as-is. The old renderer and its test are
gone; the test moved to `GenealogyDateTextTest`. ❌ Do not add a second date renderer to any surface.

**A date recorded only as raw text survives** (2026-09-18). "đời Tự Đức" parses to no year, so `format` returned
null, the export wrote nothing at all, and the book printed no "Sinh:" line — against §3.2's "verbatim user
input, never discarded". The export now writes an empty `DATE` with a `PHRASE`, which GEDCOM 7 allows for
exactly this, the importer reads that back as a raw-only date, and `display` falls back to the raw text.

**`Gender`, `PersonNameType` and `FamilyStatus` now live in `common/model`** (2026-09-18), beside `EventType`,
`CitationTargetType`, `MediaTargetType` and the rest. §8.1 moved two discriminator enums and stopped; these
three were still in `person/domain` and `family/domain` while `gedcom`, `tree`, `quality`, `merge` and `search`
all imported them, which §4 forbids in as many words. 35 files changed imports; `@Enumerated(EnumType.STRING)`
means no stored row changed. `gedcom` now imports nothing from another feature's `domain` — ⚠️ **that was wrong
until mẻ 5**: `GedcomTags` still imported `source.domain.SourceType`, which moved to `common/model` with
`PlaceType` (§8.8 #38).

**The book prints portraits** (2026-09-18). §8 F3 recorded `uq_media_one_portrait` as existing "so the tree and
the book pick a photo without guessing", and the book had never injected `MediaService` — the constraint was
built for a consumer that did not exist, and a family who uploaded portraits got a text-only gia phả.
`MediaService.findPortraitKeys()` is a read-across projection (§4): **one** query for every person's portrait
key, and the book downloads only the ones it is printing, so a chi export does not fetch the whole clan's
photos. Verified by diffing the PDF before and after: 0 image XObjects → 1, at exactly the uploaded image's
200×260, and the extracted stream is byte-identical to the file that went in. Adding `StorageService.download`
for this is recorded in §3.9.

**Efficiency** (2026-09-18):

- **`FamilyService.addChildren` links a whole union's children with one đời recompute.** Each `addChild`
  reloads the parentage graph twice and rewrites every person's `generation`, and the importer called it once
  per `CHIL` line. Measured afterwards on the live stack with the **same 1,200 child links** split two ways:
  150 unions → 10.0 s, 600 unions → 28.8 s. Cost now tracks the number of unions, not the number of links;
  before the change both files paid 1,200 recomputes. ❌ Do not put the importer back on a loop of `addChild`.
  ⚠️ **Superseded 2026-09-24 by `FamilyService.importUnion`**: `create` and `addChildren` still recomputed once
  each per union, so an import paid two whole-graph recomputes per FAM record. `importUnion` creates the union
  and links its children with none, and the importer's closing `recomputeGenerations()` does it once for the
  file. Same two files, same 1,200 links: 4.1 s and 6.2 s — on a smaller dev database than the 09-18 figures, so
  read the order of magnitude, not the ratio. `addChildren` had no other caller and is gone.
- **`GedcomExporter.individual` reads two prebuilt indexes** (`unionsAsPartner`, `childEdges`) rather than
  scanning every union for every person — that was 3.6M visits for a 3,000-person clan.
- **`hibernate.jdbc.batch_size: 50` with `order_inserts` / `order_updates`**, because an import wrote one
  statement per row: ~19,000 round trips for a 3,000-person file.
- **`BookService.render` is `@Transactional(propagation = NOT_SUPPORTED)`.** Laying out the PDF takes seconds
  and needs no database; the class-level read-only transaction held a pooled connection through all of it, so
  ten concurrent book downloads starved every other endpoint.

**A foreign file's `PLAC` now builds the §3.7 hierarchy** (2026-09-18). `PlaceService.findOrCreatePath` reads
the path GEDCOM writes — most specific first — and matches or creates each level through
`immutable_unaccent`, so `Xã A, Huyện B, Tỉnh C, Việt Nam` becomes four linked rows and a second event naming
the same path reuses them. **The level comes from the distance to the END of the path**, because the last
component is always the widest: `COUNTRY, PROVINCE, DISTRICT, WARD`. The first version indexed from the front
and produced "Việt Nam / WARD" under nothing — caught by reading the rows back, not by the import's 200.

**`HEAD.GEDC.VERS` is read.** The endpoint claims 5.5.1 and 7.0 and until now answered a `VERS 4.0` file
exactly the same way; an unknown or missing version now warns that the file was read as 7.0.

**The parse streams.** `GedcomNode.parse(BufferedReader)` reads a line at a time and the controller hands it
the upload's `InputStream`, where before a 20 MB file became the bytes, a 40 MB `String`, an array of every
line and a stripped copy of each — all live at once.

**§4.1 comments**: the 9 violations in scope are fixed, and so are the 9 multi-line JSX comments repo-wide —
`grep` for `{/*` without a closing `*/}` on the same line now returns nothing. **The 11 cleanups are done**:
the unreachable guard in `part`, `EVENT_TAGS` replaced by an exhaustive switch (a new `EventType` now fails the
build instead of exporting as `EVEN`), the duplicate month table, the two `unionsByPartner` builds, the
duplicate `NAME.TYPE` arms, `MODELLED_TAGS` as a constant, `common/web/FileDownloads` shared by both download
endpoints, and the name-length limits now read from `PersonNameRequest` rather than a fourth copy.

**Mẻ 1 is closed.** All 41 defects are fixed or recorded as a deliberate decision. ❌ Do not read that as "the
`gedcom` and `book` code is correct" — read it as "eleven angles looked, and what they found was dealt with".

### 8.4 The review of 2026-09-23 — `tree` and `quality`

Mẻ 2 of the sweep. All eleven angles were launched together and **all eleven reported**: 153 raw findings, **34
distinct defects** after merging. The tier-1 fixes — the ones where the app told a family something false — are
done and verified: 266 unit tests, and 20 HTTP checks against the running stack with the test data removed after.

> ⚠️ **A union edit could make a person their own ancestor, and nothing reported it.** `FamilyServiceImpl.update`
> ran `validatePartners` only; the cycle guard lived in `addChild` alone. Setting partner2 of a union with
> children to one of those children's descendants closed the loop, and `quality` had no cycle rule at all —
> `IssueSeverity.ERROR` was unreachable although §5.1 names it. `update` now runs the same descendant walk for
> every child of the union, and `quality` reports `ANCESTRY_CYCLE` for exactly the people on a cycle (not those
> merely below one). Proved by inserting a cycle with SQL and reading it back from `/quality/issues`.

> ⚠️ **Kinship treated a stepfather as a father.** The walk followed every `family_child` row, so a con riêng
> linked into the mother's second union called the stepfather "cha" and his younger brother "chú", and two
> con riêng of one union were "anh em ruột". Only `BIRTH` edges are walked now (§5.3).

**Kinship now reads the clan once.** `describe` loads `findAllUnions()` — two queries — and walks in memory; the
layer-by-layer walk cost 44 queries for a sibling and 124 for a bác on the mother's side, and stopped at six
generations, so two members whose common ancestor was seven up were told "không có quan hệ huyết thống". Past six
the answer is now "họ hàng xa" with the ancestor still named.

The rest of tier 1:

| Was | Now |
|---|---|
| An unrecorded sex became male: "chồng", "cha", "anh", "chú" + "bên nội" | "vợ/chồng", "cha/mẹ", "anh/chị", "chú/cậu"; `side` null (§5.3) |
| `throughMotherSide: boolean` — false meant father's side, not applicable, or unknown | `side: PATERNAL \| MATERNAL \| null` |
| Equal `birth_order` (twins) answered "em" with confidence | The ambiguous word |
| A cousin's child was "con họ"; a parent's cousin "cha họ" | "cháu họ"; "bác/chú/cô họ" by branch seniority |
| A divorced spouse was "vợ" | "vợ cũ" |
| The side came from whichever parent was `partner1` | From the parent on the shortest path; null when both are |
| `quality` compared `sort_date`: burial "1945" was before a death on 15/6/1945 | Spans (§5.1) |
| Child rules judged a stepmother as a birth mother: "mẹ mới 8 tuổi" | Birth links only |
| An inverted order printed "mẹ -20 tuổi" under the wrong rule | `BORN_BEFORE_PARENT` / `MARRIED_BEFORE_BIRTH` |
| Two BIRTH events after a merge: `findFirst` over an unordered `findAll`, no finding | The covering span, and `CONFLICTING_DATES` |
| Duplicates: `đ` kept its stroke, so "Đỗ Văn Đức" ≠ "Do Van Duc" | Mapped to `d`, matching `immutable_unaccent` (§4.3) |
| Duplicates keyed on `sort_date`'s year; unknown-year groups reported, against the comment above them | The recorded year; with none, the đời stands in; with neither, no finding |
| Only people with a dated event were loaded, so a name-only double import was never compared | `PersonService.findAllNodes()` — the whole clan |

**Tiers 2–5**, each screenshot-checked against a fixture built for the purpose (two wives, a con riêng, cousins
who married) and removed afterwards:

| Was | Now |
|---|---|
| A failed `/quality` read "Không tìm thấy điểm nào đáng ngờ"; `/tree/<deleted id>` sat on "Đang tải…" | Their own error states; a 404 says the person does not exist |
| `truncated` meant "someone is on the last layer drawn", so a childless last generation offered to go deeper | `truncatedBelow` / `truncatedAbove`, true only when a further generation exists; the picker reaches the server's 8 |
| The deepest people were drawn without their spouses | One closing pass fetches them, and doubles as the truncation check |
| † came from `living`, a privacy flag: a name-only thuỷ tổ looked alive | `recordedDeadIds` — a recorded death or burial (`EventService.findRecordedDead`) |
| Pedigree collapse: the second branch simply ended, and the fan left an empty arc | Drawn again as a dashed pointer (↺), not expanded |
| A cụ's three wives: children merged into one list, the third name outside its box | Numbered unions, children hang from their union's marker, boxes grow per row |
| Con nuôi and con riêng drawn exactly as con đẻ | A dashed link |
| Connectors stopped 12 px short of every single-person box | Each end meets its own box |
| Zoom drifted away from the cursor (~825 px from 1× to 2.5×); an old pan survived a re-centre | The zoom transform stands alone outermost: measured 1 px at 2.3×; reset on every new layout |
| Every zoom tick re-rendered every box | The drawing is memoised on the layout |
| The kinship answer lost its name when the search moved on; no debounce; never invalidated | Fixed; `AddRelationDialog`, merge and suggestion approval now invalidate `tree`, `kinship` and `quality` |
| §6.1: adding a child or spouse was reachable from the person page only | `AddRelationDialog` on the tree page, for the centred person |
| The ancestor walk pulled in every ancestor's siblings, drawn by no chart | Parents only |
| `/quality/duplicates`, `fetchDuplicates` and two i18n keys had no caller | Removed — `/quality/issues` carries the duplicates |

- **A slice stops growing at 1,500 people** (`TreeServiceImpl.MAX_PEOPLE`, decided 2026-09-23): depth 8 from a
  thuỷ tổ was the whole clan, which §3.5 forbids. The generation that crosses the cap is the last walked, and
  `truncated*` says so.
- **§3.6 decision**: union status and relation type are shown to a MEMBER — see §3.6.
- **§4.1, repo-wide**: 24 functions had their WHY between the doc and the declaration instead of in the body, and
  46 fields and constants still carried `/** */` — among them TypeScript interface fields and `static final`
  constants the 2026-09-18 scan had not classified, although that scan was reported as zero. Interface and
  repository methods keep the WHY above the declaration: they have no body to put it in.

**Mẻ 2 is closed.** All 34 defects are fixed or recorded as a deliberate decision. At close: 271 backend unit
tests, frontend type-check, lint and build clean, the backend booted on the final wiring, and the tier-1 HTTP
checks re-run green against it.

### 8.5 Review coverage — the one record of what has been reviewed

⚠️ **This table is the source of truth, not the scratchpad.** Until 2026-09-23 coverage lived only in session
scratchpad files, one of which (`code-review-max-state.md`) was left at the first wave's state and never updated
when the second wave finished. After a context compaction that stale file was read, §8.3's "claimed eleven and had
run seven" (which is about the **auth** review) was misapplied to the 09-16 review, and the user was told the
F9/F11/F3/F20 area had unclear coverage when it had all eleven. ❌ Update this table the moment a review's angles
report — never leave coverage only in a scratchpad.

| Date | Scope | Angles | Evidence |
|---|---|---|---|
| 2026-09-16 | Files of the F9/F11/F3/F20 session: `merge/**`, `media/**`, `search/**`, `suggestion/**`, `family/service/**`, `source/service/**`, `grave/service/**`, `place/service/**`, `person` dto/service/controller/repository, `event` service/controller, `SecurityConfig`/`AuthPrincipal`/`JwtTokenProvider`/`Role`, `V8`, frontend merge/media/search/person pages/`use-permissions`/router/quality page | **All 11**, in two waves | §8.1; scratchpad `code-review-findings-batch2.md` |
| 2026-09-17 | `auth/**`, `member/**`, `common/security/**`, `V1`, `V2`, frontend auth/member/api-client/session-bootstrap | **Not the standard 11**: ran line-by-line, cross-file, security (token lifecycle, authorization, input validation), efficiency, altitude, conventions, simplification + reuse, test coverage. **B, D, E were never run** | §8.2 |
| 2026-09-17/18 | `gedcom/**`, `book/**` (whole packages) | All 11 | §8.3 |
| 2026-09-23 | `tree/**`, `quality/**` (whole packages) + frontend | All 11 | §8.4 |
| 2026-09-23 | `person/**`, `family/**`, `event/**` (whole packages) + `V3` + frontend person/family/event — overlaps the 09-16 scope on the services and controllers | All 11 | §8.6 |
| 2026-09-25 | `branch/**`, `audit/**`, `purge/**` (whole packages) + `V5` + the `branches` table of `V3` + frontend `branch`, `audit` + their tests | All 11 — each recorded as it reported | §8.7; scratchpad `findings-branch-audit-purge.md` |
| 2026-09-25 | `place/**`, `source/**`, `grave/**` (whole packages) + `V4` + `V6` + the `places` table of `V3` + frontend `place`, `source`, `grave` + their call sites in gedcom/merge/purge/media/search/event | All 11 — each recorded as it reported; 147 raw findings | §8.8; scratchpad `findings-place-source-grave.md` |
| 2026-09-28 | `media/**`, `suggestion/**` (whole packages) + tests + `V7` + `V8` + `StorageProperties` + frontend `media`, `suggestion` + call sites in merge/purge/book/grave-card/person page | All 11 — each recorded as it reported; 150 raw findings | §8.9; scratchpad `findings-media-suggestion.md` |
| 2026-09-29 | `common/**` (whole: config, exception, model, security, util, web) + its tests + migrations `V9`, `V10`, `V11`, `V12` | All 11 — each recorded as it reported; seven were cut off by a rate limit and resumed with their context; 101 raw findings | §8.10; scratchpad `findings-common-migrations.md` |
| 2026-10-01/02 | Mẻ 8: frontend `App`/`main`/`router`/`index.css`, `home`, `gedcom`, `book`, `auth`, `member`, `shared/**` incl. the §6.0 shell, the restyled pages; backend `auth/**`, `member/**`; build & deploy config | All 11 — altitude, security, reuse, simplification on 10-01; the other seven were stopped at the user's request and re-run from scratch on 10-02; 106 raw findings | §8.11; scratchpad `review-mei8.md`, `findings-mei8.md`, `fixlist-mei8.md` |
| 2026-10-05 | Mẻ 9: the fix code of mẻ 1–8 — `GenerationCalculator`, `LivingRule`/`LivingRefresh`, the `family`/`event` service changes, `common/util` and `common/security` helpers, `branch`/`audit`/`purge`/`merge`, the rewritten `place`/`source`/`grave`/`media`/`suggestion` services, `auth`/`member` of mẻ 8, `V13`–`V16`; frontend `shared/lib`, `shared/components`, the place/branch/source/grave/audit/event/family/member/tree/media/suggestion UI **All 11, in three waves** — wave 1 10-06: A 7, B 7, C 13, security 7; wave 2 10-06: efficiency 10, altitude 8 (altitude did not reach `audit`, `common/security`, the `place`/`source` impls or `V13`–`V16`), E 7, D 10; wave 3 10-08: simplification 12 (did not reach `tree`, `merge`, `V14` or most frontend features), reuse 10, conventions 8 — 99 raw findings. All 11 launched 10-05 died on the weekly rate limit before reporting; each was resumed from its own transcript in the three waves the user asked for (10-06) | §8.12; scratchpad `review-mei9.md`, `findings-mei9.md`, `fixlist-mei9.md` |

**Never reviewed:** none of the backend packages. `auth`/`member` got the B, D and E they lacked in mẻ 8, and
`common/security` all eleven in mẻ 7.

Missing from the list above until 2026-09-25, found by diffing it against the tree rather than rereading it:

- **`V9`**, written during the mẻ 3 fixes, after that review. Every migration up to `V12` has been reviewed
  since (mẻ 3–7); `V13`–`V15` are mẻ 7's own fix code and have not.
- **Frontend `home`, `gedcom`, `book`**, `shared/components`, `shared/lib`, `shared/store`, `shared/i18n`,
  `App.tsx`/`main.tsx`. Mẻ 1 reviewed the `gedcom` and `book` **backend** only. The app shell of 2026-10-01 (§6.0:
  `shared/layouts`, `shared/ui` sidebar/dropdown/tooltip/sheet, the theme provider, `member` pages) is new and
  unreviewed too.
- **Build and deploy config** was reviewed in mẻ 8 (2026-10-02, §8.11) and has been rewritten since: compose, both
  Dockerfiles, `.dockerignore`, `ci.yml`, `.env.example`, `vite.config.ts`, `eslint.config.js`.
- **Code written to fix a review has not itself been reviewed.** The mẻ 3 fixes were unit-tested, HTTP-checked and
  screenshot-checked, but no angle has read them: `GenerationCalculator`, `LivingRule`, `LivingRefresh`,
  `RelationController`, the new `family`/`person` service methods, `common/util` `NameKey`/`StaleEdit`/
  `VietnamTime`, and the new frontend `place`, `branch`, `audit/revision-list`, `shared/components/
  confirm-delete-dialog`, the event and union dialogs and panels, and the person edit page. The same holds for the
  fixes of mẻ 1, mẻ 2, mẻ 4 and mẻ 5 — the last including `V11`, `common/util` `AdvisoryLock`/`LikePattern`/
  `TextCut`, `gedcom/domain/GedcomPlaces`, the rewritten `source`, `place` and `grave` services, and the frontend
  `place`, `source`, `grave`, `shared/lib` (`batcher`, `hierarchy`, `coordinates`) and `coordinate-fields`.

### 8.6 The review of 2026-09-23 — `person`, `family`, `event`

Mẻ 3 of the sweep. All eleven angles reported: 169 raw findings, **47 distinct defects**. **Closed 2026-09-24** —
the closing paragraph at the end of this section says what that does and does not mean.

Tier 1 at close: 294 backend unit tests, 24 frontend tests, 59 HTTP checks against the running stack with two roles
(test data removed after), the backend booted on the final wiring, and the anniversary and kinship screens
screenshot-checked.

> ⚠️ **Linking a child could hang the whole app.** `alignSpouseGenerations` looped for ever when a person with no
> parents married two people at different đời, holding a pooled connection each time until nothing answered. The
> đời rule now lives in `GenerationCalculator`, a pure function with its own tests, and is written down in §3.4.
> The answer to "whose đời does a con dâu take" is **her husband's**, and her own recorded parents get none rather
> than "đời 0" (decided 2026-09-23).

> ⚠️ **`living` failed open.** It read one arbitrary BIRTH's year with no `ORDER BY`, so "sau 1900", a `BETWEEN`
> or a second recorded birth could mark a living person dead and hand their details to every MEMBER. It ignored
> BURIAL, never re-ran when an event moved to another person, and the 100-year line never moved on its own. The
> rule is now `LivingRule` (§3.4); **a BURIAL counts as death** (decided 2026-09-23).

The rest of tier 1:

| Was | Now |
|---|---|
| `FamilyService.create` did not recompute đời; a person unlinked from every union kept their old đời | Every union and link write recomputes; `applyGenerations` clears whoever the graph no longer places |
| Purge recomputed from cached union entities that still named the deleted person | The graph is read through `findAllPartners`/`findAllLinks` projections, straight from the tables |
| Swapping partner1 and partner2 kept each child's relation slots, so a con riêng became the other's con đẻ; merge's `moveChildren` did the same | The relations are swapped with the partners |
| Merge folded a divorced-then-remarried couple's two unions into one | Only unions moved onto the survivor are folded |
| Deleting a union left its events, citations and media behind | It goes through `PurgeService.purgeUnion`; unions dropped with a person are cleaned the same way and audited |
| The add-relation dialog kept the union chosen for the previous person on the tree | Keyed by person, and a union not in the current list is ignored |
| Two editors could each pass the cycle check and close a loop; no row had a version | A transaction-scoped advisory lock serialises every parentage write; `persons`, `families` and `events` carry `@Version` and a stale form is a 409 through `common/util/StaleEdit` |
| `GET /persons?branchId=` and alternate names in search were open to a MEMBER | `GET /persons` is gone (`/search/persons` covers it); alternate names match a living person only for EDITOR+ |
| A giỗ lost its modifier, lost 31 February, clamped lunar 30 to a solar month end, and had no leap month | Only EXACT/ABOUT/ESTIMATED/CALCULATED days are reminded, approximate ones say so, the day is clamped to the lunar month, and the leap flag is stored (§3.3) |
| "Today" was UTC, so a giỗ was a day early until 07:00 | `common/util/VietnamTime` |
| Validation holes answered 500 (a `BETWEEN` with no end, a day with no month, year 0, a FAMILY event for no union) | 400 with a Vietnamese message |

- **A child's relation to a partner added later still defaults to `BIRTH`** (decided 2026-09-23). Filling in a
  stepfather therefore makes him every existing child's birth father until someone corrects the link. The common
  intent when a second partner is added is the other parent, so the default stays; the correction is the child-link
  edit in tier 2, not a guess here.
- **`version` is optional on the requests.** A form sends it; the GEDCOM import and an approved suggestion do not,
  because a suggestion is applied onto the person as they are now. Person updates bump it by two (the names are
  flushed twice), so a client must send back the version it was **given**, never compute one.

**Tier 2 — the edit UI that §8 P1 claimed** (2026-09-24). 298 backend tests, 31 HTTP checks with two roles, every
new screen and dialog screenshot-checked, and one Playwright pass that saves through the UI and reads the rows back.

| Was | Now |
|---|---|
| No screen could edit a person, or add, change or delete an event | `/persons/:id/edit` (names, sex, chi, notes, change note, version); an event list with add/edit/delete on the person page and on each union |
| Unions and child links could only be created | Each union has edit (status, order) and delete; each child has "sửa liên kết" (relation to each partner, birth order) and "gỡ khỏi hôn nhân" — `PUT /families/{id}/children/{childId}` is new |
| Adding a spouse or child was three requests from the browser, `orderIndex` counted by the client | `POST /persons/{id}/relations`, one transaction; a CHILD into a union that is not the person's is refused before anyone is created |
| A failed query read as "không tìm thấy" or an empty list | Its own error state; only a 404 says the person does not exist |
| "Còn sống / Đã mất" came from the privacy flag | "Đã mất" only for a recorded death or burial (`deathRecorded` on search rows), "Hẳn đã mất" for the 100-year presumption, "Chưa ghi ngày mất" otherwise |
| No delete said why; DELETE child answered 200 with a body | Every delete asks for a reason, sent as `?changeNote=` into the revision; DELETE child is 204 |
| Forms were `useState`; three name fields had no label | React Hook Form + Zod (`buildXxxSchema(t)`), a label on every field |
| `family.unionN` read "Đời thứ N"; English messages from person, family and event; "gia pha" in `en.json` | "Hôn nhân thứ N"; Vietnamese messages; "gia phả" |

- **Partners are not editable in the union dialog.** Pointing a union at someone else is a merge or a new union,
  not a correction, and the partner-change path on the server (cycle check, relation swap) stays for the import.
- **The date field starts from the raw text, or the server's rendering when an import left none**, so editing an
  event never silently blanks its date.

**Tier 3 — the trail** (2026-09-24). A merge moved a ngày mất, folded a marriage away and deleted a union's
wedding, and a purge deleted a cụ's events, all without one revision. `EventService.reassignPerson`,
`reassignFamily` and `forgetSubject` now take the actor and the reason and record every event they move or delete;
`FamilyService.reassignPerson` snapshots every union either person touches and records each one that was deleted
or changed. 300 backend tests; 8 HTTP checks read the revisions back after a real merge and a real purge.

- **Diffed, not instrumented.** The union side compares a before and an after snapshot instead of recording inside
  each branch of the merge, so a new branch cannot forget to write its revision.
- ⚠️ **Out of scope and still unaudited:** `source`, `media`, `grave` and `suggestion` record no revisions at all —
  their `reassign*` and `forget*` included. That is the same §3.8 gap, in features mẻ 3 did not review. `source`
  and `grave` (and `place`) are audited since mẻ 5 (§8.8 #28), and `media` and `suggestion` since mẻ 6 (§8.9 #27–28).

**Tier 4 — efficiency** (2026-09-24). 304 backend tests; 10 HTTP checks on the name rewrite, and the tier 1–3 HTTP
suites re-run green on the same build.

| Was | Now |
|---|---|
| An import recomputed đời twice per FAM record | `FamilyService.importUnion`, and one recompute for the file (the measured figures are under §8.3) |
| `findByPerson` fetched each union's children with its own query | One query for all of them |
| Saving a person deleted and re-inserted every name, so every name had a new id after every save | Names are updated in place by position; only added or removed rows are inserted or deleted |
| A merge's cycle guard loaded the whole parentage graph twice | `inOneLineOfDescent` loads it once for both directions |

- ⚠️ **Updating names in place made a name-only edit invisible to `@Version`.** A `PersonName` is its own row, so
  changing one leaves `Person` clean and its version unmoved — a stale form would then overwrite it unrefused.
  `update` now loads through `PersonRepository.findForEdit` (`PESSIMISTIC_WRITE`) and touches `updatedAt`, so every
  edit moves the version and two edits of one person cannot compare against a version the other is changing.
  Caught while writing the change, and pinned by a unit test and an HTTP check. ❌ Do not drop the touch.

**Tier 5 — structure and cleanups, in part** (2026-09-24). 305 backend tests, 24 frontend tests, and the four HTTP
suites above (108 checks) re-run green on the final build.

- **`AuditAction` moved to `common/model`**; person, family and event imported it from `audit.domain` (§4).
- **`FamilyService.involvesLiving` is the one union guard** (§9): event, media and source each had a copy of
  "no partners, or any partner living".
- **`ReassignResult`, `SharedUnion` and the new `ImportedUnion` moved to `family/dto/response`**; they cross
  feature boundaries, and §4 puts records that do in `dto`.
- **`PersonMapper.applyFields`** replaces the field copying `create` and `update` each did by hand (§4.1).
- **Diacritics** restored in 36 comments and two test names across `common/model`, `place/domain` and `family`;
  `EventType`'s constants carried `/** */`, which §4.1 forbids on enum constants. The first word-list scan caught
  17 of them; the rest turned up only by reading the files it had flagged and running a second, wider scan (§9).
- One shared `TYPING_DEBOUNCE_MS`; stale doc comments on `SharedUnion`, `Person.primaryName`, three domain
  `package-info`s, `PersonService.update` and `fetchPerson` corrected; two dead guards removed.

**The rest of tier 5** (2026-09-24). 318 backend tests, 28 frontend tests, 17 new HTTP checks with two roles, the
four suites above re-run green, and the history panels screenshot-checked.

| Was | Now |
|---|---|
| A union's and an event's change history was recorded and shown nowhere | A "Lịch sử" toggle on every union and every event (EDITOR+), fetched only when opened; the summary reads each kind's payload — "Kết hôn 1920 → Kết hôn khoảng 1921", "Kết hôn, 6 con → Đã ly hôn, 6 con" |
| Every `PersonLink` fetched a whole `GET /persons/{id}`: a cụ with a wife and six children was seven requests | `GET /persons/nodes?ids=` (at most 200) and a batcher that sends every name asked for in one tick as one request — measured: seven names, one request |
| `toNode`, the union and child edges, `currentState`, the merge's name copy and the family `applyRequest` were mapped by hand | `PersonMapper` / `FamilyMapper` methods (§4.1) |
| Suggestion approval matched names exactly while merge compared the lowercased display, so " dam " and "Đảm" were two names to one and one to the other | One rule, `common/util/NameKey`: type and each part, trimmed, spacing collapsed, case ignored, **diacritics kept** — "Ánh" and "Anh" are different names |
| No test pinned what `/families`, `/persons/nodes` or the anniversary list hand a MEMBER, and the anniversary logic had no test at all | `FamilyShapeTest` (a node is never wider than the redacted view), `EventAnniversaryTest` (lunar, solar, bounds, leap, a deleted person, the shape) |

- **`/persons/nodes` is open to every role** because a node carries only what `PersonRedactedResponse` does — a
  test asserts exactly that, so a field added to the node without adding it to the redacted view fails the build.
- **`reassign*` and `drop*` do not recompute đời themselves** (#36, decided 2026-09-24): `merge` and `purge` call
  `recomputeGenerations()` once at the end, after every row has moved; recomputing inside each step would redo the
  whole graph three times per merge, on states that are not yet the final one.
- Every overlong line outside the vendored `shared/ui` is fixed repo-wide, including two in `media` and `tree`.

**Mẻ 3 is closed.** All 47 defects are fixed or recorded as a deliberate decision. At close: 318 backend unit tests,
28 frontend tests, type-check and lint clean, the backend booted on the final wiring, and 125 HTTP checks across
five suites green against it, with every test row removed after. ❌ Do not read that as "`person`, `family` and
`event` are correct" — read it as "eleven angles looked, and what they found was dealt with". The same families of
defect outside its scope are listed just below.

Outside mẻ 3's scope, the same family of defect as its English error messages was fixed later: `branch` in mẻ 4
(§8.7), `grave`, `place` and `source` in mẻ 5 (§8.8), and `auth`, `member` and `tree` in mẻ 7 (§8.10 #18).

### 8.7 The review of 2026-09-25 — `branch`, `audit`, `purge`

Mẻ 4 of the sweep, and the last of the packages `docker compose`'s own services touch: what is left unreviewed
after this is code nothing in the running stack calls at request time (`common/util`, `common/exception`, config,
migrations) or features never in this sweep's plan (`place`, `source`, `grave`, `suggestion`'s frontend). All
eleven angles reported, each recorded as it came back rather than at the end: 169 raw findings, **51 distinct
defects**. Closed the same day.

Tier 1 at close: 350 backend unit tests, frontend type-check/lint/vitest/build clean, the backend booted on the
final wiring, and HTTP checks with two roles against the running stack, with every test row removed after.

> ⚠️ **A grave's own photos, and an event's own citations, orphaned silently and for ever.** `graves` cascades away
> when its person is deleted (`V4`), and `media`/`citations` name a grave or an event by id with no foreign key —
> exactly the §8.1 defect `purge` was built to close for `person` and `family`, just never extended to the two
> rows a person and a union cascade into. Fixed by reading the id **before** the cascading delete and forgetting
> its rows first: `purgePerson` now looks up the grave via `GraveService.idOf` before deleting the person, and both
> `purgePerson` and `purgeUnion` read each subject's event ids before `forgetSubject` and forget each one's
> citations by id. A single event delete went through `EventService.delete` directly with nobody to forget its
> citations at all; `DELETE /api/v1/events/{id}` now goes through the new `PurgeService.purgeEvent`, the same way
> union delete already goes through `purgeUnion`. `MergeServiceImpl` had the identical gap in both directions —
> `graveService.reassignPerson` dropping a grave, and `followTheUnions` dropping a union — and is fixed the same
> way. Proved live: uploaded a photo to a throwaway grave, fetched its signed URL (200), deleted the person, and
> the same URL and the `media` row were both gone.

> ⚠️ **Deleting a chi silently unrooted everyone in it.** `branches.parent_id` and `persons.branch_id` were both
> `ON DELETE SET NULL` (`V3`), so deleting a chi that still had members or sub-branches gave every member "no chi"
> and turned every sub-branch into a root — with no revision, because branch writes were not audited at all. `V10`
> drops both `SET NULL`s (delete now refuses instead, backstopped by the FK), adds `branches.version` for
> optimistic locking, and adds `uq_branches_sibling_name` so two chi under one parent can no longer share a name
> and become indistinguishable in a picker. `BranchService.create/update/delete` all take an `actorId` and record
> through `AuditService`, the same as `person`/`family`/`event`; a PostgreSQL advisory lock (`TREE_LOCK_KEY`,
> distinct from the parentage lock) serialises concurrent reparenting the same way §8.6's parentage lock does.
> `PurgeService.purgeBranch` is the one path a chi is deleted through — it checks `PersonService.countInBranch`
> (member count) before `BranchService.delete` checks its own sub-branch count, and reports whichever blocks first.

> ⚠️ **`/revisions` with an id and no type fell through to the whole clan's feed**, and a MEMBER was blocked only
> by the route matcher — the service itself trusted every caller. `AuditService.findForEntity`/`search` now take
> the caller's role and refuse below EDITOR themselves (§3.6's pattern: a matcher is not the guard, the service
> is), `AuditController` 400s an id given with no type, and every list is `changedAt DESC, id DESC` regardless of
> what the request asked for — a client sort on an unmodelled field used to 500. `entityType` was a free string
> written from three copy-pasted constants (`"person"`, `"family"`, `"event"`); it is `AuditEntityType` now
> (`common/model`, joined by `BRANCH`), and `Revision`/`RevisionResponse` carry the enum, not a string a fourth
> feature could misspell. `RevisionMapper` replaces the hand-written `RevisionResponse` construction (§4.1).

The rest of tier 1, each screenshot-checked on the frontend:

| Was | Now |
|---|---|
| Deleting a union with children was not refused, unlike deleting its sole parent or merging one away | `purgeUnion` refuses when `FamilyService.childCount` is more than zero, naming the count |
| The revision toggle on a union or event fetched only the first page of 20 and had no way to see more | `RevisionList` pages, with a "Xem thêm" button; the whole-clan page below paginates the same way |
| There was no view of the trail across the whole gia phả — only per-record toggles, EDITOR+ could not audit at all | `/revisions`, "Nhật ký thay đổi": filterable by kind and action, paginated, linked from the home page for `mayAudit` |
| There was no page to create, rename, reparent or delete a chi; the person form's picker was a flat, unindented list that could not tell two same-named branches apart | `/branches`: a tree table, indented by depth, with create/edit/delete; `BranchPicker` (used by the branch dialog, the person form and the search filter) renders each option's full ancestor path and excludes a branch's own subtree when picking its parent |
| Filtering search by chi matched that chi exactly, unlike a place filter which matches the whole subtree | `SearchServiceImpl` expands the chosen chi through `BranchService.findWithDescendants` before it reaches `PersonService.search`, the same shape place already used |
| Adding a spouse or child from the tree left the new person in no chi at all, however deep the tree was centred | `FamilyServiceImpl.addRelation` defaults the new person's `branchId` to `PersonService.branchIdOf` the anchor — still correctable afterward, the same deliberate default as a child's relation type (§8.6) |
| GEDCOM export/import carried no chi at all — a round trip silently dropped it | `_BRANCH`, an extension declared in `HEAD.SCHMA` like `_STYPE`; written as the chi's root-first path (`Chi Hai > Chi Hai - Phái Hai`, no fixed levels the way `PLAC` has); import resolves it through `BranchService.findOrCreatePath`, creating any level a foreign file names that the clan does not hold yet |
| `merge-dialog` and the person-detail delete handler invalidated every query a write could affect except `revisions` | Both add it; a merge or a delete now refreshes an open history panel instead of leaving it stale |
| The locale for a formatted date/time was picked ad hoc in three places, and the suggestion page hard-coded `vi-VN` inside an otherwise-translated UI | `shared/lib/format-moment` — `formatMoment`, `formatSolarDate`, `formatDateOnly` — reads the language from i18next once; the other three call sites now call it |
| `RevisionCardProps` duplicated the `RevisionList`/`RevisionToggle` props shape field for field | `RevisionCard` takes the exported `RevisionTarget` type instead |

- **Storage objects are deleted only after the transaction commits.** `MediaServiceImpl.delete`/`forgetTarget` used
  to call `StorageService.delete` inline; a rollback then left the row back in place with its file already gone.
  `deleteObjectsAfterCommit` registers a `TransactionSynchronization` and calls it from `afterCommit()`, falling
  back to an immediate delete when no transaction is active (so the existing unit test, which runs outside Spring,
  still exercises the real call). Pinned by a test that opens a synchronization scope by hand, per §4.2's "unit
  tests must not need a DB" — `TransactionSynchronizationManager` needs none.
- **Purging a person that does not exist is a 404, not a 400** (#17): consistent with every other by-id lookup off
  a path variable (`GET`/`PUT` already answered 404); a body-referenced id, as in a merge request, still 400s.
- **`branch` also picked up the same-word find as `person`/`family`/`event`.** The word "union" had slipped back
  into three Vietnamese messages in `family` and `merge` written after §8.6 closed; all now read "hôn nhân".

**Deliberate, not fixed:** the trail for `source`, `media`, `grave` and `suggestion` still records nothing (§8.6
flagged this as out of its scope; mẻ 4 closed the same gap for grave/event citation *cleanup*, which is a
different defect from grave/media/suggestion writes carrying no `actorId` at all — that one is still open). A
`suggestion.forgetTarget` deleting an already-reviewed suggestion about a purged person is kept as-is: the payload
is that person's own data, and §3.8's "the trail outlives its subject" is answered by the person's own revisions,
not by keeping a stranger's read of them around.

**Mẻ 4 is closed.** All 51 defects are fixed or recorded as a deliberate decision. ❌ As with every mẻ before it,
that is not a claim that `branch`, `audit` and `purge` are correct — it is a claim that eleven angles looked. The
fix code itself — `V10`, `BranchServiceImpl`, the rewritten `AuditServiceImpl`/`AuditController`, the purge/merge
changes, `MediaServiceImpl`'s deferred delete, and the new frontend (`branch/**`, `audit/pages/revision-page`,
`shared/lib/format-moment`) — has not itself been read by any of the eleven, the same as every previous mẻ's own
fixes (§8.5).

### 8.8 The review of 2026-09-25 — `place`, `source`, `grave`

Mẻ 5 of the sweep. All eleven angles reported, each recorded as it came back: 147 raw findings, **49 distinct
defects**. **Closed 2026-09-28** — the closing paragraph at the end of this section says what that does and does not
mean. This section was written before any fix, on purpose (the user asked for it on 2026-09-25), so the decisions
below are the plan the fixes followed; the ones taken while building are listed after the table.

**Decided by the user (2026-09-25):**

- **D1 — place model: add levels, not a name history.** A new migration adds `VILLAGE` (thôn / làng, where a gia
  phả's quê quán usually sits) and `OTHER` (tổng, phủ, trấn and any unit the modern hierarchy lacks) to
  `PlaceType`. No `place_name` history table. A rename or move is recorded in the audit trail instead (D-audit
  below), so "why does cụ's birthplace say Hà Nội" is answerable from the trail.
- **D2 — graves: a cải táng event and a sinh phần flag.** `EventType.REBURIAL` (cải táng, a PERSON event with its
  own date and place) carries each move; the grave row keeps only where the grave is **now**. `graves.kind` is
  `GRAVE` (mộ) or `LIVING_PLOT` (sinh phần). A recorded `GRAVE` counts as a recorded death: `living` treats it like
  a BURIAL, and `findRecordedDead` includes it (so "Đã mất" and † follow). A `LIVING_PLOT` counts for nothing and
  stays private while its owner lives.
- **D3 — sources are EDITOR+ to read.** `GET /sources` and `GET /sources/{id}` move to EDITOR+, at the matcher
  **and** in the service (§3.6: the matcher is not the guard). A MEMBER still sees a citation, source title
  included, through `/citations`, which already fails closed on a living target.
- **D4 — the missing UI is all in scope:** a place management page (tree, create / edit / move / delete, a picker
  that shows the full path and can create a place inline); a source management page (every field, delete, merge
  two sources, a searchable picker, source + citation created in one request); citations on every event, union
  and grave (with `GRAVE` added as a citation target, citation edit, delete with a reason); and on a grave, delete
  (through `purge`), photos (`MediaCard` for `GRAVE`) and a place picker. The tảo mộ map stays out of scope.

**Decided by default while writing this plan** (the same shape as decisions the user already made elsewhere —
change them here, before the code, if they are wrong):

- **Delete refuses while anything references the record**: a place with events, graves or child places; a source
  with citations (merge it into another instead). The same rule as `purgeBranch` (§8.7). No `ON DELETE SET NULL`
  is left to fire silently: the migration drops those FKs' `SET NULL` the way `V10` did for branches.
- **Citation delete stays ADMIN-only** (repo-wide: every DELETE is ADMIN); the button is gated on `mayDelete` and
  asks for a reason. Citation edit (`PUT /citations/{id}`) is EDITOR+ like every other edit.
- **D-audit**: place, source, citation and grave writes all take an `actorId` and a `changeNote` and record through
  `AuditService` (`AuditEntityType` gains `PLACE`, `SOURCE`, `CITATION`, `GRAVE`). Places, sources, citations and
  graves gain `@Version`, checked through `StaleEdit`.
- **Grave → living without a bean cycle.** `LivingRule` lives in `event`, which may read `grave` (grave depends only
  on `person` and `place`), but a grave write cannot call `event` back. So `GraveServiceImpl` publishes a
  `GraveChanged(personId)` application event and `EventServiceImpl` recomputes `living` on it before commit; the
  nightly `LivingRefresh` sweep covers anything missed.
- **GEDCOM**: event citations are written as `SOUR` under each event node and read back from there. Place levels
  ride in the standard `PLAC.FORM` (no extension needed) and the most specific place's coordinates in `PLAC.MAP`.
  A grave is a declared `_GRAVE` extension under INDI (kind, plot, `PLAC`, `MAP`, `NOTE`); `REBURIAL` is
  `EVEN` + `TYPE REBURIAL`. On import, a path with no `FORM` gets positional levels only when it has exactly four
  components; otherwise the unknown levels are `OTHER`, never a guessed `COUNTRY`. A bare top-level name first
  matches an existing place of that name (accent-insensitive) before one is created, under the place-tree lock.
- **Import length limits**: the importer truncates every field to its request's `@Size` before calling a service,
  the same as it already does for names, and `addCitation` maps only `uq_citations` to "đã trích dẫn".

**The 49 defects** (full text and angles in the session scratchpad `fixlist-place-source-grave.md`):

| # | Defect | Tier | State |
|---|---|---|---|
| 1 | Grave lat/lng typed `string` on the frontend, sent as numbers: editing a located grave crashes; 0 read as missing; a pasted "21.02, 105.80" pair or a decimal comma 400s untranslated | 1 | fixed — numbers end to end; `shared/lib/coordinates` reads a decimal comma and a pasted pair; `coordinateText(0)` is "0" |
| 2 | The grave form never sends `placeId` and the mapper copies nulls: every UI save erases the grave's place | 1 | fixed — the form sends every field, `PlacePicker` included; HTTP-checked |
| 3 | `DELETE /persons/{id}/grave` leaves the grave's photos (rows and objects) orphaned | 1 | fixed — `PurgeService.purgeGrave` forgets media and citations first; HTTP-checked with a real upload |
| 4 | Import: an over-long field raises `DataIntegrityViolation`, which escapes `catch (ApiException)` — import dies midway, wrong 409, warnings lost, đời maybe not recomputed; `placeOf`/`branchOf` outside the try; `PUBL` falls back to `TEXT` | 1 | fixed — fields cut to each request's `MAX_*` (`common/util/TextCut`), both exceptions caught, lookups inside the try, `TEXT` goes to the notes |
| 5 | `addCitation` reports every integrity violation as "đã trích dẫn" | 1 | fixed — checked first (`findColliding`), and only `uq_citations` maps to it |
| 6 | Deleting a place is unguarded: events, graves and child places silently lose it, no revision | 1 | fixed — `V11` drops the `SET NULL`s; `PurgeService.purgePlace` refuses with both counts; audited |
| 7 | Place cycle check races without a lock, and the descendants CTE is `UNION ALL`: one cycle hangs every search on it | 1 | fixed — `AdvisoryLock.PLACE_TREE`, and `UNION` |
| 8 | Merge drops colliding citations with their quote, unreported; its key treats null and "" as equal, the DB does not | 1 | fixed — a collision is folded into the kept citation with both quotes, and named in the merge notes; locators are stored blank-as-null |
| 9 | GEDCOM loses event citations (both ways), every grave, and each place's level and coordinates | 1 | fixed — `SOUR` under events, `_GRAVE`, `PLAC.FORM`/`MAP`, `EVEN TYPE REBURIAL`; a round-trip unit test asserts each |
| 10 | `findOrCreatePath` guesses levels by position (a short path makes a tỉnh a COUNTRY), duplicates a bare "Hà Nội", and has no lock | 1 | fixed — as planned above; unit-tested |
| 11 | `addCitation` never checks its target exists | 1 | fixed — per target type, 400 in Vietnamese |
| 12 | No UI can create, edit or delete a place | 2 | fixed — `/places`, a tree table with create / edit / move / delete |
| 13 | Places are picked and shown by name only, never their path | 2 | fixed — `PlaceResponse.path`; `PlaceName` renders it, the picker lists it |
| 14 | Citations can be entered and seen on a person only — not on an event, a union or a grave | 2 | fixed — `CitationToggle` on every event and union, `CitationList` on the grave |
| 15 | Sources: no management UI, the picker shows the first 20 with no search, typing a title always creates a duplicate, no merge | 2 | fixed — `/sources`, a searchable `SourcePicker`, `POST /sources/merge` (ADMIN) |
| 16 | Source then citation are two requests (orphan source on every retry); locator has no `maxLength`; the picker cannot go back to "new source" | 2 | fixed — `CitationRequest.newSource`, one transaction; `maxLength`; "Nguồn mới" is always the first option |
| 17 | Citation remove shows to every role, asks nothing; a citation cannot be edited | 2 | fixed — `mayDelete` + `ConfirmDeleteDialog`; `PUT /citations/{id}` |
| 18 | Grave: no delete, no photos, no sinh phần flag, a recorded grave does not mean dead; `GET /graves` has no consumer and is N+1 | 2 | fixed — delete, `MediaCard` GRAVE, `graves.kind`, `GraveChanged` → `living`; `findLocated` reads living once. The endpoint keeps no consumer until the tảo mộ map (D4) |
| 19 | No cải táng history: an edit overwrites the old location | 2 | fixed — `EventType.REBURIAL`; the grave row is where it is now, and its revisions keep where it was |
| 20 | Place model has no thôn/làng or old-unit level | 2 | fixed — `VILLAGE`, `OTHER` (D1) |
| 21 | A grave (bia mộ) cannot be cited | 2 | fixed — `CitationTargetType.GRAVE` |
| 22 | Citation and grave cards are not keyed by person (form state crosses people); a fetch error reads as "none" and invites overwriting | 2 | fixed — keyed by person; each has its own error state, and the grave hides edit after a failed read |
| 23 | The book prints no places, graves or sources | 2 | fixed — scope decided below: a place after each date, a grave line, a closing list of sources |
| 24 | `GET /sources` hands a MEMBER every title, author and note (D3) | 3 | fixed — matcher and service; HTTP-checked with two roles |
| 25 | Place and source search `LIKE` is not escaped | 3 | fixed — `common/util/LikePattern` with `ESCAPE '\'`; `?query=%` HTTP-checked |
| 26 | `notes`/`quote` have no `@Size`; coordinates no `@Digits` | 3 | fixed |
| 27 | The grave living guard is written twice (grave, media); source uses `eventService.maySee(MEMBER)` as a living guard; `findLocated` checks per grave | 3 | fixed — `GraveService.involvesLiving` and `EventService.involvesLiving` are the one guards; `maySee` is gone |
| 28 | Place, source, citation and grave writes are unaudited | 4 | fixed — `AuditEntityType` `PLACE`/`SOURCE`/`CITATION`/`GRAVE`; the audit page and history summaries read them |
| 29 | Place, source and grave have no `@Version` | 4 | fixed — and citations; 409 on a stale form, HTTP-checked |
| 30 | `GET /sources` counts citations over the whole table for one page | 5 | fixed — only the page's ids |
| 31 | Import pays extra queries per source, citation and place level | 5 | fixed in part — each distinct PLAC line resolves once; a citation still pays its collision check, deliberate for a one-off import |
| 32 | `PlaceName` is one request per row; the picker queries on mount with no `staleTime` | 5 | fixed — `shared/lib/batcher` (the person batcher now uses it too); the picker fetches when opened or typed in |
| 33 | Event citations are forgotten one query per event | 5 | fixed — `SourceService.forgetTargets` |
| 34 | Export re-reads sources it does not use; its seven reads see different snapshots | 5 | fixed in part — one `REPEATABLE_READ` snapshot for all eight reads; the citations still load their source titles, one query |
| 35 | `idx_sources_type` unused; `IS NOT DISTINCT FROM` bypasses the parent index | 5 | fixed — dropped in `V11`; the parent lookups are two queries with `=` / `IS NULL` |
| 36 | Merge looks the grave up twice, and its dropped-grave note names only the plot | 5 | fixed — `DroppedGrave` carries the id; the note names plot, place and coordinates |
| 37 | Source mapping is hand-written; no `source/mapper` | 6 | fixed — `SourceMapper` |
| 38 | `gedcom/domain/GedcomTags` imports `source.domain.SourceType` (§4) — §8.3's "imports nothing from another feature's domain" was wrong | 6 | fixed — `SourceType` and `PlaceType` moved to `common/model`; §8.3 corrected |
| 39 | No test at all for the three packages, redaction included (§3.6, §4.2) | 6 | fixed — `PlaceServiceImplTest`, `SourceServiceImplTest`, `GraveServiceImplTest`, `EventGraveLivingTest`, the GEDCOM round trip |
| 40 | English messages: place ×3, source ×3, grave ×5 | 6 | fixed — none left in the three packages |
| 41 | Citation and grave forms are raw `useState`, not RHF + Zod | 6 | fixed — and the new place and source forms |
| 42 | Dead code: `findIdByName`, `findIdsByName`, `findByParentId`, unused imports, `MAP_ZOOM`, `Source.type` defaulted three times | 6 | fixed — `findIdsByName` is now used by the bare-name match; the type is defaulted in `SourceMapper` and the column |
| 43 | Stale or missing docs: `findAllSources` "gedcom and book", `@param role`, "CLAUDE.md 3.7", props docs, §8.3's `findIdByName`, `Place.parentId` "null for a country" | 6 | fixed |
| 44 | `GraveController` throws `NotFound` itself; the first `PUT` of a grave answers 200, not 201 | 6 | fixed — `getByPerson`, `GraveSaved.created` |
| 45 | Place and source search have no default order; a native sort key reaches SQL raw (500) | 6 | fixed — name / title order in the query; a client sort is ignored, HTTP-checked |
| 46 | Places accept an unpaired coordinate and a parent narrower than the child | 6 | fixed — `ck_places_coordinates_paired`, `PlaceType.fitsInside` |
| 47 | Shared helpers: the advisory-lock query (branch, family), `escapeLike`, `NOT_FOUND` ×3, `NO_PLACE`/`NO_BRANCH`, "place exists" ×3, path cleaning ×2 | 6 | fixed but one — `common/util/AdvisoryLock`, `LikePattern`, `HTTP_NOT_FOUND`, `NONE_VALUE`, `PlaceService.requireExists`; path cleaning stays two, below |
| 48 | Import order out of place after the enum moves | 6 | fixed — repo-wide, 20 files, scanned afterwards |
| 49 | Removing a citation does not refresh `['sources']` | 6 | fixed — `invalidateCitationQueries` |

**Decided while building (2026-09-26/28)** — not asked of the user, and each of the same shape as a decision already
made; change it here if it is wrong:

- **The book (#23)** prints each birth, death, burial and cải táng with its place's full path, one "Mộ phần:" or
  "Sinh phần:" line, and a closing "NGUỒN TƯ LIỆU" list. The book is EDITOR+ like the export, so a sinh phần is
  printed as the export already carries it.
- **A source merge is ADMIN-only**, at the matcher: it deletes the folded source, and every delete is ADMIN's.
- **A colliding citation is folded, not dropped** — on a source merge and on a person or union merge alike. The kept
  citation keeps its quote and gains the other's after a blank line; an identical quote is kept once.
- **`GET /places/all`** serves the place page's tree. A clan records hundreds of places, and a tree cannot be drawn
  one search page at a time; places are not person data, so it is open to every role like `GET /places`.
- **Path cleaning stays in two places** (#47): a `_BRANCH` path has no levels, while a `PLAC` path must keep its
  blank components until `FORM` has been aligned with them. One helper would have to take a flag saying which.
- **`flattenHierarchy`/`subtreeOf` moved to `shared/lib/hierarchy`** so the place page and the chi page share one
  tree walk; the place feature must not import the branch feature's `lib` (§6).

**Mẻ 5 is closed.** All 49 defects are fixed, or fixed in part with the remainder recorded above as deliberate. At
close: 405 backend unit tests, 36 frontend tests, type-check, lint and build clean, the backend booted on the final
wiring with `V11` applied, 64 HTTP checks with two roles against the running stack (63 on the first run; the one
that failed was PowerShell misreading `psql`'s Vietnamese output, §9, and the same assertion passed through the
API), and the place page, source page, merge dialog, citation dialog, event and union citations, and grave view and
edit form screenshot-checked. Every test row was removed afterwards. ❌ As with every mẻ before it, that is not a
claim that `place`, `source` and `grave` are correct; the fix code has not itself been read by any of the eleven
angles (§8.5).

Deleted files during the fixes: `source/domain/SourceType.java` and `place/domain/PlaceType.java`, which moved to
`common/model`, and `frontend/src/features/branch/lib/hierarchy.ts`, which moved to `shared/lib/hierarchy.ts`.

**Outside this mẻ, since closed:** the English messages in `auth`, `member` and `tree` (§8.6) by mẻ 7 (§8.10 #18),
and the `media` and `suggestion` trail by mẻ 6 (§8.9).

### 8.9 The review of 2026-09-28 — `media`, `suggestion`

Mẻ 6 of the sweep, the first full review of either package since 2026-09-16 and the first ever of the `suggestion`
frontend. All eleven angles reported, each recorded as it came back: 150 raw findings, **44 distinct defects**.
**Closed 2026-09-29** — the closing paragraph at the end of this section says what that does and does not mean. This
section was written before any fix, on purpose, like §8.8, so the decisions below are the plan the fixes followed.

**Decided by the user (2026-09-28):**

- **D1 — suggestions: edit a person, with a before/after comparison.** A con cháu can propose a person's names, sex
  and birth/death dates, or a new person anchored to a parent or a spouse (applied through `addRelation`, so they
  get a union and a đời). A MEMBER may not propose chi or notes: the service refuses them, because a MEMBER cannot
  read a living person's notes and must not blind-overwrite them (§3.6). The reviewer sees a field-by-field diff
  of the current state against what approval will write, served by the backend from the same `merged()` it
  applies, and every field approval writes is on that diff. NOTE stays, and approving one says "đã ghi nhận",
  not "đã áp dụng".
- **D2 — scans of the old gia phả hang off a source.** `MediaTargetType` gains `SOURCE` (new migration), so the
  80 A3 pages sit on the "Gia phả cũ" source, are shown on the source page and reachable from its citations, and
  a merge or purge of a person no longer drags them along. A source merge moves them; a source delete is already
  refused while cited, and is refused while it has media too.
- **D3 — thumbnails as a size variant.** `StorageService.resolveUrl` takes a size (`THUMBNAIL` | `ORIGINAL`),
  with no provider type leaking through (§3.9): Cloudinary answers with a transform URL, MinIO with a thumbnail
  generated and stored at upload. Galleries use the thumbnail, a viewer opens the original, and the book embeds
  a size-limited copy.
- **D4 — deleting a file asks, takes a reason, and is audited.** `ConfirmDeleteDialog`, `?changeNote=`, and an
  `AuditEntityType.MEDIA` revision; the object is still deleted after commit. No soft delete.

**Decided by default while writing this plan** (same shape as decisions already made; change them here, before
the code, if they are wrong):

- **D-audit**: media and suggestion writes take an `actorId` (and a `changeNote` for media edits and deletes) and
  record through `AuditService` (`AuditEntityType` gains `MEDIA`, `SUGGESTION`). `media` and `suggestions` gain
  `@Version`; a review is a conditional `UPDATE … WHERE status = 'PENDING'` whose row count decides the winner,
  the same way `revokeIfLive` does (§8.2), so a second reviewer gets a 409.
- **The upload leaves no orphan**: the network upload runs before a short transaction, and an
  `afterCompletion(STATUS_ROLLED_BACK)` hook deletes the object if the row never commits — the mirror of mẻ 4's
  delete-after-commit.
- **The file type is read from the bytes**, not the header: JPEG, PNG, WebP, GIF, HEIC, PDF by magic number;
  `image/jpg` is accepted as JPEG; Cloudinary gets an explicit `resource_type`. A PORTRAIT must be JPEG, PNG or
  GIF (what browsers and iText both render), and only a PERSON has one.
- **Storage faults are a 5xx with a fixed Vietnamese message**, the cause logged, never the SDK's text.
- **Merge**: a pending UPDATE on the duplicate becomes a NOTE on the survivor ("đề xuất về bản trùng đã gộp"),
  an approved CREATE is left alone, and the merge reports suggestions moved and FAMILY media moved.
- **Out of scope, recorded**: GEDCOM `OBJE` export of media (#25), attaching a photo to a suggestion (#26), and
  notifications beyond a pending-count badge for EDITOR+ and a status on the member's own list (#17).

**The 44 defects** (full text and angles in the session scratchpad `fixlist-media-suggestion.md`):

| # | Defect | Tier | State |
|---|---|---|---|
| 1 | Approval writes sex, chi, notes (`""` wipes them) and primary flags the reviewer never sees; a MEMBER can overwrite notes they cannot read | 1 | fixed — `SuggestedPerson` has no chi or notes; approval keeps the person's own; HTTP-checked with two roles |
| 2 | `review` is check-then-act with no lock or version: two approvals create two people; approve racing reject leaves status and data disagreeing; a stale review rewrites a merged `target_id` | 1 | fixed — `SuggestionRepository.claim`, a conditional UPDATE; two concurrent approvals over HTTP gave 200 and 409, one birth written |
| 3 | `SuggestCard` is not keyed by person: a note typed on A is filed against B | 1 | fixed |
| 4 | Deleting a file is one click, with no confirmation, reason, actor or revision | 1 | fixed — `ConfirmDeleteDialog`, `?changeNote=`, a `MEDIA` DELETE revision (D4) |
| 5 | An object whose row never commits (two concurrent portraits, a rollback) is orphaned for ever | 1 | fixed — the row is written in its own `TransactionTemplate` after the upload, and both keys are deleted if it fails; a lost portrait race is a 409 |
| 6 | Merge moves a pending UPDATE onto the survivor, whose primary name and sex it then replaces; an approved CREATE is repointed too | 1 | fixed — turned into a NOTE that quotes the proposal; approved CREATEs stay; pending CREATEs are re-anchored |
| 7 | A proposed name with no primary flag is added as an alternate, against `PersonRequest`'s "the first is primary" | 1 | fixed — `withPrimary`; the replaced primary is kept as an alternate |
| 8 | Upload trusts the client's Content-Type: mislabelled files are stored, an SVG can run script on the CDN, a Windows `.heic` or `image/jpg` is refused | 1 | fixed — `FileTypes` reads the magic number; an `.exe` and an SVG labelled `image/png` are 400 over HTTP |
| 9 | HEIC/WebP/PDF portraits cannot be shown by browsers or embedded by the book, which drops them silently | 1 | fixed — a portrait is JPEG, PNG or GIF on a PERSON (`ck_media_portrait_person`, `V12`) |
| 10 | Storage faults answer 400 with the SDK's raw message (internal host, bucket) — to a MEMBER on a GET too; Cloudinary double-wraps its own error | 1 | fixed — `ServiceUnavailableException`, a fixed Vietnamese message, the cause logged |
| 11 | The queue shows only the first 20; client sort reaches the derived query, no size cap, no `id` tie-break | 1 | fixed — one JPQL `search` ordered by `createdAt, id`; `common/web/PageRequests` caps at 100; the page pages |
| 12 | One unreadable payload makes the whole `GET /suggestions` page a 400, and that suggestion can never be reviewed | 1 | fixed — a tolerant read flags `payloadReadable: false`; it can still be rejected |
| 13 | Cloudinary `download` has no timeout: `/book` can hang a thread for ever | 1 | fixed — 20 s connect and read timeouts; not run against Cloudinary, which needs credentials |
| 14 | The UI sends only NOTE; approving a NOTE says "applied"; the target is not a link | 2 | fixed — NOTE / UPDATE / CREATE; a NOTE is "Ghi nhận"; the target links to the person |
| 15 | A suggestion carries a person's own fields only; CREATE makes a person in no union | 2 | fixed — birth and death dates; a CREATE carries an anchor and is applied through `addRelation` |
| 16 | The reviewer sees no before/after | 2 | fixed — `GET /suggestions/{id}/preview` (EDITOR+), from the same `merged()` approval writes |
| 17 | No notification: `pending-count` has no caller, a member never learns the outcome | 2 | fixed in part — a badge on the home page and the status on the member's own list; no push, as planned |
| 18 | Scans of the old gia phả have no home (D2) | 2 | fixed — `MediaTargetType.SOURCE`, on the source page and each citation (EDITOR+); moved by a source merge, refused on delete |
| 19 | Kind, caption and order cannot be set from the UI; caption travels in the query string; a blank caption is stored | 2 | fixed — an edit dialog; every field in the multipart body; blank is null |
| 20 | An image cannot be seen large (320 px `object-cover`, not clickable) | 2 | fixed — a viewer with the original and a link to it |
| 21 | No `MediaCard` for a union | 2 | fixed — `MediaToggle` on every union |
| 22 | The hint says the portrait shows on the tree (nothing reads it there); "make portrait" is offered on union and grave files | 2 | fixed — the hint says page and book; the portrait is the edit dialog's kind, offered on a person's image only |
| 23 | One file per upload | 2 | fixed — several files, uploaded one at a time with a count |
| 24 | Who uploaded a file, and when, is not shown | 2 | fixed |
| 25 | Media is absent from the GEDCOM export and has no orphan report | 2 | open — out of scope, see above |
| 26 | A con cháu cannot attach a photo to a suggestion | 2 | open — out of scope, see above |
| 27 | Media writes: no actor, no revision, no `@Version` | 2 | fixed — `AuditEntityType.MEDIA`, a snapshot without the URL, `media.version` |
| 28 | Suggestion writes: no revision | 2 | fixed — `AuditEntityType.SUGGESTION` on create, review, move and delete |
| 29 | Galleries load the full original, no thumbnail (D3) | 3 | fixed — `ImageSize`; MinIO stores a 600 px JPEG, Cloudinary transforms; both URLs fetched over HTTP |
| 30 | The media query has no `staleTime`, every fetch re-signs every URL (cache misses); a page open over an hour shows expired links | 3 | fixed — 20 min `staleTime` and `refetchInterval`, inside the hour a URL lives |
| 31 | The book fetches portraits one by one, full size, and builds the PDF in memory | 3 | fixed in part — the small copy; still one by one and in memory |
| 32 | The upload's network call runs inside the DB transaction, holding a connection and a row lock | 3 | fixed — `NOT_SUPPORTED`, and a short transaction for the row |
| 33 | `nextSortOrder` loads every row; `getBytes()` copies the file; objects deleted one call at a time; `forgetTarget` once per union | 3 | fixed in part — `findMaxSortOrder` and a stream; objects are still deleted one call each |
| 34 | Invalidation: a review refreshes tree/quality even when nothing was written; the merge dialog skips media/grave/citations/suggestions; sending a suggestion refreshes nothing | 3 | fixed — `invalidateAfterReview` |
| 35 | Suggestion indexes do not match the queries issued | 3 | fixed — `V12`, `(status, created_at DESC, id DESC)` and the per-owner one |
| 36 | "Target exists" written two ways (media 404 + enum name, source 400 + Vietnamese); no `FamilyService.exists` or `PersonService.requireExists` | 4 | fixed — both added; media answers 400 with a Vietnamese noun |
| 37 | "A MEMBER sees only their own" written three times; four finders and two counts for one query | 4 | fixed — `ownerScope`, one `search`, one `countInState` |
| 38 | Media and suggestion mapping is hand-written, no MapStruct | 4 | fixed — `MediaMapper`, `SuggestionMapper` |
| 39 | The suggestion page re-derives the review role (and `data-page.tsx:98` the import role) instead of `usePermissions` | 4 | fixed — `mayReview`, and a new `mayImport` |
| 40 | `review` has no role check in the service | 4 | fixed — and `preview`; a MEMBER calling either is 403 over HTTP |
| 41 | Dead code: a media finder with a stray doc, `GET /suggestions/{id}` with no UI, unused imports, redundant `setStatus`/`save`, the payload parsed twice, unused i18n and api parameters | 5 | fixed but one — `GET /suggestions/{id}` stays, documented as having no UI yet |
| 42 | Docs: missing `@param role`/`memberId`, "EDIT", a contradicting comment, undocumented props, a WHY in the wrong component | 5 | fixed |
| 43 | Frontend: `useState` forms, an unlabelled field, failed reads shown as empty, redundant `cursor-pointer`, colliding React keys, no `MEDIA_QUERY_KEY`, a loose `accept` | 5 | fixed |
| 44 | Tests and limits: one-directional redaction tests, a Cloudinary IT that never fetches the URL, no `@Size` on message/reviewNote/caption, unstripped `reviewNote`, Cloudinary `resource_type` on destroy/url, `max-swallow-size`, an unsanitised key extension | 5 | fixed — the IT uploads a real PNG and fetches both URLs, but has not been run: no credentials here |

**Decided while building (2026-09-29)** — not asked of the user; change it here if it is wrong:

- **A MEMBER's chi or notes are dropped, not refused.** `SuggestedPerson` has no such field, and Boot's mapper
  ignores an unknown one, so a hand-written request carrying `notes` is stored without it. Nothing can apply it.
- **A proposed name must carry `primary`.** Jackson 3 refuses a missing `boolean` (`FAIL_ON_NULL_FOR_PRIMITIVES`),
  so a name without it is a 400 "Failed to read request"; the UI always sends it. `PersonNameRequest` is shared
  with every person form, so it was not loosened for this one caller.
- **An UPDATE with a date adds the event when none is recorded, and refuses when two are** — a contradiction for
  the reviewer to settle on the person page (§5.1), not for an approval to pick between.
- **"Make portrait" is no longer its own button**: the kind field of the edit dialog does it, because the three
  buttons of a tile wrapped unevenly and §6.3 puts edits to one record's fields in one dialog.
- **Old stored payloads are read with unknown fields ignored** (`readerFor(...).without(FAIL_ON_UNKNOWN_PROPERTIES)`),
  independent of Boot's configuration, so the 24 pending notes and the anchorless CREATE in a dev database still
  read. The anchorless CREATE can only be rejected: approving it is refused rather than placing a person nowhere.

**Mẻ 6 is closed.** 42 defects are fixed or fixed in part with the remainder recorded; #25 and #26 are out of scope
as planned. At close: 436 backend unit tests, 36 frontend tests, lint and build clean, the backend booted on the
final wiring with `V12` applied, 73 HTTP checks with two roles against the running stack (upload type sniffing, the
413, thumbnails, SOURCE scans hidden from a MEMBER, a source merge moving them, stale edits, a concurrent double
approval, a CREATE landing in its union with a đời, a merge turning a pending edit into a note), and the gallery,
viewer, upload, edit and delete dialogs, the union and source galleries, the three suggestion forms, both review
comparisons and both queues screenshot-checked. Every test row was removed afterwards. ❌ As with every mẻ before
it, that is not a claim that `media` and `suggestion` are correct; the fix code has not itself been read by any of
the eleven angles (§8.5). The Cloudinary path — resource type, transform URLs, timeouts — compiles and has never
run.

### 8.10 The review of 2026-09-29 — `common`, migrations `V9`–`V12`

Mẻ 7 of the sweep: the shared kernel, whole, and the four migrations no review had read. All eleven angles reported;
seven were cut off by a rate limit mid-run and resumed with their context intact. 101 raw findings, **45 distinct
defects**. **Closed 2026-09-29** — the closing paragraph says what that does and does not mean. Written before any
fix, on purpose, like §8.8 and §8.9; where the building departed from the plan, it is listed after the table.

**Decided by the user (2026-09-29):**

- **D1 — a giỗ with no year is a valid date.** A lunar date may carry a month and a day with no year. A new
  migration relaxes `ck_events_date_precision` for `LUNAR` only; the service stops refusing it; `sort_date` stays
  null (§3.2's "no year at all" row); the anniversary list includes it and reminds it every year, with no năm thứ.
- **D2 — âm lịch before 1968 is computed at UTC+8.** A solar↔lunar conversion for a day before 1968-01-01 uses
  UTC+8, from that day on UTC+7 — the Northern convention, which gia phả follow (§5.3). ⚠️ **This changes the
  locked §3.3** at the user's explicit decision; §3.3 is rewritten with the fix, and the "no Chinese-lunar library"
  rule stands — a library that is UTC+8 for every year is still wrong for every year after 1967. Tests pin known
  pairs on both sides of Tết Mậu Thân 1968.
- **D3 — auth hardening, all four:** (a) the JWT filter re-reads the member's role, enabled flag and a credential
  version from the database (no cache), so disabling, demoting or resetting takes effect at once; (b) access
  tokens carry and require `iss`, `aud` and `typ`; (c) failed logins are throttled per email and per IP, in memory;
  (d) compose binds Postgres and MinIO to `127.0.0.1` with no default passwords, `/auth/refresh` and `/auth/logout`
  check `Origin`/`Sec-Fetch-Site`, and an expired refresh token is no longer treated as a replay.
- **D4 — can-chi is rendered, the rest is out of scope.** A lunar date with a year renders its can-chi year
  ("ngày 10 tháng 3 năm Canh Dần"), derived, never stored. Out of scope and recorded: can-chi or reign-era years as
  *input*, a chữ Hán/Nôm script axis on names, giờ (giờ Tý), EDITOR scoped to a chi, and a viewer-local "today".

**Decided by default while writing this plan** (change them here, before the code, if they are wrong):

- **Pre-1582 dates are proleptic Gregorian end to end.** `LocalDate` already is; the Julian branch in
  `julianDay`/`fromJulianDay` is removed rather than reconciled. A gia phả date from the 1400s is its lunar record;
  the solar day is only for ordering.
- **Repair migrations are forward only.** `V13` reclassifies pre-V11 graves whose owner was `living` as
  `LIVING_PLOT` (the reverse inference is the safe one: a mộ wrongly marked sinh phần only stays private), and sets
  `date_leap_month` from `date_raw` where it says "nhuận" and the year has that leap month. Applied migrations are
  not edited (#42, #43 stay recorded).
- **A constraint failure says what failed.** `DataIntegrityViolationException` is mapped by constraint name to a
  Vietnamese message; only a unique violation on a racing write says "có người khác vừa sửa". Lock and timeout
  failures are 409. Every validation message is Vietnamese (`ValidationMessages.properties` plus the field's
  Vietnamese label), never a Java path.
- **A lunar BETWEEN gets `date_leap_month2`** (`V13`), carried like the first endpoint's flag.
- **Leap-month giỗ stay in the ordinary month** (the custom; already the code's choice). `nextAnniversary` was left
  without the flag; a test pins the choice instead (#34).

**The 45 defects** (full text and angles in the session scratchpad `fixlist-common-migrations.md`):

| # | Defect | Tier | State |
|---|---|---|---|
| 1 | V11 backfilled every pre-V11 grave as `GRAVE`, so a sinh phần made its living owner "dead" and public; the GEDCOM kind read fails open the same way | 1 | fixed — `V13` reclassified 11 dev graves with a revision each; `graveKindOf` fails closed to `LIVING_PLOT` |
| 2 | Before 1582 `LunarCalendar` switches to the Julian formula while `LocalDate` is Gregorian: ~10 days off, and a Julian 29 February is a 500 on save and on `/quality/issues` | 1 | fixed — Gregorian throughout; a lunar date of 1500 saved over HTTP; `V14` re-derived every lunar `sort_date` |
| 3 | V9's leap flag was never backfilled from `date_raw`, so a pre-V9 leap-month giỗ is still a month early | 1 | fixed — `V14`; the dev database held no lunar event, so it restored none there |
| 4 | Merge and purge check cycles and shared unions before taking the parentage lock | 1 | fixed — the lock is the first thing both take; order pinned by tests |
| 5 | `updateChild` has no lock, no stale check and no version | 1 | fixed — the union's version stands for its links; a stale form is a 409 over HTTP |
| 6 | A lunar `BETWEEN`'s end is never leap; the leap suffix covers the whole range; `BETWEEN` prints "khoảng" like `ABOUT` | 1 | fixed — `date_leap_month2` (`V13`) through GEDCOM, the request and the text; "từ … đến …" |
| 7 | `NameKey` does not normalise to NFC; `TextCut` cuts between a letter and its tone mark and throws at 0 | 1 | fixed |
| 8 | Every integrity violation is a 409 "có người khác vừa sửa", however it happened | 1 | fixed — by SQLSTATE and constraint name (`ProblemMessages`); only unique and FK clashes are 409 |
| 9 | A stale grave form recreates a deleted grave; two first saves race on the unique owner | 1 | fixed in part — the stale recreate is a 409 over HTTP; the first-save race now says "đã có" rather than "người khác vừa sửa" |
| 10 | Role and enabled come from the token: a disable, demotion or reset waits up to 15 minutes (D3a) | 2 | fixed — `MemberAccessLookup` per request and `members.credential_version` (`V15`); a reset killed the old token at once over HTTP |
| 11 | No `iss`/`aud`/`typ` on the JWT (D3b) | 2 | fixed — a same-secret token without them is refused, tested |
| 12 | Login is not throttled (D3c) | 2 | fixed — `LoginThrottle`: 5 per email, 20 per address, 15 minutes; the sixth guess was 429 over HTTP |
| 13 | Cookie-only refresh/logout with CSRF off; replaying an old cookie revokes every session again and again (D3d) | 2 | fixed — `SameOriginGuard`; a merely expired token no longer revokes the rest |
| 14 | Postgres and MinIO on every interface with default passwords (D3d) | 2 | fixed — loopback only, no defaults in compose or `application.yml` |
| 15 | Integrity failures logged with row values; config records print secrets in `toString` | 2 | fixed — one line with state and constraint; masked `toString` |
| 16 | The JWT filter is registered twice and skips the ERROR dispatch, turning real errors into 401 | 2 | fixed — registration disabled, `DispatcherType.ERROR` permitted |
| 17 | Validation errors are English Java paths, a constraint violation leaks method names, a class-level error is blank | 3 | fixed — `ValidationMessages.properties` and a field-label table; "Tên: không được để trống" over HTTP |
| 18 | English 500, 401 and 403 details and titles | 3 | fixed — and Spring's own 404 and 405; the English messages in `auth`, `member` and `tree` too (§8.6) |
| 19 | Lock, deadlock and timeout failures are 500 | 3 | fixed |
| 20 | The cause of an internal or storage error is never logged; an expected 409 is logged with a full stack | 3 | fixed |
| 21 | JWT TTLs and the storage provider are not validated at boot | 3 | fixed |
| 22 | Person search is the one list that bypasses `PageRequests` | 4 | fixed — size=2000 answered 100 over HTTP |
| 23 | `(:x IS NULL OR …)` predicates likely keep the V9 and V12 indexes from being used | 4 | recorded — not measured; at a clan's size a scan is milliseconds, and splitting each query per filter combination is its own change |
| 24 | CHECK lists copied by hand from enums, with no test tying them | 4 | fixed — `CheckConstraintsMatchEnumsTest`, eighteen constraints |
| 25 | "Person exists" written seven ways, two of them English | 4 | fixed in part — one Vietnamese sentence everywhere; the call sites keep `exists` because 404 (path) and 400 (body) are both right where they are |
| 26 | Target-exists checks and Vietnamese noun maps written twice, nouns typed again in nine stale checks | 4 | fixed — `AuditEntityType.noun()`; `StaleEdit` takes the enum |
| 27 | The 409 sentence exists twice and has drifted | 4 | fixed — `ProblemMessages.RACED` |
| 28 | `changeNote` capped on 6 of ~19 declarations | 4 | fixed — `ChangeNotes.MAX_LENGTH` on every request and every `?changeNote=` |
| 29 | Five private blank-to-null helpers | 4 | fixed — `Blank.toNull` |
| 30 | `quality` reimplements `immutable_unaccent` in Java | 4 | fixed — moved to `common/util/SearchKey`, tested against "Đỗ Văn Đức" |
| 31 | The full-name SQL expression is written twice and treats `''` as a part | 4 | fixed in part — blank parts are stored as null by the mapper; the expression is still written twice (V9 is applied) |
| 32 | `JwtParser` built per request | 4 | fixed |
| 33 | No RFC 5987 `filename*` on downloads | 4 | fixed |
| 34 | `nextAnniversary` cannot take the leap flag | 4 | recorded — no change: the ordinary-month custom is pinned by a test instead of a flag nobody reads |
| 35 | Blanket write rules precede the reader-only paths in `SecurityConfig` | 4 | fixed |
| 36 | Dead paths in `GenealogyDate` and `LunarCalendar`, one of them a trap that reads lunar parts as solar | 5 | fixed |
| 37 | `GenealogyDateText`: three overloads, leap by string replace | 5 | fixed — one entry, `of(GenealogyDate)` |
| 38 | `initCause` after `super` in two exceptions | 5 | fixed |
| 39 | Visibility wider than needed; `LikePattern.escape` lets a caller skip `orNull` | 5 | fixed |
| 40 | Lunar and date tests do not pin a known leap pair, leap 11/12, the wrong-leap fallback, day 30, pre-1582 or leap rendering | 5 | fixed but one — no leap 11/12 pair: none this project could source with confidence |
| 41 | Conventions: `common/util` has no `package-info`, a stale JWT comment, `// Nu.`, a banner comment, constructor docs, enum spacing, import order, V12's header | 5 | fixed but one — V12's header stays: editing an applied migration changes its checksum |
| 42 | V10 and V11 add a unique and a CHECK without cleaning existing rows | — | recorded — applied, cannot edit |
| 43 | V12 demoted portraits with no revision; V10's UPDATE has no `WHERE` | — | recorded — applied, cannot edit |
| 44 | The parentage lock is global through a whole-graph recompute | — | recorded — deliberate (§8.6) |
| 45 | Downloads are built as `byte[]` | — | recorded — §8.9 #31 |

**Decided while building (2026-09-29)** — where the plan above was changed, and why:

- **No cache on the per-request account read.** One primary-key read per request is cheap for one clan, and a
  cache would reopen the window D3a closes. Every access token issued before `V15` is refused, so everyone signs
  in once more.
- **`V13` picks the sinh phần by what it can still see.** `living` had already been recomputed from the wrong kind,
  so the plan's "owner was living" could not be read; it takes graves recorded before `V11`, never saved since
  (version 0), whose owner has no death, burial or cải táng recorded.
- **The leap backfill is `V14`, a Java migration** (`src/main/java/db/migration`), not `V13`: which month of which
  year is a tháng nhuận is `LunarCalendar`'s to say. It also re-derives every lunar `sort_date`, which D2 and #2
  both moved.
- **The validation bundle is `ValidationMessages.properties`**, the base bundle, not `_vi`: the JVM's locale is
  English, and the base is what Hibernate Validator falls back to.
- **Compose requires `DB_PASSWORD`, `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD`** and refuses to start without
  them. Postgres is published on `127.0.0.1:${POSTGRES_PORT:-5433}`: this dev box runs a Windows Postgres on 5432.
- **The throttle counts `getRemoteAddr()`.** Behind a reverse proxy that is the proxy, so production needs
  `server.forward-headers-strategy` set before the per-address limit means anything.

**Mẻ 7 is closed.** 38 defects are fixed, 5 fixed in part or recorded with the reason, 2 recorded as applied
migrations. At close: 505 backend unit tests, frontend lint, build and 36 tests clean, the backend booted with
`V13`–`V15` applied, 39 HTTP checks with two roles (token binding, a reset ending a live token, the throttle,
origin checks, Vietnamese errors, a year-less giỗ in the anniversary list, a lunar date of 1500, a leap range end,
stale child links and graves, the page cap, loopback-only ports) and the 73 of §8.9 re-run green, and the date
field's year-less hint screenshot-checked. ❌ As with every mẻ before it, that is not a claim that `common` is
correct; the fix code — `LoginThrottle`, `SameOriginGuard`, `MemberAccessLookup`, `V13`–`V15`, the new handler —
has not itself been read by any of the eleven angles (§8.5).

### 8.11 The review of 2026-10-01/02 — the shell, the unread frontend, `auth`/`member`, build & deploy

Mẻ 8 of the sweep. Its scope: the app shell of §6.0, the frontend features no review had read (`home`, `gedcom`,
`book`, `auth`, `member`, `shared/**`), the B, D and E angles `auth`/`member` lacked, and the build and deploy
config. All eleven angles reported: four on 10-01, the other seven re-run whole on 10-02 after being stopped at the
user's request. 106 raw findings, **51 distinct defects**; one more (a download filename) was checked against
Spring 7.0.8 by two angles and is not a defect. Written before any fix, on purpose, like §8.8–§8.10.

**Decided by the user (2026-10-02).** The plan below was written with every decision as a default. The user
accepted D2–D8 as written and **deferred D1**: forcing the seeded ADMIN to change password is "not needed yet".
So #1 and the forced-change half of #27 stay open by decision. Nobody forgot them.

- **D1 — the seeded `Admin@123` (#1). Deferred by the user.** The plan was this: `V16` adds
  `members.must_change_password`. It is set for the seeded ADMIN while their hash is still the seed's, and by every
  ADMIN reset (#27). While it is set, the server answers 403 to everything except `/auth/*` and
  `/members/me/password`, and the UI opens "Đổi mật khẩu" and cannot dismiss it.
- **D2 — replaying a revoked token (#2).** `refresh_tokens` gains a `revoke_reason`: `ROTATED`, `LOGOUT` or
  `CREDENTIALS`. Only a replay of a `ROTATED` token is theft and revokes every session. A logged-out or
  pre-reset cookie is an ordinary 401, so an old laptop no longer signs the family out over and over.
- **D3 — throttling (#3–#5).**
  - Sign-in counts failures per **email + address pair**, plus a looser per-address limit, so a stranger cannot
    lock a con cháu out by typing their email.
  - The current-password check counts per **member id**, apart from sign-in.
  - The check and the count are one step.
  - `server.forward-headers-strategy: native` is documented in `.env.example` for a deployment behind a proxy.
- **D4 — account management (#19).** Endpoints and the `/members` page for create, disable/enable and change role,
  all ADMIN only, each audited (`AuditEntityType.MEMBER`, #13), and each ends the account's sessions. An ADMIN
  cannot disable or demote themselves, so the clan cannot be left with no ADMIN.
- **D5 — the tree in navigation (#20).** "Cây gia phả" goes in the sidebar. `/tree` with no id centres on the
  clan's founder, the person `GenerationCalculator` already picks as đời 1, served by a new `TreeService` method.
- **D6 — Chi and Nơi chốn for a MEMBER (#21).** Shown read-only: the server already lets every role read them, and
  neither is person data.
- **D7 — text size (#26).** A "Cỡ chữ" switch (normal / large) in the header, remembered in `localStorage` like the
  theme. Large scales the root font size, so every `rem` follows.
- **D8 — type-aware lint (#35).** `recommendedTypeChecked` turned on. Keeping that layer is the only reason §2 pins
  TypeScript 6.0.3.
- **Out of scope, recorded (#28):**
  - a production deployment path, HTTPS and the Cloudinary prod run, which need a host and credentials this
    project does not have;
  - backup, beyond a documented `pg_dump` command;
  - running CI, which needs the repository on GitHub.

  MinIO links that only open on the host are fixed by `MINIO_PUBLIC_ENDPOINT` documentation, not by code.

**The 51 defects** (full text and angles in the session scratchpad `fixlist-mei8.md`):

| # | Defect | Tier | State |
|---|---|---|---|
| 1 | The seeded ADMIN `Admin@123` works in every environment and is never forced to change | 1 | deferred — D1, by the user |
| 2 | Replaying a token revoked by a logout or a password change revokes every session, again and again | 1 | fixed — `refresh_tokens.revoke_reason` (`V16`); only a `ROTATED` replay ends every session; HTTP-checked |
| 3 | Anyone can lock an account out by its email, current-password changes included | 1 | fixed — keyed on email + address; the password check per member id; HTTP-checked |
| 4 | The throttle checks before it records, so concurrent sign-ins all get through | 1 | fixed — one `compute` per key reserves the guess; 50 concurrent guesses admit exactly 5 (`LoginThrottleTest`) |
| 5 | Behind a proxy the per-address limit counts the proxy and locks out the whole clan | 1 | fixed in part — `SERVER_FORWARD_HEADERS_STRATEGY` documented in `.env.example`; not set, as there is no proxy yet |
| 6 | A password change and its revocation are two transactions; refresh ignores `credential_version` | 1 | fixed — `CredentialsChanged`, handled by `AuthServiceImpl.endSessions` in the same transaction |
| 7 | A 401 from `/auth/login` sets off a refresh: the wrong message, two throttle counts, a token with no member | 1 | fixed — the interceptor leaves `/auth/*` alone |
| 8 | Any error on the retry after a refresh, or a 5xx/network failure of the refresh itself, signs the user out | 1 | fixed — only a 401 from the refresh clears the session; the retry's own error is its own |
| 9 | A forced sign-out or a new sign-in keeps the query cache, so a MEMBER sees what an ADMIN loaded unredacted | 1 | fixed — `shared/lib/query-client` clears the cache whenever the viewer or their role changes |
| 10 | Two separate refresh single-flights, none across tabs; a refresh drops `member`, so a changed role is stale | 1 | fixed — one `refreshSession` in `shared/lib/session`, under a Web Lock, storing the member too |
| 11 | Email matching is case-sensitive and unstripped; two accounts can differ only in case | 1 | fixed — stored lower-cased, `uq_members_email_lower` (`V16`); HTTP-checked |
| 12 | `create` and `resetPassword` have no role guard in the service; the controller's comment is wrong | 1 | fixed — both take the caller's role; the comment is gone |
| 13 | Account writes record no revision and take no actor | 1 | fixed — `AuditEntityType.MEMBER`, a snapshot without the hash; an account's history is ADMIN-only, found while fixing |
| 14 | On the person page a failed read of unions, events or grave reads as empty, and invites a duplicate union | 1 | fixed — its own error state; no add-relation and no life badge while a read has failed |
| 15 | Cancelling the password dialog keeps the typed passwords; confirm-delete closes before the result and loses the reason | 1 | fixed — every way out resets; confirm-delete awaits the delete and stays open on failure |
| 16 | A GEDCOM import refreshes none of sources, places, chi, citations or graves; hand-written invalidation lists drift | 1 | fixed — `invalidateClanData()` for import, merge and person delete |
| 17 | The dev stack listens on every interface with Swagger on; CORS allows one origin, so 127.0.0.1 or the LAN breaks | 1 | fixed — loopback only; both loopback origins; Swagger stays on for dev, now loopback only |
| 18 | The backend image runs as root; CI has no `permissions:`, mutable action tags, no dependency audit | 1 | fixed — user `genealogy`; `contents: read`; actions pinned to commits; `npm audit` — CI itself has never run (#28) |
| 19 | No way to create, disable or re-role an account from the UI, and no endpoint to disable or re-role | 2 | fixed — `PUT /members/{id}`, the create and edit dialogs; HTTP-checked and screenshot-checked |
| 20 | The tree is in neither the sidebar nor the dashboard | 2 | fixed — "Cây phả hệ" in the sidebar; `/tree` opens on `GET /tree/founder` |
| 21 | Chi and Nơi chốn are hidden from a MEMBER although the server lets them read both | 2 | fixed — in a MEMBER's sidebar, read-only |
| 22 | `/data` is blank for a MEMBER | 2 | fixed — says who can export and why |
| 23 | The person page does not show the person's chi | 2 | fixed — a badge with the chi's path, absent from the redacted view |
| 24 | Signing in does not return to the page asked for; a signed-in visit to `/sign-in` is not redirected | 2 | fixed — both, and only to a path inside the app |
| 25 | On a phone the sidebar sheet stays open after a link is tapped | 2 | fixed |
| 26 | Text is small for the older members a gia phả is mostly read by | 2 | fixed — "Chữ lớn hơn" in the header (D7), applied before the first paint |
| 27 | An ADMIN reset does not force a change; the 72-byte limit is explained in bytes | 2 | fixed in part — the message speaks of letters, not bytes; the forced change is deferred with D1 |
| 28 | No production path, MinIO links open only on the host, no backup, CI has never run | 2 | recorded — out of scope; `.env.example` documents the public endpoint and a `pg_dump` |
| 29 | `problemMessage` prefers `error.message` (English "Network Error"); the toast handler is copied 32 times | 3 | fixed — only the server's words or the fallback; the 32 copies are gone, replaced by one `mutations.onError` in `shared/lib/query-client` (a mutation with its own `onError`, as the person edit page has, replaces it) |
| 30 | The circular light/dark reveal has no `flushSync`, so it animates the old theme | 3 | fixed |
| 31 | No pre-paint theme script, so a dark-mode load flashes light | 3 | fixed — a script in `index.html` sets the theme and the text size |
| 32 | `language-toggle` nests a `DropdownMenuTrigger` directly under `TooltipTrigger asChild` (§6.3) | 3 | fixed — a plain span between them; the open state shows |
| 33 | Plurals and locale: `pendingCount` has no `_one`/`_other`, `inDays` reads `n`, `vi-VN` hard-coded | 3 | fixed — English plurals, `count`, `formatCount` |
| 34 | English sr-only and aria text in `shared/ui`; rail, logo, account button and dialog close lack tooltips | 3 | fixed but one — translated; logo, account button, dialog and sheet close have tooltips; the rail is an unfocusable edge strip and gets a label only |
| 35 | ESLint is not type-aware; `npm run typecheck` checks no file; a two-line comment | 3 | fixed — `recommendedTypeChecked` (D8) found 20: 18 floating `handleSubmit` promises (`shared/lib/forms.voidSubmit`) and 2 needless casts; `typecheck` is `tsc -b`; the comment is one line |
| 36 | Config drift: port 5432 vs 5433, the Vite proxy ignores `BACKEND_PORT`, `.env.example`'s `JWT_SECRET` is refused, and more | 3 | fixed — 5433, the proxy reads `BACKEND_PORT`, `JWT_SECRET` blank and `:?`, Cloudinary passed through, a named `node_modules` volume refreshed on lockfile change, a backend healthcheck |
| 37 | About 850 kB loads before sign-in (zod, an eager shell); Noto Serif subsets overlap; `en.json` is inlined | 4 | fixed in part — every page is lazy and the password dialog loads on first use, so zod (126 kB) left the entry; the browser fetches only the font subsets a page uses; `en.json` stays inlined, as loading it late would flash Vietnamese |
| 38 | No default `staleTime`; the dashboard counts people with a whole search; two anniversary keys | 4 | fixed in part — a 30 s default `staleTime`; the count asks for a page of one, and the two windows (30 and 60 days) are two different views |
| 39 | Sign-in holds a connection through BCrypt; the throttle sweeps its map per failure; the per-request read loads the entity | 4 | fixed in part — no transaction around BCrypt, a sweep a minute, a five-column projection; sign-in still reads the account twice |
| 40 | Dockerfiles have no cache mount, no layer split, no `.dockerignore`; compose polls and checks health every 10 s | 4 | fixed in part — cache mounts, layers, `.dockerignore`; polling stays, as the Windows bind mount needs it |
| 41 | The giỗ row is written twice, and the home copy drops `approximate` | 5 | fixed — `AnniversaryCountdown`; the home row says when a giỗ is approximate |
| 42 | Loading, error and empty states hand-written on four pages; filters skip `SearchInput`; `totalPages > 1` repeated | 5 | recorded — the four pages are cards, not tables, and `TableCard` draws a footer border for any element, so the guard is what keeps an empty one away |
| 43 | A name's initial is computed in two places | 5 | fixed — `shared/lib/initial`, tested |
| 44 | The form-field-plus-error block is repeated across 16 files | 5 | recorded — six lines per field with its own id, label and message; a wrapper would hide the `htmlFor` and `aria` wiring each one needs |
| 45 | Password logic, the 8/72 limits and their messages repeated; a dead `NO_SUCH_MEMBER_HASH`; an unused `@Slf4j` | 5 | fixed — `Passwords`, one `setPassword`; the dead copy and `@Slf4j` removed |
| 46 | Dead code: `collapsible`, `scroll-area`, jspecify, `fetchCurrentMember`, `avatarUrl`, `--chart-*` and more | 5 | fixed but one — removed `collapsible.tsx`, `scroll-area.tsx`, jspecify, `fetchCurrentMember`, `person.noBranch`, the `--chart-*` tokens and the second download body (`downloadFile`); `avatarUrl` stays, as it is a `members` column |
| 47 | `MemberServiceImpl.create` maps by hand, not through MapStruct (§4.1) | 5 | fixed — `MemberMapper.toEntity`, `toSnapshot` |
| 48 | The person page has no `PageHeader`; early returns render outside `PageContainer` | 5 | fixed in part — the early returns are inside it; the record card stays in place of a `PageHeader` |
| 49 | Missing docs on `AuthState`, `useAuthStore`, three form value types and `Language` | 5 | fixed — and the 18 other `*Values` types of the same shape repo-wide (§9) |
| 50 | `shared` imports from features; `Pager` reads `search.*` keys | 5 | fixed in part — the store and session no longer import a feature; `Pager` reads `common.*`; `shared/layouts` keeps composing features, recorded in §6.0 |
| 51 | A redundant `cursor-pointer`; §2's table drifts from `package.json`; `@types/node` 26 vs Node 24; no `format:check` | 5 | fixed in part — the `cursor-pointer` removed, `@types/node` 24.19.1 (checked against the registry), `npm audit fix` cleared three transitive advisories; **the user decided (2026-10-02) that `prettier --write` runs only once the repository is in git**, so a formatting pass can be reviewed and reverted; until then ❌ do not run it, and `format:check` stays out of CI; open: 94 files are not Prettier-formatted, so `format:check` in CI would fail; formatting them all is a rewrite to agree with the user first |

**Found while fixing, and fixed:** the person search's chi filter called "every chi" "Không thuộc chi nào"
(`BranchPicker.noneLabel`); an account's revision carries its email, so its history is the ADMIN's alone (#13).

**Mẻ 8 is closed** (2026-10-02). Of the 51 defects, 38 are fixed, 10 fixed in part or fixed but one with the remainder
named in its row, 1 deferred by the user (#1), and 2 recorded with the reason (#28, #44) — #42 is recorded too.
Open for real: the Prettier formatting of 94 files (#51), which is a rewrite to agree on first. At close: 533 backend
unit tests; frontend type-check, type-aware lint, 47 tests and build clean; `npm audit` 0; the backend booted on `V16`
as a non-root user; 34 HTTP checks of sessions, the throttle and accounts with three roles and 7 of the account trail
and the founder, re-run on the final build, with every test account deleted after; the members page and both
dialogs, the tree from the sidebar, large text, a MEMBER's sidebar, chi page and `/data`, the language menu, the
dialog close tooltip, the folded sidebar and the phone sheet screenshot-checked in light and dark. ❌ As with every mẻ
before it, that is not a claim that this code is correct; the fix code — `LoginThrottle`, `shared/lib/session`,
`query-client`, `CredentialsChanged`, `V16`, the member dialogs — has not itself been read by any of the eleven
angles (§8.5).

### 8.12 The review of 2026-10-05/08 — the fix code of mẻ 1–8

Mẻ 9 of the sweep: the code written to fix mẻ 1–8, which no angle had read (§8.5). All eleven launched on 10-05 died
on the weekly rate limit; each was resumed from its own transcript in three waves (10-06, 10-08). 99 raw findings,
**52 distinct defects**. Written before any fix, on purpose, like §8.8–§8.11. Full text and angles are in the session
scratchpad `findings-mei9.md` and `fixlist-mei9.md`.

**Decided by the user (2026-10-08):**

- **D1 — đời and the founder follow birth links only (#25).** ⚠️ **This changes the locked §3.4** at the user's
  explicit decision. `GenerationCalculator` walks `BIRTH` edges for the clan's descent and the founder choice, the
  same as kinship (§5.3) and quality (§5.1). A con nuôi, con riêng or con nuôi dưỡng is still placed, the way a
  spouse is: one below the adoptive or step parent, as someone outside the line.
- **D2 — the missing data-entry flows are in scope (#21–23):** link an existing person as spouse, child or parent,
  and add a parent; an ADMIN picks any two people to merge; the add-relation dialog takes "đã mất" and a birth and
  death year, so a cụ entered from the tree is not redacted as living.
- **D3 — a MEMBER sees the old gia phả's scans (#24).** `SOURCE` media is shown to every role through the citation
  that names it; the source's own notes and author stay EDITOR+ (§8.8 D3 stands for those). A source is not person
  data, and a scan is the most valuable thing the clan holds.
- **D4 — deployment security (#19, #20):** stored objects carry `Content-Disposition` and `nosniff`; `.env.example`
  trusts `X-Forwarded-For` only from declared proxies; a dev box behind Vite's proxy shares one address, recorded
  as accepted. **MinIO keeps its root credentials**: it is local only, and production moves to Cloudinary like
  charity once the features settle.

**Decided by default while writing this plan** (change them here, before the code, if they are wrong):

- **A form sends the version it opened with** (#1), captured when the dialog opens, never the live prop; a 409
  keeps the dialog open with a "tải lại" action, and the person edit page warns instead of remounting.
- **Derived columns do not move a row's version** (#2): `generation` and `living` are excluded from optimistic
  locking, so a recompute never stales a form. `LivingRefresh` logs a failure at boot rather than stopping the app.
- **The thumbnail is decoded subsampled**, with a pixel cap, and EXIF orientation applied (#4).
- **Ending access is one method** (#13): role, active, password, logout and a rotated replay all bump
  `credential_version`; the last-ADMIN check locks the ADMIN rows it counts.

**The 52 defects:**

| # | Defect | Tier | State |
|---|---|---|---|
| 1 | Edit dialogs send the live version with fields frozen at open, so a refetch defeats the stale check; a 409 leaves the dialog stuck; person edit wipes typed text on a new version | 1 | fixed, driven through the UI — a branch dialog left open while the branch was changed elsewhere answered 409 and kept the other edit, and its text stayed; `useVersionAtOpen` fixes the version when a dialog opens; the person edit page does not refetch on focus; a 409 reloads the cache |
| 2 | đời/living recompute bumps `persons.version`: spurious 409s, add-child lock failures, the nightly sweep rolled back; a boot-time sweep failure stops the app | 1 | fixed — `generation` and `living` are `@OptimisticLock(excluded)` and `Person` no longer stamps `updatedAt` from a callback; `LivingRefresh` logs a failure; HTTP-checked: a death leaves the version alone |
| 3 | `AdvisoryLock` flushes pending writes outside exception translation, so a clash is a 500 | 1 | fixed in code — `AdvisoryLock` is a `@Repository`; the backend boots; no test provokes the clash |
| 4 | The MinIO thumbnail decodes the full raster: an OOM takes the backend down and orphans the original; EXIF orientation ignored | 1 | fixed in part — the header's size is read first (cap 150 million pixels) and the image is decoded subsampled; not tried on a huge image; EXIF orientation is **not** handled |
| 5 | `collapseDuplicateUnions` folds the duplicate's own divorced and remarried unions | 1 | fixed — a moved union folds only into the survivor's own; unit-tested |
| 6 | A pending CREATE keeps the `familyId` of a folded, dropped or purged union, so approval 404s; an approved CREATE keeps a merged-away `targetId` | 1 | fixed — `SuggestionService.reanchorUnions` from merge and purge; an approved CREATE follows its person to the survivor (a change from §8.9); unit-tested |
| 7 | A place's type changes without checking its children; the parent picker offers descendants and narrower levels | 1 | fixed — the server refuses (HTTP-checked, unit-tested) and the place picker excludes the subtree and narrower levels (`place/lib/levels.ts`) |
| 8 | A password over 72 bytes may 500 at sign-in or change | 1 | fixed — `matchesStored` answers "no match" without calling BCrypt; HTTP-checked: 120 bytes is 401 |
| 9 | A GEDCOM `MAP` exponent hangs the import | 1 | fixed — plain decimals only; unit-tested |
| 10 | Retrying a partly failed batch upload uploads every stored file again | 1 | fixed, driven through the UI — one PNG and one non-image: the PNG was stored once, the dialog stayed open with "Đã chọn 1 tệp" and the refusal toast (the native input still reads "No file chosen") |
| 11 | An upload racing a purge or merge leaves an orphan; a post-commit failure deletes a committed upload's objects | 1 | fixed in code — the target is asked again inside the row's transaction, and the cleanup covers the commit only; the race itself is not tested |
| 12 | A non-API exception in a GEDCOM import commits part of the file, skips the đời recompute and loses the warnings | 1 | fixed in part — every per-record catch takes `DataAccessException`, and đời is recomputed in a `finally`; an unexpected exception still loses the warning list |
| 13 | The last-ADMIN guard races; a role, active or logout change leaves the access token live although the UI says signed out | 1 | fixed — the active ADMINs are locked and counted in one read; a role or active change and a rotated replay bump `credential_version`; unit-tested; a logout still leaves its access token to expire |
| 14 | A suggested child defaults to a new one-parent union when the unions load after the form | 1 | fixed, driven through the UI as a MEMBER — a child proposed for a person with one union was stored with that union's id; the suggestion form was screenshot-checked |
| 15 | The date field starts from `display` when `raw` is null, and one keystroke makes the date raw-only | 1 | fixed — `editableText` writes the parts the parser reads back; unit-tested |
| 16 | "Xem thêm" on a revision list replaces the page instead of appending | 1 | fixed, screenshot-checked — 24 edits plus the creation show newest first after "Xem thêm", all 25 on one list (`useInfiniteQuery`) |
| 17 | A merge doubles identical notes, unlike citation quotes | 1 | fixed — `Blank.join`, shared with the citation fold; unit-tested |
| 18 | `/auth/login` has no same-origin check | 1 | fixed — HTTP-checked: a foreign Origin is 403, the app's own is 200 |
| 19 | The forward-headers advice lets `X-Forwarded-For` be spoofed; behind Vite every client is one address | 1 | fixed — `.env.example` names the proxy (`internal-proxies`); the shared dev address is recorded as accepted |
| 20 | Stored objects carry no `nosniff` or `Content-Disposition`; the backend uses MinIO's root credentials | 1 | recorded — MinIO cannot set `nosniff` per object, and keeps its root credentials by decision (local only); objects are already served with the type read from their bytes |
| 21 | No way to link two existing people or add a parent | 2 | fixed — `POST /persons/{id}/links` and `LinkPersonDialog` on the person and tree pages; HTTP-checked, unit-tested, screenshot-checked in light and dark, and driven through the UI (a loose person picked by name became a child and got đời 2) |
| 22 | A merge is reachable only from a detected duplicate pair | 2 | fixed in code — `MergeWithDialog` on the person page for an ADMIN, with a shared `PersonPicker`; screenshot-checked in light and dark, and a merge of two throwaway people was run through it (the duplicate answered 404 afterwards, the result lists what moved) |
| 23 | People added from the tree have no dates, so they are redacted as living | 2 | fixed — birth, death and "đã mất" fields, written as events after the person; screenshot-checked, and driven through the UI (a spouse entered with 1890 and 1950 is stored with the death recorded and no longer living) |
| 24 | Scans of the old gia phả are never visible to a MEMBER | 2 | fixed — HTTP-checked: a MEMBER lists them, the source record stays EDITOR+; the scans button shows on every citation |
| 25 | đời and the founder count step, adoptive and foster links as blood | 2 | fixed — `GenerationCalculator` walks birth links only and places the rest beside the line; §3.4 rewritten; unit-tested |
| 26 | Invalidation lists have drifted (purges, person edit, approval, source merge, upload, merge dialog Esc) | 3 | fixed in code — the union, event, grave, approval and person-edit lists are widened; a source merge refreshes media and reads `mediaMoved`; an upload refreshes the history; the merge panel refreshes when it goes, even if closed mid-run; a 409 reloads the cache |
| 27 | The union-delete text promises a cascade the server refuses; a source with media offers delete | 3 | fixed in part — both texts now say what the server refuses; a source with scans but no citations still shows the delete button, and the server answers it |
| 28 | Member schema limits without `t()`; no client cap on `changeNote` or name parts | 3 | fixed — `buildChangeNoteField` on every edit form and the 50/100/50 name limits, in words |
| 29 | `V14`'s revisions cannot be read by the history summary | 3 | recorded — the rows show a change with no before/after; the payload is applied, and a summary for it is not worth a rule |
| 30 | "Không có thuỷ tổ" ignores the ancestry-cycle cause | 3 | fixed — the text names the loop and points at the data-quality page |
| 31 | The refresh lock has no timeout; signing out after a password change loses the page to return to | 3 | fixed — the refresh request times out after 15 s; the password dialog no longer navigates, so `RequireAuth` keeps where the page was |
| 32 | A place's path is shown in two orders | 3 | recorded — the client's root-first form only feeds the page's filter and is never shown |
| 33 | `addRelation` recomputes đời twice; the founder comparator is quadratic; `/tree/founder` re-reads the graph | 4 | fixed in part — a new one-parent union and its child recompute once; the comparator's two walks are made once per root; `/tree/founder` still reads the graph on every call |
| 34 | The giỗ list loads every death before filtering | 4 | recorded — not measured; the window needs the lunar conversion of each death, and a clan's deaths are few hundred rows |
| 35 | The place page re-renders every row per keystroke | 4 | fixed — the tree is flattened once per fetch and the filter is debounced |
| 36 | A source merge costs three round trips per citation | 4 | recorded — a one-off ADMIN action on a handful of citations; not done |
| 37 | Storage deletes after commit still hold the connection | 4 | recorded — `afterCompletion` runs before the connection is released too, so it takes an executor; not done |
| 38 | The kinship card searches with an empty query | 4 | fixed — it waits for something typed |
| 39 | Union and grave row cleanup is written four times across purge and merge | 5 | fixed — `PurgeService.forgetUnionRows` and `forgetGraveRows` (`RowsForgotten`), called by purge and merge; unit-tested through both; the backend boots |
| 40 | `Blank.toNull` written by hand twice more, against §8.10 #29 | 5 | fixed but one — suggestion and branch use `Blank.toNull`; `locatorOf` stays as a one-line wrapper because it carries the `uq_citations` reason |
| 41 | `partnerIds().isEmpty()` used as an existence check | 5 | fixed — `FamilyService.exists` in purge and event |
| 42 | Dead code: `handleUnknownSort`, `uploaderIds`' empty branch, `ThemeState.theme`, the `TEXT_SIZE_KEY` export, `keysOf`, a contradicting comment | 5 | fixed but one — all gone except `handleUnknownSort`, kept as the answer if a raw sort ever reaches a repository |
| 43 | A suggestion payload is parsed twice per page, against §8.9 #41 | 5 | fixed — `anchorNames` takes the proposals already read; the existing tests pass |
| 44 | Hand-written mapping: the suggestion date, `MediaSnapshot`, `EventFactResponse` | 5 | fixed — `MediaMapper.toSnapshot`, `EventMapper.toFact`, and `EventService.renderDate` for the suggestion's date (so `suggestion` never imports `event`'s mapper) |
| 45 | `maySeeLivingDetails` used as a plain EDITOR+ gate | 5 | fixed — `Role.isEditorOrAbove` for the plain gate in audit, source and suggestion; `maySeeLivingDetails` stays for living-person data, so scoping an EDITOR to a chi would move only one |
| 46 | Per-target switches written three times; two sentences for `uq_citations` | 5 | fixed in part — the "already cited" sentence is `ProblemMessages.CITED_ALREADY`, used by the map and the source service; the per-target switches stay, as every one is exhaustive and a new target type fails the build rather than slipping through |
| 47 | Dialog open/reset handling repeated in about thirteen dialogs | 5 | recorded — `useVersionAtOpen` took the version part; the reset part is a three-line `useEffect` per dialog that each keys on its own form, and a shared hook would hide which dependency resets which form |
| 48 | "Recorded dead" derived on the client and the server | 5 | recorded — the person page shows a badge from the events and grave it already loaded; asking the server again would add a request for a label, and the server rule (§3.4) stays the only one that gates data |
| 49 | WHY inside doc comments (about fifteen), wrapped `//` in `sidebar`, one line over 120, two undocumented props | 5 | fixed — the long line, the two props docs, the wrapped `//` in `sidebar` and the second sentence of 21 doc comments (a "door for X" clause is dropped: §4 already says which features read across) |
| 50 | The home stat link has no tooltip | 5 | fixed, screenshot-checked — hovering the arrow on the first stat shows its label (`IconTooltip`) |
| 51 | The 404 page draws its own column; a redundant `aria-label`; `MergeServiceImpl` lacks the class-level read-only transaction; `Passwords` is a non-record in `dto` | 5 | fixed in part — the redundant `aria-label` is gone and `MergeServiceImpl` is read-only at class level; the 404 page keeps the template's own column and `Passwords` stays, a constants holder in `dto` |
| 52 | §3.4's living rule omits `REBURIAL` and a `GRAVE`-kind grave; `V14` runs live code with no checksum | 5 | fixed in part — §3.4 corrected; `V14` is applied and recorded as is |

---

## 9. Hard rules (do / don't)

- ✅ **Before any non-trivial or multi-step task — and always before any delete/rename/rewrite of existing files —
  list the concrete steps and wait for the user's explicit confirmation.** Don't jump from "I found X" to "I fixed X",
  even when the fix looks obviously correct. The user decides what is safe.
- ✅ §3 is **locked**. Don't quietly deviate from the GEDCOM-aligned model, the fuzzy date model, the Vietnamese lunar
  algorithm, service-layer privacy, or bounded-depth tree APIs. Raise it explicitly if you think one is wrong.
- ❌ No `father_id`/`mother_id` on `person`. ❌ No bare `LocalDate` for a genealogical date. ❌ No Chinese-lunar
  library. ❌ No whole-tree endpoint.
- ✅ Modular monolith, package-by-feature, cross-feature by id (or by the explicit graph projection for
  `tree`/`quality`, §4).
- ✅ Records for DTOs; Lombok for entities + DI + logging; MapStruct for all mapping; RFC 9457 `ProblemDetail` for all
  errors.
- ✅ Return DTOs directly; wrap `Page<T>` in `PagedModel<T>` at the controller; `@Valid` inbound; `@Operation` on every
  handler.
- ✅ One-sentence WHAT doc comment + `@param` on every method, in **TypeScript as well as Java**; WHY only as a
  one-line `//` outside the doc block. ❌ Never a second paragraph inside a doc comment; ❌ never `<p>`.
- ✅ The §4.2 list of domain logic gets unit tests — no exceptions.
- ✅ **Verify a dependency's current version against the registry before pinning it** (§2) — charity's manifests are
  already stale, so copying them forward silently reintroduces old versions.
- ❌ No Jackson 2 imports (`com.fasterxml.jackson`) — Boot 4 uses `tools.jackson`.
- ❌ No custom response/error envelope. ❌ No `@Data` on entities. ❌ No editing applied migrations. ❌ No
  cross-feature entity/repository imports.
- ✅ Screenshot-verify any UI change before reporting it done (§6.2).
- ✅ **When asked to fix one occurrence of a text/wording/pattern, grep the whole repo (source only, not
  `dist`/`target`) for other occurrences of the same *family* of issue and surface them in the same pass** — not just
  the one spot pointed at, and not just the literal substring (e.g. one deprecated library API means checking that
  library's other deprecated APIs too).
- ✅ The same applies to code-bug patterns: if a bug came from a mechanical edit applied across several files, check
  every file that edit touched before reporting it fixed.
- ✅ **A rule enforced by copy-paste is not enforced.** When the same guard appears in a second feature, move it
  behind one method the features call, before adding the third copy. Five §3.6 leaks (§8, F9) and a wrong grave
  answer all came from private `isLiving` copies that each had to be remembered separately.
- ✅ **Unit tests cannot see Spring wiring.** Any change to a service's injected dependencies gets the app booted
  — `docker compose up -d --build backend`, then confirm `Started GenealogyApplication` in the log. Mocks pass
  happily through a bean cycle that stops the app from starting at all.
- ✅ **A privacy or authorization fix is verified over HTTP, with two roles, against the running stack** — not by
  unit tests alone. Assert both directions: the caller who must not see it gets nothing, *and* the caller who
  must still see it does.
- ✅ **When an e2e fixture fails to set up, the assertions that depend on it are not passes.** An empty list
  proves nothing when the list was never filled; fix the fixture and re-run before reporting.
- ✅ **A tracking list is only as good as the day it was written.** "Everything on the list is done" is not
  "everything is done" — check the findings themselves before saying a review is closed. §8.1 was reported
  complete while five findings it never contained were still open.
- ✅ **Exercise every arm of an enum an endpoint accepts.** `kind: CREATE` on `/suggestions` returned 500 from
  the day it shipped because 48 e2e checks only ever sent `UPDATE` and `NOTE` (§8, P5). The same blind spot hid
  the `FAMILY` and `GRAVE` arms of `MediaTargetType` and the `FAMILY` arm of `EventSubjectType`.
- ❌ **Never call `Map.get(null)`.** `Map.of()` and `List.of()` throw on null rather than returning null, and this
  codebase has nullable ids everywhere — `targetId` before approval, `reviewedBy` while pending, and every
  `ON DELETE SET NULL` column.
- ❌ **A security reaction must not share a transaction with the exception that reports it.** Revoking sessions and
  then throwing a `RuntimeException` rolls the revocation back; the replay guard was decorative for three phases
  because of it (§8.2). Write the reaction with `noRollbackFor`, and prove it over HTTP, not with a mock.
- ❌ **Never publish an `HttpMessageConverter` as a bean just to inject it somewhere.** Boot prepends context
  converters to MVC's list, which re-orders content negotiation for the whole app — one such bean base64-encoded
  every `byte[]` returned as JSON and broke Swagger UI project-wide (§8.2). Use a static factory.
- ✅ **A feature that has never been switched on has never been verified.** Swagger UI was configured from P0 and
  had never once rendered; the bug surfaced the first time someone actually opened it. Turning a flag on counts
  as a change — open the thing and look at it.
- ❌ **Removing `@Transactional` from a method does not remove its transaction.** The class-level annotation still
  applies, so the method inherited `readOnly = true` and every insert failed (§8.3). To run outside a
  transaction, say so: `@Transactional(propagation = Propagation.NOT_SUPPORTED)`.
- ✅ **A round trip is only lossless if something asserts it.** Export-then-import silently turned every con nuôi
  into a con đẻ and every fuzzy lunar date into an exact one, because the two halves were written from the same
  intention and never run against each other (§8.3). Build the tag list each side writes and reads, and diff it.
- ✅ **A scan that reports zero proves only that its classifier found nothing.** The 2026-09-18 comment scan said
  zero while 46 field docs remained, in shapes it never looked for. Before reporting a mechanical rule clean,
  point the scan at one known violation of each shape and see it caught.
- ✅ **Record which review angles actually reported, as they report.** "Eleven angles" meant seven, and by the
  next session nobody could say which seven. A review's coverage is a claim about the past — write it down while
  it is still true.
- ❌ **Never assert on a Vietnamese string through Windows PowerShell 5.1 without decoding it yourself.** It reads
  a charset-less JSON response as Latin-1, so a correct answer fails the comparison and sends you hunting a bug
  in the app: decode `RawContentStream` as UTF-8, and `-Form` does not exist there at all — use `curl.exe`.
- ✅ **A check written before a rule changed is not a regression until you read it.** The one failure of the final
  HTTP run (§8.11) was a test that used the account trail to probe an EDITOR's rights, after the trail became the
  ADMIN's alone. Read what a failing check asserts before deciding the app is wrong, or the test.
- ✅ **A shared type that a feature owns is a boundary leak, however small.** `shared/store` imported `Member` from
  `features/member` to type one field; the types a store and a client share live in `shared/types`, and the features
  re-export them.
- ❌ **Never pass a Vietnamese request body to `curl.exe` as an argument.** Windows converts the argument to the
  ANSI codepage, so "ặ" arrives as "?" — a 90-byte password test sent 30 bytes and the check "passed" against a
  request nobody made. Write the body to a file and send `--data-binary @file`.

---

## 10. Formatting of this file

Prose and bullets wrap at **120 columns**. Tables and fenced code blocks are exempt. Keep it that way when editing.
