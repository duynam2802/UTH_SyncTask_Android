# HỒ SƠ KỸ THUẬT (TECHNICAL PROFILE - v2.7)

### 1. Kiến trúc ứng dụng (Architecture)

- **Kiến trúc:** MVVM (Model-View-ViewModel) kết hợp Clean Architecture đơn giản.
- **UI Framework:** Jetpack Compose (Declarative UI tối ưu hóa GPU RenderThread cho trải nghiệm mượt mà 120fps).
- **Cơ chế Animation & Tối ưu UI Shell (v2.6+):**
  - **Đồng bộ thời gian thực:** Tính toán vị trí vuốt liên tục `currentContinuousPosition = currentPage + currentPageOffsetFraction` từ `PagerState` để điều khiển thanh BottomBar và Capsule Indicator chuyển động mượt theo ngón tay sinh viên.
  - **Tối ưu RenderThread GPU:** Sử dụng `Modifier.graphicsLayer` để điều khiển `scale`, `alpha` và `translation` trên kênh RenderThread của GPU, giữ nguyên `FontWeight` cố định nhằm triệt tiêu hoàn toàn việc re-measure text layout trên Main Thread khi chuyển Tab.
  - **Vật lý Spring Physics:** Tích hợp `spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)` cho hiệu ứng chuyển trang `HorizontalPager` tự nhiên, không bị khựng.
- **Cơ chế xử lý Tạm ngưng học (v2.7):**
  - Trích xuất trường `isTamNgung: Boolean` từ API Portal UTH (`PortalScheduleRepository`).
  - Đánh dấu `isTamNgung = true` trên model `SyncedEvent` và quản lý cờ chống lặp thông báo `notifiedTamNgung`.
  - Tự động kích hoạt Push Notification (`notifyClassPaused`) cảnh báo sinh viên về buổi học bị hoãn.
  - Vô hiệu hóa âm báo nhắc đếm ngược (`ReminderPolicy.evaluateTier` trả về `NONE`) và đồng bộ nhãn `🚫 [TẠM NGƯNG]` kèm gạt bỏ pop-up trên Google Calendar.
- **Cơ chế xử lý Hết hạn Session Portal & Deep Linking 1-chạm (v2.7):**
  - Trong quá trình đồng bộ, nếu API Portal phản hồi mã `401` hoặc `Invalid JWT token`, `SyncRepository` tự động gửi thông báo đẩy `notifyPortalTokenExpired`.
  - `PendingIntent` gửi extra `EXTRA_OPEN_PORTAL_LOGIN = true` về `MainActivity`.
  - `MainActivity` tiếp nhận qua `onCreate`/`onNewIntent` và luân chuyển state tới `AppNavHost` -> `MainShellScreen` (tự động chuyển sang tab Settings) -> `SettingsScreen` (tự động mở giao diện `PortalLoginScreen` full-screen dialog).
- **Cơ chế xử lý Đổi phòng học (v2.5+):**
  - `SyncRepository` đối chiếu trực tiếp `room` từ `PortalScheduleRepository` với cơ sở dữ liệu cục bộ (`EventStore`).
  - Khi phát hiện thay đổi phòng (`roomChanged = true`), hệ thống tự động cập nhật bản ghi địa điểm trên Google Calendar và đặt lại cờ thông báo.
  - `ReminderCheckWorker` kiểm tra định kỳ mỗi 15 phút, kích hoạt thông báo nhắc nhở phòng học mới trước **24 giờ** và **1 giờ** so với thời gian bắt đầu buổi học.

### 2. Các thư viện và công nghệ sử dụng (Stack & Dependencies)

- **Networking (Kết nối mạng):** `OkHttp`, `Retrofit2` & `Gson` (gửi Request, parse JSON API Portal UTH, xử lý Session/Cookie).
- **HTML Parsing (Trích xuất dữ liệu):** `Jsoup` (parse HTML trích xuất lịch học từ Moodle/Courses/THNN).
- **Background Task (Chạy ngầm):** `WorkManager` (`ScheduleSyncWorker` chạy đồng bộ định kỳ và `ReminderCheckWorker` kiểm tra nhắc nhở/đổi phòng/tạm ngưng học mỗi 15 phút).
- **Local Storage (Lưu trữ):** `DataStore` (Lưu thông tin cấu hình và danh sách `SyncedEvent` dưới dạng JSON).
- **Mã hóa bảo mật:** Mã hóa thông tin xác thực sinh viên bằng EncryptedSharedPreferences/CryptoManager.
