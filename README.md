<div align="center">

# 🌳 Gia phả

**Ứng dụng web quản lý gia phả cho một dòng họ Việt Nam.**
Ghi lại người trong họ, quan hệ, sự kiện, ảnh và bản scan gia phả cũ, ngày giỗ theo âm lịch — rồi vẽ thành cây phả hệ.

![Java](https://img.shields.io/badge/Java-26-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)

🔗 **Trang web:** [tocnguyenvinhhathanh.vercel.app](https://tocnguyenvinhhathanh.vercel.app)

</div>

---

## Tính năng

| Nhóm | Nội dung |
|---|---|
| **Cây phả hệ** | Bốn kiểu biểu đồ: hậu duệ, tổ tiên, đồng hồ cát, hình quạt. Kéo, thu phóng; thêm con hoặc vợ/chồng ngay trên nút của cây |
| **Người & quan hệ** | Mô hình theo GEDCOM: nhiều tên (húy, tự, hiệu…), tái hôn, con nuôi, con riêng, một bên cha mẹ không rõ |
| **Ngày tháng mờ** | Nhập `1890`, `khoảng 1890`, `trước 1900`, `1918-1922`, `15/3/1890`; giữ nguyên chữ người nhập |
| **Âm lịch & ngày giỗ** | Thuật toán âm lịch Việt Nam (Hồ Ngọc Đức), tháng nhuận, nhắc giỗ sắp tới kèm năm thứ |
| **Xưng hô** | Tính bác / chú / cô / cậu / dì… theo cách gọi miền Bắc; không đoán khi thiếu thứ tự sinh |
| **Ảnh & tư liệu** | Ảnh chân dung, ảnh mộ, bản scan A3 của gia phả cũ (cả PDF); nguồn và trích dẫn cho từng sự kiện |
| **Mộ phần & nơi chốn** | Mộ phần, sinh phần, cải táng; địa danh có phân cấp (xã → huyện → tỉnh → nước) |
| **Chất lượng dữ liệu** | Cảnh báo (không chặn) khi ngày tháng mâu thuẫn; phát hiện người trùng; gộp người trùng |
| **Nhập / xuất** | Xuất GEDCOM 7.0, nhập GEDCOM 5.5.1/7.0, xuất cuốn gia phả PDF có font Unicode |
| **Quyền riêng tư** | Người còn sống bị ẩn chi tiết với thành viên thường; vai trò `ADMIN`, `EDITOR`, `MEMBER` |
| **Nhật ký** | Mọi thay đổi đều có người sửa, thời điểm, nội dung trước/sau và lý do |

> Dự án dành cho **một dòng họ**, không đa người dùng nhiều họ, không có thanh toán hay so khớp DNA.

## Công nghệ

| Lớp | Lựa chọn |
|---|---|
| Backend | Java 26, Spring Boot 4.1, Maven, Spring Security + JWT |
| Cơ sở dữ liệu | PostgreSQL 18 (`unaccent`, `pg_trgm`), Flyway |
| Lưu ảnh | MinIO khi phát triển, Cloudinary khi chạy thật |
| Frontend | React 19, TypeScript, Vite, TailwindCSS 4, shadcn/ui |
| Dữ liệu & form | TanStack Query, React Hook Form + Zod, Zustand |
| Vẽ cây | d3-hierarchy + d3-zoom trên SVG |
| i18n | Tiếng Việt (mặc định), tiếng Anh |

Kiến trúc là **modular monolith**, chia package theo tính năng. Chi tiết nằm ở [docs/analysis.md](docs/analysis.md).

## Cấu trúc thư mục

```
genealogy-spring-react/
├── backend/               # Spring Boot, package-by-feature (com.genealogy.*)
├── frontend/              # React + Vite, feature-sliced (src/features/*)
├── docs/analysis.md       # phân tích nghiệp vụ và lộ trình
├── docker-compose.yml     # PostgreSQL, MinIO, backend, frontend cho máy dev
├── render.yaml            # dịch vụ backend trên Render
└── .github/workflows/     # CI và deploy
```

## Chạy trên máy

**Cần có:** JDK 26, Node 24 LTS, Docker. Không cần cài Maven nếu chạy backend bằng Docker.

```bash
cp .env.example .env
```

Mở `.env` và điền các giá trị bắt buộc:

| Biến | Cách lấy |
|---|---|
| `DB_PASSWORD` | tự đặt |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | tự đặt |

Sau đó:

```bash
docker compose up -d
```

Hoặc chạy từng phần khi phát triển:

```bash
docker compose up -d postgres minio
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

| Thứ | Địa chỉ |
|---|---|
| Giao diện | http://localhost:5173 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MinIO console | http://localhost:9101 |

Tài khoản ADMIN có sẵn: `admin@genealogy.vn` / `Admin@123`. **Hãy đổi mật khẩu ngay** sau lần đăng nhập đầu.

## Kiểm tra

```bash
cd backend  && mvn clean verify
cd frontend && npm run lint && npm test && npm run build
```

`GenealogyApplicationTests` dùng Testcontainers nên cần Docker trực tiếp trên máy chạy Maven. Nếu chạy Maven trong container, loại nó ra: `mvn -Dtest='!GenealogyApplicationTests' test`. CI vẫn chạy bài test này.

## Triển khai

```
GitHub push → CI (build + test)
                └─ Deploy ─┬─ Render  → backend (Docker)
                           └─ Vercel  → frontend (tĩnh)

Backend → Supabase (PostgreSQL)
Backend → Cloudinary (ảnh)
```

| Dịch vụ | Việc đảm nhận | Ghi chú |
|---|---|---|
| **Render** | Backend, Docker, gói Free | Tắt Auto-Deploy; deploy qua Deploy Hook |
| **Vercel** | Frontend tại https://tocnguyenvinhhathanh.vercel.app, thư mục gốc `frontend` | `vercel.json` chuyển `/api/*` sang Render để trình duyệt chỉ thấy một origin |
| **Supabase** | PostgreSQL | Dùng **Session pooler**; tắt Data API |
| **Cloudinary** | Lưu ảnh | `STORAGE_PROVIDER=cloudinary` |

**Secret trên GitHub** (Settings → Secrets and variables → Actions):

| Tên | Giá trị |
|---|---|
| `RENDER_DEPLOY_HOOK_URL` | Render → service → Settings → Deploy Hook |
| `VERCEL_DEPLOY_HOOK_URL` | Vercel → Settings → Git → Deploy Hooks |

**Biến môi trường trên Render:**

| Biến | Giá trị |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host-pooler>:5432/postgres` |
| `DB_USER`, `DB_PASSWORD` | từ Supabase |
| `JWT_SECRET` | chuỗi ngẫu nhiên từ 32 byte trở lên; thiếu thì backend không khởi động |
| `STORAGE_PROVIDER` | `cloudinary` |
| `MEDIA_BUCKET` | thư mục trên Cloudinary, mặc định `genealogy-media` |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | từ Cloudinary |
| `CORS_ORIGINS` | địa chỉ Vercel, `https://tocnguyenvinhhathanh.vercel.app` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native`, để giới hạn đăng nhập sai tính theo từng người thay vì theo proxy |
| `JAVA_TOOL_OPTIONS` | `-Xmx300m`, vì gói Free của Render chỉ có 512 MB |

Gói Free của Render **ngủ sau 15 phút không có yêu cầu**, lần mở đầu mất 30–60 giây. Muốn giữ thức, tạo một monitor HTTP bên ngoài (ví dụ UptimeRobot) gọi `https://<service>.onrender.com/actuator/health` mỗi 10 phút.

Sao lưu cơ sở dữ liệu:

```bash
docker exec genealogy_postgres pg_dump -U postgres -Fc genealogy > genealogy.dump
```

Ảnh nằm ở Cloudinary (hoặc volume `minio_data` khi dev) và cần sao lưu riêng.

## API chính

Tài liệu đầy đủ ở Swagger UI (chỉ bật khi đặt `API_DOCS_ENABLED=true`). Một số nhóm đường dẫn, đều dưới `/api/v1`:

| Nhóm | Đường dẫn |
|---|---|
| Đăng nhập | `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Tài khoản | `/members`, `/members/me`, `/members/me/password` |
| Người, hôn nhân, sự kiện | `/persons`, `/families`, `/events`, `/events/anniversaries` |
| Cây & xưng hô | `/tree`, `/tree/kinship`, `/tree/founder` |
| Tìm kiếm | `/search/persons` |
| Nơi chốn, chi, nguồn | `/places`, `/branches`, `/sources`, `/citations` |
| Ảnh & mộ phần | `/media`, `/persons/{id}/grave` |
| Chất lượng & nhật ký | `/quality/issues`, `/revisions` |
| Nhập / xuất | `/gedcom/export`, `/gedcom/import`, `/book` |
| Đề xuất của con cháu | `/suggestions` |

Quyền theo vai trò: `MEMBER` xem và đề xuất chỉnh sửa; `EDITOR` ghi dữ liệu; `ADMIN` xóa, gộp, nhập GEDCOM và quản lý tài khoản.

## Bảo mật

- Access token sống 15 phút, refresh token 7 ngày, xoay vòng mỗi lần dùng; dùng lại token cũ sẽ thu hồi toàn bộ phiên.
- Đăng nhập sai nhiều lần bị tạm khóa theo cặp email và địa chỉ.
- Chi tiết người còn sống bị ẩn ở tầng service, không chỉ ở giao diện.
- Link ảnh ký có hạn; ảnh của người còn sống không được trả cho thành viên thường.
- `.env` không bao giờ được commit.
