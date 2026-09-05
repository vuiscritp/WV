# Egeo — Roadmap 5 Phần Lớn × 5 Phần Nhỏ (25 mốc)

Nguyên tắc: **mỗi Phần Lớn kết thúc = build APK thành công qua GitHub Actions + chạy được trên máy thật/emulator**, không chỉ compile. Phần lớn sau build tiếp trên nền phần lớn trước, không phá vỡ cái đã chạy được.

---

## PHẦN 1 — Trình duyệt lõi (UI + Search + Cookie + JS + UA)
**Tiêu chí hoàn thành:** mở app → thấy UI cam/đỏ theo phong cách Cốc Cốc (nguyên bản, không copy asset) → gõ từ khóa/URL → tải trang thật, JS/cookie chạy đúng, UA không còn `wv`.

1.1 Khởi tạo repo: cấu trúc `android/`, `node/`, `ui/`, `docs/`, `scripts/`, Gradle + package `egeo.com.browser`, workflow GitHub Actions build Debug APK rỗng đầu tiên (proof of pipeline).
1.2 WebView lõi: bật JavaScript, DOM Storage, Cookie (CookieManager), xử lý UA — loại bỏ marker `wv`, có ô nhập UA tùy chỉnh trong Settings.
1.3 UI chính: thanh địa chỉ/tìm kiếm, nút back/forward/reload, theme engine (CSS variables) 4 chế độ morning/day/night/midnight + theo hệ thống Android.
1.4 Search engine abstraction: Google/Bing/Cốc Cốc/DuckDuckGo, chọn trong Settings, interface dễ thêm engine mới.
1.5 Đóng gói: build Debug + Release (chưa ký thật, dùng debug keystore tạm), upload APK làm artifact CI, README hướng dẫn build local.

---

## PHẦN 2 — Đa tab & Đa profile
**Tiêu chí hoàn thành:** mở nhiều tab trong 1 profile, tạo nhiều profile độc lập cookie/storage, có màn Settings và Diagnostics cơ bản.

2.1 Tab manager: tạo/đóng/chuyển tab, mỗi tab giữ WebView state riêng.
2.2 Profile manager: tạo/xóa/chuyển profile, mỗi profile = thư mục dữ liệu riêng (`WebView.setDataDirectorySuffix` hoặc tương đương).
2.3 Cô lập dữ liệu: cookie/localStorage/IndexedDB/cache tách theo profile, kiểm thử chuyển profile không rò dữ liệu.
2.4 Settings screen đầy đủ: theme, search engine, UA, quản lý profile.
2.5 Diagnostics v1: hiển thị Android/WebView version, profile/tab hiện tại, số lượng session.

---

## PHẦN 3 — Local API (REST + WebSocket)
**Tiêu chí hoàn thành:** app chạy nền, `127.0.0.1:8888` (đổi được port) trả lời `/api/v1/status`, tạo/xoá session-tab-profile qua API, có API key với thời hạn.

3.1 API server skeleton (Ktor/NanoHTTPD), bind localhost-only, đổi port trong Settings.
3.2 API key: generate/revoke, thời hạn 1h/1d/7d/30d/vô thời hạn, lưu an toàn, không log ra ngoài.
3.3 REST endpoints nhóm 1: `status`, `sessions`, `tabs`, `profiles`, `settings`, `cookies` (GET/POST/DELETE theo spec).
3.4 WebSocket: broadcast event navigation/tab/session/console/error, có xác thực bằng API key.
3.5 Bảo mật: request validation, chặn LAN, chống command injection, test unit cho auth + expiration.

---

## PHẦN 4 — Automation engine
**Tiêu chí hoàn thành:** gọi API `navigate/click/fill/type/wait/evaluate/screenshot` từ bên ngoài (Postman/script) và thấy WebView phản ứng đúng, có log phân loại theo module.

4.1 Bridge automation qua injected JS: `click`, `fill`, `type` theo selector.
4.2 `evaluate` (chạy JS tùy ý trong context trang) + `screenshot` (capture WebView bitmap → base64/file).
4.3 `wait` (chờ selector/timeout) + cookie API (get/set/delete qua automation).
4.4 Trạng thái verification: NORMAL/LOADING/CHALLENGE_DETECTED/VERIFICATION_REQUIRED/VERIFICATION_COMPLETED/FAILED/TIMEOUT, phát hiện challenge (không tự giải), báo qua WebSocket.
4.5 Hệ thống log theo category (APP, WEBVIEW, BROWSER, PROFILE, TAB, API, WEBSOCKET, AUTOMATION), test cho automation cơ bản.

---

## PHẦN 5 — Hybrid Node/Puppeteer/Chromium + hoàn thiện
**Tiêu chí hoàn thành:** app tự phát hiện Chromium/CDP (hoặc cho nhập path), chế độ AUTO/WEBVIEW/EXTERNAL_CHROMIUM fallback đúng, Diagnostics đầy đủ, có test suite, Release build ký được (secret riêng, không commit key).

5.1 Chromium/CDP detection: tự dò path phổ biến + ô nhập path thủ công trong Settings, test kết nối CDP.
5.2 `node/` service độc lập: kết nối Puppeteer → CDP, chạy được trong Termux/proot-distro Debian, tài liệu hoá trong `docs/`.
5.3 Chế độ AUTO/WEBVIEW/EXTERNAL_CHROMIUM: logic chọn + fallback về WebView khi Node/Chromium không khả dụng.
5.4 Diagnostics v2 (đầy đủ): Node availability, Chromium path, Puppeteer, CDP, API server + port, WebSocket, profile/session hiện tại.
5.5 Hoàn thiện: test suite tổng, GitHub Actions build Debug + Release (ký bằng secret GitHub), README/docs đầy đủ, dọn dự án theo `.gitignore` (không commit `.git`, `node_modules`, build cache, profile local, secret).

---

## Ghi chú
- Mỗi phần nhỏ (X.Y) là 1 commit/PR có thể review riêng.
- Cuối mỗi Phần Lớn: chạy CI build APK, cài thử, xác nhận tiêu chí "hoạt động cơ bản" trước khi sang phần tiếp theo.
- Giao diện dùng làm nguồn cảm hứng nguyên bản (màu cam/đỏ/cam-đỏ, bố cục kiểu Cốc Cốc), không sao chép asset/code gốc từ file coccoc.com đã tải về.
