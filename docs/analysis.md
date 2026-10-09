# Phân tích dự án — Gia phả (Genealogy Web App)

> Tài liệu phân tích khởi tạo dự án. Đọc trước khi thiết kế schema hay viết code.
> Stack và convention kế thừa từ `D:\Me\charity-spring-react` (xem §6).

---

## 1. Bối cảnh & mục tiêu

Ứng dụng web quản lý **gia phả của một dòng họ** (single-clan, private), dùng thật
cho gia đình đồng thời làm portfolio kỹ thuật.

Hai mục tiêu song song, và chúng **xung đột ở một vài chỗ** — cần chốt ưu tiên:

| Mục tiêu | Kéo theo |
|---|---|
| Dùng thật cho gia đình | Nhập liệu phải dễ cho người lớn tuổi, dữ liệu phải backup được, âm lịch phải đúng, không mất dữ liệu |
| Portfolio / học tập | Kiến trúc rõ, có test + CI, có phần "khó" để show (thuật toán phả hệ, visualization) |

**Nguyên tắc giải xung đột đề xuất:** ưu tiên *đúng dữ liệu* > *dễ nhập* > *đẹp* >
*nhiều tính năng*. Một gia phả sai dữ liệu thì đẹp cũng vô nghĩa, và dữ liệu phả hệ
rất khó sửa về sau khi đã nhập hàng trăm người.

**Ngoài phạm vi (out of scope):**
- Multi-tenant / SaaS nhiều dòng họ — kiến trúc **không** cần tenant isolation.
- Thanh toán, quỹ họ có giao dịch tiền thật.
- Tích hợp DNA matching, tìm kiếm record trực tuyến (Ancestry/FamilySearch API).

---

## 2. Actors & phân quyền

| Role | Mô tả | Quyền |
|---|---|---|
| `ADMIN` | Trưởng tộc / người quản trị gia phả | Toàn quyền, kể cả xoá người, merge, import/export, xem dữ liệu người còn sống |
| `EDITOR` | Người biên tập (thường là đại diện từng chi) | Thêm/sửa nhân khẩu, sự kiện, ảnh; không xoá, không merge |
| `MEMBER` | Con cháu trong họ, đã đăng nhập | Xem toàn bộ (trừ trường riêng tư), đề xuất chỉnh sửa (suggestion), xem lịch giỗ |
| `GUEST` | Khách chưa đăng nhập | *(tuỳ chọn)* Chỉ xem trang giới thiệu dòng họ + cây rút gọn người đã mất |

**Quyết định cần chốt:** có mở trang public read-only không? Ảnh hưởng tới
SecurityConfig và tới §5.4 (privacy). Mặc định đề xuất: **không** ở MVP, thêm sau.

---

## 3. Danh sách chức năng

### 3.1 Đã chốt (bạn chọn)

| # | Chức năng | Ghi chú |
|---|---|---|
| F1 | Hồ sơ cá nhân & quan hệ | CRUD nhân khẩu, hôn nhân nhiều đời, cha mẹ/con, con nuôi |
| F2 | Cây phả hệ trực quan | Zoom/pan, đổi người trung tâm, nhiều kiểu chart |
| F3 | Ảnh & tài liệu | Ảnh chân dung, ảnh gia đình, scan gia phả cũ, giấy tờ |
| F4 | Giỗ chạp & sự kiện | Lịch âm, nhắc ngày giỗ, sinh nhật, sự kiện dòng họ |

### 3.2 Đề xuất thêm — chuẩn nghiệp vụ phả hệ (best practice)

Đây là phần bạn nhờ research. Các tính năng dưới đây **không phải nice-to-have** —
chúng là chuẩn có mặt trong hầu hết phần mềm phả hệ nghiêm túc (Gramps, webtrees,
MyHeritage, Ancestry), và một vài cái **phải quyết ngay từ schema**, không thêm sau
được nếu không migrate đau đớn.

| # | Chức năng | Vì sao là chuẩn | Bắt buộc quyết sớm? |
|---|---|---|---|
| **F5** | **Mô hình dữ liệu kiểu GEDCOM** (Person + Family + FamilyChild) thay vì `person.fatherId/motherId` | Model "cha/mẹ trực tiếp" vỡ ngay khi gặp: tái hôn, con riêng, con nuôi, không rõ một bên cha/mẹ, anh em cùng cha khác mẹ | ✅ **Schema — quyết ngay** |
| **F6** | **Ngày tháng mờ / không chắc chắn** (`khoảng 1890`, `trước 1900`, `1918–1922`, chỉ có năm) | Gia phả cũ hầu như không có ngày chính xác. Nếu dùng `LocalDate` sẽ phải bịa dữ liệu hoặc để trống — mất thông tin | ✅ **Schema — quyết ngay** |
| **F7** | **Nguồn & dẫn chứng** (source + citation) | Chuẩn GPS (Genealogical Proof Standard): mỗi khẳng định nên chỉ ra lấy từ đâu — gia phả chữ Hán năm nào, lời kể của ai, giấy khai sinh nào | ⚠️ Nên có bảng từ đầu, UI làm sau |
| **F8** | **Nhiều tên cho một người** (tên khai sinh, tên húy, tên tự, tên hiệu, thuỵ hiệu, tên thánh, biệt danh) | Rất đặc thù gia phả Việt — cụ tổ thường có 3–4 tên. Nhét vào 1 cột `full_name` là mất dữ liệu | ✅ **Schema — quyết ngay** |
| **F9** | **Bảo vệ người còn sống** (living person privacy) | Chuẩn bắt buộc của mọi app phả hệ: người chưa có ngày mất & sinh < 100 năm → ẩn chi tiết với role thấp | ⚠️ Ảnh hưởng mọi response DTO — quyết sớm |
| **F10** | **Kiểm tra tính nhất quán dữ liệu** (consistency checker) | Tự phát hiện: mất trước khi sinh, mẹ < 12 hoặc > 55 tuổi khi sinh con, cưới trước 13 tuổi, thọ > 110, **người là tổ tiên của chính mình** (cycle) | Có thể thêm sau |
| **F11** | **Phát hiện & gộp trùng** (duplicate detection + merge) | Nhập liệu nhiều người → chắc chắn có trùng. Merge thủ công rất dễ hỏng dữ liệu nếu không có tool | Có thể thêm sau |
| **F12** | **Máy tính quan hệ** ("A là gì của B?") | Tính LCA trên đồ thị tổ tiên rồi map sang **xưng hô tiếng Việt** (ông/bà, bác/chú/cô/dì/cậu/mợ, anh–chị–em họ, cháu/chắt/chút/chít). Đây là phần thuật toán "show" được nhất của dự án | Có thể thêm sau, nhưng cần `birth_order` trong schema |
| **F13** | **Import / Export GEDCOM** | Tránh "data prison". Cho phép nhập từ phần mềm gia phả cũ và đem dữ liệu đi nơi khác. Import GEDCOM 5.5.1, export [GEDCOM 7.0](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html) | Sau MVP |
| **F14** | **Xuất sách gia phả PDF** | Giá trị thực tế cao với dòng họ Việt: in thành sách, chia theo đời/chi. Backend đã chốt dùng iText Core 9 (xem CLAUDE.md §2) | Sau MVP |
| **F15** | **Nhật ký thay đổi / audit** (ai sửa gì, khi nào, rollback) | Dữ liệu phả hệ hay tranh cãi; cần biết ai đã đổi ngày mất của cụ và dựa vào đâu | Nên có sớm (bảng `revision`) |
| **F16** | **Chi / phái / nhánh** | Dòng họ Việt tổ chức theo chi — cần lọc "chỉ xem chi Hai", thống kê theo chi | ✅ **Schema — quyết ngay** |
| **F17** | **Đề xuất chỉnh sửa** (suggestion từ MEMBER, ADMIN duyệt) | Cho con cháu góp thông tin mà không cho quyền sửa trực tiếp — cách duy nhất để gia phả sống mà không loạn | Sau MVP |
| **F18** | **Địa danh có cấu trúc** (place: xã/huyện/tỉnh + toạ độ) | Địa danh Việt Nam đổi tên/sáp nhập liên tục; lưu chuỗi tự do sẽ không thống kê hay lên bản đồ được | ⚠️ Nên có bảng từ đầu |
| **F19** | **Mộ phần** (vị trí, toạ độ, ảnh) | Đặc thù Việt — con cháu cần biết mộ ở đâu khi đi tảo mộ | Có thể thêm sau |
| **F20** | **Tìm kiếm & lọc nâng cao** | Theo tên, đời, chi, năm sinh/mất, địa danh, còn sống/đã mất | MVP nên có bản đơn giản |

### 3.3 Khuyến nghị cắt / hoãn

- **Không** làm chat/forum dòng họ ở MVP — tốn công, ít dùng.
- **Không** làm mobile app — responsive web là đủ.
- **Hoãn** bản đồ phân bố dòng họ (map view) — phụ thuộc F18 có dữ liệu sạch.

---

## 4. Mô hình dữ liệu (quyết định cốt lõi)

### 4.1 Vì sao không dùng `person.father_id / mother_id`

Model phẳng này hỏng ở các ca rất phổ biến trong gia phả Việt:

- Cụ ông có **hai bà** → con của bà nào? Không biểu diễn được thứ tự vợ cả/vợ thứ.
- **Con nuôi** → là con về mặt gia phả nhưng không cùng huyết thống.
- Chỉ biết cha, **không biết mẹ** → mother_id null, mất thông tin "đây là một cặp".
- **Anh em cùng cha khác mẹ** → không truy được nhóm anh em ruột.
- Sự kiện **kết hôn / ly hôn** không có chỗ để gắn.

### 4.2 Mô hình đề xuất (GEDCOM-aligned)

```
person                    -- INDI: một cá nhân
  id, gender, living, generation, branch_id, birth_order, notes, created_*

person_name               -- nhiều tên / người (F8)
  id, person_id, type(BIRTH|HUY|TU|HIEU|THUY|SAINT|ALIAS), surname, middle, given, is_primary

family                    -- FAM: một cặp vợ chồng (union)
  id, partner1_id, partner2_id, status(MARRIED|DIVORCED|PARTNER|UNKNOWN), order_index

family_child              -- liên kết con vào family, KÈM loại quan hệ
  family_id, child_id, relation_to_p1, relation_to_p2  -- BIRTH|ADOPTED|STEP|FOSTER
  birth_order

event                     -- polymorphic: gắn vào person hoặc family
  id, subject_type(PERSON|FAMILY), subject_id,
  type(BIRTH|DEATH|BURIAL|MARRIAGE|DIVORCE|RESIDENCE|OCCUPATION|...),
  <các cột ngày — xem 4.3>, place_id, description

place                     -- F18, phân cấp
  id, name, type(WARD|DISTRICT|PROVINCE|COUNTRY), parent_id, lat, lng

source / citation         -- F7
media / media_link        -- F3, polymorphic giống event
branch                    -- F16, cây chi/phái, parent_id
revision                  -- F15, audit
```

**Ghi chú:** `partner1/partner2` thay vì `husband/wife` để không phải xử lý ngoại lệ
khi giới tính không rõ (gia phả cũ rất hay thiếu). Hiển thị "chồng/vợ" suy ra từ
`person.gender` ở tầng DTO.

### 4.3 Mô hình ngày tháng (F6) — đừng dùng `LocalDate`

Đề xuất **embeddable `GenealogyDate`**, lưu thành nhóm cột:

```
<prefix>_modifier    ENUM  EXACT | ABOUT | BEFORE | AFTER | BETWEEN | ESTIMATED | CALCULATED
<prefix>_calendar    ENUM  SOLAR | LUNAR
<prefix>_year        INT   nullable
<prefix>_month       INT   nullable   -- cho phép chỉ biết năm
<prefix>_day         INT   nullable
<prefix>_year2/…            -- vế thứ hai cho BETWEEN
<prefix>_sort_date   DATE  -- dẫn xuất, chỉ để ORDER BY / so sánh, KHÔNG hiển thị
<prefix>_raw         TEXT  -- nguyên văn người dùng nhập, giữ để không mất dữ liệu
```

`sort_date` là cột dẫn xuất (đầu khoảng cho `BEFORE`, giữa khoảng cho `BETWEEN`…),
tính ở service khi ghi. Mọi so sánh/sắp xếp dùng cột này; mọi hiển thị dùng bộ
modifier + y/m/d.

### 4.4 Âm lịch (F4) — cạm bẫy cần biết trước

Ngày giỗ trong gia phả Việt là **âm lịch**, và:

- **Âm lịch Việt Nam ≠ âm lịch Trung Quốc.** Hai lịch tính theo múi giờ khác nhau
  (UTC+7 vs UTC+8), nên **một số ngày lệch nhau đúng 1 ngày**. Dùng thư viện lịch
  âm Trung Quốc (`lunar-java`, `cn.6tail`…) sẽ sai ở đúng những ngày đó.
- Khuyến nghị: **port thuật toán Hồ Ngọc Đức** (thuật toán chuẩn cho âm lịch Việt
  Nam, công khai) sang một class thuần trong `common/util/` — không dependency,
  test được bằng bộ ngày đã biết.
- Giỗ cần lưu **ngày âm**, rồi tính **lần xuất hiện dương lịch kế tiếp** để nhắc.
  Xử lý riêng **tháng nhuận** và ngày 30 ở tháng thiếu (chỉ 29 ngày) — quy ước
  thường dùng: lùi về ngày 29.

### 4.5 Trường suy ra (derived) cần chốt cách tính

| Trường | Cách tính | Chiến lược |
|---|---|---|
| `generation` (đời) | Khoảng cách tới cụ tổ theo đường huyết thống | Lưu denormalized, recompute khi đổi quan hệ cha/con |
| `living` | `death_date IS NULL AND (birth_year IS NULL OR birth_year > năm nay - 100)` | Tính khi đọc, hoặc cột dẫn xuất + job nền |
| Nội / ngoại | Suy từ đường nối tới người trung tâm | Tính lúc render, không lưu |
| Quan hệ (F12) | LCA trên đồ thị tổ tiên | Tính lúc gọi API, cache theo cặp nếu chậm |

---

## 5. Quyết định kỹ thuật cần chốt

### 5.1 Vẽ cây phả hệ — phần khó nhất

Gia phả **không phải cây, mà là đồ thị (DAG)**: hôn nhân tạo cạnh ngang, tái hôn
tạo nhiều nhánh, và anh em họ lấy nhau (có thật trong nhiều dòng họ) tạo chu trình
trong đồ thị vô hướng. Vẽ "toàn bộ dòng họ" trên một canvas sẽ rối không đọc được
khi vượt ~150 người.

**Đề xuất:** luôn vẽ theo **một người trung tâm + độ sâu cấu hình được**, backend
trả về **subgraph** (BFS N đời lên/xuống), không bao giờ trả cả cây.

Các kiểu chart nên có:

| Kiểu | Dùng khi | Thuật toán |
|---|---|---|
| Cây hậu duệ (descendant) | Xem con cháu một cụ — kiểu gia phả truyền thống | `d3-hierarchy` tidy tree, gộp cặp vợ chồng thành 1 node |
| Cây tổ tiên (pedigree) | Truy ngược tổ tiên của mình | Cây nhị phân, dễ |
| Fan chart | Ảnh đẹp để in / khoe | Pedigree toạ độ cực |
| Đồng hồ cát (hourglass) | Tổ tiên + hậu duệ quanh 1 người | Ghép 2 cái trên |

**Kỹ thuật:** SVG + `d3-hierarchy` + `d3-zoom`, **không** dùng thư viện family-tree
đóng gói sẵn (đa số không hỗ trợ nhiều đời vợ/chồng, và bạn sẽ phải fork). Đây cũng
là phần đáng show nhất trong portfolio.

### 5.2 Lưu trữ ảnh & tài liệu

**Đã chốt (CLAUDE.md §3.9, đổi ngày 2026-10-09):** **chỉ dùng Cloudinary**, cả khi dev lẫn prod, sau một interface
`StorageService` hẹp (`upload` / `delete` / `resolveUrl` / `download`). Ban đầu chọn MinIO cho dev, nhưng đã bỏ vì
giống charity và để dev chạy đúng cùng một implementation với prod. Giữ Cloudinary vì transform/thumbnail tự động —
app này nặng ảnh (chân dung + scan A3 300dpi) và tự viết bộ resize là việc thật.

Đánh đổi chấp nhận: ảnh thử khi dev cũng lên Cloudinary, nên dùng tài khoản thử riêng, không dùng tài khoản của họ.

**Còn phải chốt:** có giữ file scan gốc không nén không? Nếu có thì cần đường lưu riêng, vì
Cloudinary sẽ nén lại ảnh gốc.

### 5.3 Tìm kiếm tiếng Việt

Tìm "Nguyen Van An" phải ra "Nguyễn Văn An". PostgreSQL có `unaccent` +
`pg_trgm` — bật extension trong migration đầu tiên và tạo index trigram trên tên.
Rẻ và đủ dùng; không cần Elasticsearch.

### 5.4 Privacy người còn sống (F9)

Quyết định **ở tầng nào**: filter trong mapper (MapStruct `@AfterMapping`), trong
service, hay ở `@JsonView`? Đề xuất: **ở service**, trả về DTO khác nhau theo role
(`PersonDetailResponse` vs `PersonRedactedResponse`) — rõ ràng và test được, tránh
rò rỉ ngầm qua mapper.

---

## 6. Kiến trúc & stack (kế thừa `charity-spring-react`)

Giữ nguyên **kiến trúc và convention** của charity, nhưng **version thì lấy mới nhất** — charity
đã tụt lại ở một số dependency. Bảng version chốt nằm ở [CLAUDE.md](../CLAUDE.md) §2 (đã đối chiếu
npm / Maven Central ngày 2026-09-14); đừng copy `pom.xml` / `package.json` của charity sang.

**Backend** — Java 26, Spring Boot 4.1.0 (Jackson 3 `tools.jackson.*`), Maven, PostgreSQL 18 +
Flyway (`spring-boot-flyway`), Spring Security + JJWT 0.13.0 (access 15m / refresh 7d HttpOnly
cookie), MapStruct 1.6.3, Lombok 1.18.48, springdoc-openapi 3.1.1,
Cloudinary 2.4.0,
iText Core 9.7.1, Testcontainers 2.0.5.

**Frontend** — React 19.3 + TypeScript 6.0.3 strict, Vite 8, TailwindCSS 4.3 + Shadcn/ui,
TanStack Query 5.102, React Hook Form + Zod 4.6, Zustand 5, Axios (interceptor refresh
single-flight), react-i18next 17 (vi mặc định, en fallback), Node 24 LTS.

> TypeScript dừng ở **6.0.3** chứ không lên 7.0.2: `typescript-eslint` hiện khai báo peer
> `typescript: ">=4.8.4 <6.1.0"`, nên TS 7 sẽ mất toàn bộ tầng lint type-aware. Xem CLAUDE.md §2.

**Convention giữ nguyên:** modular monolith package-by-feature, cross-feature chỉ
qua service interface + id; DTO là `record`; lỗi theo RFC 9457 `ProblemDetail`;
controller trả DTO trực tiếp, phân trang bọc `PagedModel`; Javadoc 1 câu WHAT +
`@param`; frontend feature-sliced `features/<name>/{api,types,components,pages}`.

**Thêm mới cho dự án này:** `d3-hierarchy` + `d3-zoom` (F2).

### 6.1 Bản đồ package backend

```
com.<group>.genealogy/
├── common/            config, security, exception, util (LunarCalendar, VietnameseName, GenealogyDate)
├── person/            person, person_name, giới tính, đời, chi
├── family/            family, family_child, quan hệ hôn nhân
├── event/             event polymorphic + giỗ/sinh nhật
├── place/             địa danh phân cấp
├── media/             ảnh & tài liệu; StorageService + impls
├── source/            nguồn & dẫn chứng
├── tree/              API subgraph cho visualization + relationship calculator
├── quality/           consistency checker + duplicate detection
├── gedcom/            import/export  (Phase 3)
├── branch/            chi / phái
├── auth/ member/      kế thừa gần như nguyên từ charity
└── GenealogyApplication.java
```

`tree` và `quality` là **feature đọc-chéo** — chúng cần dữ liệu của `person` và
`family`. Theo luật cross-feature-by-id, chúng gọi qua `PersonService` /
`FamilyService` interface, **không** import entity. Lưu ý: điều này sẽ tốn query
khi duyệt đồ thị — chấp nhận đọc qua một DTO "graph edge" gọn (id + parent ids)
thay vì gọi từng người một.

---

## 7. Lộ trình đề xuất

| Phase | Nội dung | Ra được gì |
|---|---|---|
| **P0 — Nền** | Scaffold repo theo charity: pom, compose, Flyway V1, auth/member, CI, CLAUDE.md | Đăng nhập được, CI xanh |
| **P1 — Lõi dữ liệu** | F1, F5, F6, F8, F16, F18 + tìm kiếm cơ bản (§5.3) | Nhập được nhân khẩu & quan hệ đúng chuẩn |
| **P2 — Nhìn thấy** | F2 (descendant + pedigree), F3 ảnh, F20 lọc | Bản demo có sức thuyết phục |
| **P3 — Đặc thù Việt** | F4 âm lịch + nhắc giỗ, F19 mộ phần, F12 xưng hô | Gia đình bắt đầu dùng thật |
| **P4 — Chất lượng dữ liệu** | F10 checker, F11 merge, F15 audit, F7 nguồn | Dữ liệu không mục theo thời gian |
| **P5 — Mở rộng** | F13 GEDCOM, F14 sách PDF, F17 suggestion, fan chart | Không data prison, in được sách |

**Việc cần làm ngay ở P0/P1 vì không sửa sau được:** F5, F6, F8, F16 (schema) và
F9 (hình dạng DTO).

---

## 8. Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Chọn sai mô hình quan hệ (§4.1) | **Cao** | Chốt GEDCOM-aligned ngay P1 |
| Âm lịch sai do dùng lib Trung Quốc | **Cao** | Port thuật toán Hồ Ngọc Đức + unit test với ngày giỗ có thật |
| Cây phả hệ rối khi > 150 người | Trung bình | Luôn vẽ theo người trung tâm + giới hạn độ sâu |
| Nhập liệu nản, dự án chết ở 30 người | **Cao** | Ưu tiên UX form nhập nhanh; cân nhắc import GEDCOM sớm hơn nếu đã có file |
| `generation` sai sau khi sửa quan hệ | Trung bình | Recompute có transaction + test; không cho sửa cha/mẹ tuỳ tiện |
| Phạm vi phình to (20 tính năng) | **Cao** | Bám lộ trình P0–P5, không nhảy cóc |

---

## 9. Câu hỏi cần bạn chốt

1. **Trang public read-only** cho khách chưa đăng nhập — có hay không? (§2)
2. **Đã có dữ liệu sẵn chưa?** File GEDCOM, Excel, Word, hay ảnh chụp gia phả giấy?
   Nếu có file GEDCOM thì F13-import nên kéo lên P1 thay vì P5.
3. **Quy mô dự kiến**: dòng họ khoảng bao nhiêu người, bao nhiêu đời? (quyết định
   mức đầu tư vào §5.1 performance)
4. **File scan gốc**: có cần giữ bản chất lượng cao không nén không? (§5.2)
5. **Song ngữ vi/en** có cần không, hay chỉ tiếng Việt? (charity có i18n bắt buộc —
   giữ hay bỏ cho dự án này?)
6. `groupId` Maven và tên package base muốn đặt là gì?

---

## Nguồn tham khảo

- [The FamilySearch GEDCOM 7.0 Specification](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html) — mô hình INDI/FAM, kiểu ngày mờ, cấu trúc citation
- [What is the FamilySearch GEDCOM 7.0 standard?](https://www.familysearch.org/en/help/helpcenter/article/what-is-the-familysearch-gedcom-7-0-standard) — bối cảnh và trạng thái chuẩn
- [The GEDCOM data format — webtrees](https://webtrees.net/gedcom/) — góc nhìn triển khai thực tế
- [GEDCOM 7.0 Compatibility Breakdown](https://sites.google.com/view/generagenealogicalservices/blog/gedcom-7-0-compatibility-breakdown) — mức độ hỗ trợ 7.0 vs 5.5.1 của các phần mềm
- [Professional Genealogy Software features](https://evidentiasoftware.com/how-professional-genealogy-software-assists/) — consistency check, duplicate detection
- [Online Tree or Genealogy Software?](https://www.familytreemagazine.com/resources/online/online-tree-vs-genealogy-software/) — privacy người còn sống, phân quyền
