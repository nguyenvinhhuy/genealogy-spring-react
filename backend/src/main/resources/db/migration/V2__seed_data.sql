-- ============================================================
-- Genealogy - seed data.
-- ============================================================

-- Bootstrap admin (truong toc). Password: Admin@123 - change it after first login.
-- BCrypt cost 10; Spring's BCryptPasswordEncoder verifies the $2b$ prefix.
INSERT INTO members (full_name, email, password_hash, role, is_active)
VALUES (
    'Quan tri vien',
    'admin@genealogy.vn',
    '$2b$10$t6Jzl1gEHBM9XyFc/LpgMO97N3u23hVlJK3zwa.2Zwu5uuOHU6SFW',
    'ADMIN',
    TRUE
)
ON CONFLICT (email) DO NOTHING;
