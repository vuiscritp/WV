# Egeo — Trình duyệt Android (Phase 1)

Trình duyệt Android dùng WebView, giao diện lấy cảm hứng màu sắc cam/đỏ/cam-đỏ
(thiết kế nguyên bản, không dùng lại asset/code của bất kỳ sản phẩm nào khác).

## Trạng thái: Phase 2 — Đa tab & đa hồ sơ

Kế thừa toàn bộ Phase 1, cộng thêm:

- **Đa tab thật**: mỗi tab là 1 `WebView` riêng biệt (không phải load lại URL
  trên 1 WebView dùng chung), nên mỗi tab giữ đúng lịch sử back/forward, vị
  trí cuộn... của riêng nó. Thanh tab cuộn ngang phía trên, có nút đóng từng
  tab và nút "+" thêm tab mới.
- **Trang chủ/tab mới thật**: `assets/home.html` (phong cách cam, dựa trên
  giao diện người dùng cung cấp, đã bỏ phần "kết quả tìm kiếm" giả lập — khi
  tìm kiếm, trang gọi sang Android để điều hướng WebView tới trang kết quả
  **thật** của công cụ tìm kiếm đã chọn).
- **Đa hồ sơ (profile) cách ly thật**: 4 hồ sơ (Mặc định + 3 hồ sơ phụ), mỗi
  hồ sơ chạy trong **1 tiến trình Android riêng** (`android:process` trong
  Manifest) — đây là cách chính thống để WebView cô lập cookie/localStorage/
  cache theo hồ sơ, không phải giả lập. Hồ sơ phụ cần Android 9 (API 28) trở
  lên vì giới hạn kỹ thuật của WebView; dưới mức đó app tự ẩn tùy chọn này.
- **Cài đặt**: thêm mục chọn/chuyển hồ sơ, xóa dữ liệu hồ sơ hiện tại, mở màn
  Chẩn đoán.
- **Chẩn đoán (v1)**: hiển thị phiên bản Android/WebView, tiến trình, hồ sơ
  hiện tại, số tab, URL tab hiện tại, phiên bản app.
- **Quyền micro thật**: nút tìm kiếm bằng giọng nói dùng Web Speech API thật
  trong WebView, có xin quyền `RECORD_AUDIO` và cấp qua
  `WebChromeClient.onPermissionRequest` — không phải nút trang trí.

Giới hạn đã biết của Phase 2 (sẽ cải thiện ở phase sau): danh sách tab không
được lưu lại khi tiến trình bị hệ thống kill (mất khi mở lại app từ đầu); cài
đặt giao diện/UA/công cụ tìm kiếm dùng chung cho mọi hồ sơ (chưa tách riêng
từng hồ sơ).

## Trạng thái: Phase 1 — Trình duyệt lõi

Đã có trong phase này:

- WebView bật JavaScript, DOM Storage, Cookie (kể cả third-party).
- User-Agent bỏ marker `; wv)` mà WebView tự thêm; cho phép nhập UA tùy chỉnh trong Cài đặt.
- Thanh địa chỉ kiêm ô tìm kiếm, tự nhận input là URL hay từ khóa tìm kiếm.
- 4 theme: `Morning`, `Day`, `Night`, `Midnight` + chế độ `Theo hệ thống`
  (đọc dark mode của Android, tự áp lại khi hệ thống đổi trong lúc app đang mở).
- 4 công cụ tìm kiếm: Google, Bing, Cốc Cốc, DuckDuckGo — chọn trong Cài đặt.
- Màn hình Cài đặt (Settings) lưu lựa chọn qua SharedPreferences.
- CI GitHub Actions: build Debug + Release (chưa ký) APK tự động mỗi lần push.

Chưa có (nằm ở các phase sau, xem `docs/ROADMAP.md`): đa tab, đa profile, local
API, automation engine, tích hợp Node/Puppeteer.

## Cấu trúc thư mục

```
Egeo/
├── .github/workflows/android-ci.yml   # CI build APK
├── android/                           # Toàn bộ Android app (Kotlin)
│   └── app/src/main/kotlin/egeo/com/browser/
│       ├── MainActivity.kt
│       ├── SettingsActivity.kt
│       ├── AppPrefs.kt
│       ├── theme/                     # Theme engine (morning/day/night/midnight)
│       ├── search/                    # Trừu tượng hoá search engine
│       └── util/                      # UserAgentUtil
├── node/                              # (Phase 5) service Puppeteer/CDP
├── ui/                                # (Tham khảo) mockup/asset UI nguyên bản
└── docs/                              # Tài liệu, roadmap
```

## Build local

Yêu cầu: JDK 17, Android SDK (compileSdk 34), không cần cài Gradle riêng nếu
dùng Android Studio (đồng bộ project sẽ tự tải Gradle qua wrapper của Studio).

Nếu build bằng dòng lệnh mà máy đã có `gradle` (>=8.7):

```bash
cd android
gradle assembleDebug
# APK ở: android/app/build/outputs/apk/debug/app-debug.apk
```

## Build qua GitHub Actions

Push code lên nhánh `main` (hoặc mở Pull Request) → workflow
`.github/workflows/android-ci.yml` tự chạy:
1. Cài JDK 17 + Android SDK.
2. Chạy unit test (`testDebugUnitTest`).
3. Build `assembleDebug` và `assembleRelease` (bản release chưa ký, chỉ để
   kiểm tra pipeline — phase sau mới thêm ký release bằng GitHub Secrets).
4. Upload 2 APK làm artifact của run, vào tab **Actions** → chọn run → mục
   **Artifacts** để tải về cài thử.

## Ghi chú bản quyền

Giao diện được thiết kế nguyên bản (màu sắc, layout, icon tự vẽ bằng vector).
Không sao chép mã nguồn/asset từ bất kỳ trang web hoặc sản phẩm nào khác.
