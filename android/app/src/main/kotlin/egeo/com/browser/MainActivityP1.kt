package egeo.com.browser

/**
 * Không có logic riêng - toàn bộ hành vi kế thừa từ MainActivity.
 * Lý do tồn tại class riêng: Android chỉ cho khai báo 1 <activity> cho mỗi
 * class trong Manifest, mà mỗi hồ sơ cần android:process riêng để WebView
 * cô lập dữ liệu (cookie/localStorage) thật sự ở cấp hệ điều hành.
 */
class MainActivityP1 : MainActivity()
