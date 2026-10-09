# Gia phả — Genealogy Web App

Ứng dụng web quản lý gia phả cho **một dòng họ** (private, single-clan).

- Kiến trúc & quy ước: [CLAUDE.md](CLAUDE.md)
- Phân tích nghiệp vụ & lộ trình: [docs/analysis.md](docs/analysis.md)

**Giai đoạn hiện tại: P0 — nền tảng.** Đăng nhập được; các bảng phả hệ
(`person`, `family`, `event`, ...) sẽ có ở P1.

---

## Yêu cầu

| | Phiên bản |
|---|---|
| JDK | 26 |
| Node | 24 LTS |
| Docker | để chạy PostgreSQL 18 + MinIO |

Maven không cần cài sẵn nếu chạy backend qua Docker.

## Chạy

```bash
cp .env.example .env      # điền JWT_SECRET
docker compose up -d      # postgres + backend + frontend
```

Chạy từng phần khi phát triển:

```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

| Thứ | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MinIO console | http://localhost:9101 (`minioadmin` / `minioadmin`) |

**Tài khoản seed:** `admin@genealogy.vn` / `Admin@123` — đổi ngay sau lần đăng nhập đầu.

## Kiểm tra

```bash
cd backend && mvn clean verify
cd frontend && npm run lint && npm run build
```

Test nạp context (`GenealogyApplicationTests`) cần Docker socket cho Testcontainers.
Không có Docker thì loại trừ nó: `mvn -Dtest='!GenealogyApplicationTests' test`.

## API hiện có (P0)

| Method | Path | Quyền |
|---|---|---|
| POST | `/api/v1/auth/login` | công khai |
| POST | `/api/v1/auth/refresh` | công khai (cookie) |
| POST | `/api/v1/auth/logout` | công khai (cookie) |
| GET | `/api/v1/members/me` | đã đăng nhập |
| POST | `/api/v1/members` | `ADMIN` |
