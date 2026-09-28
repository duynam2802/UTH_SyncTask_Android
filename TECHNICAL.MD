# HỒ SƠ KỸ THUẬT (TECHNICAL PROFILE - v2.6)

### 1. Kiến trúc ứng dụng (Architecture)

- **Kiến trúc:** MVVM (Model-View-ViewModel) kết hợp Clean Architecture đơn giản.
- **UI Framework:** Jetpack Compose (Declarative UI tối ưu hóa GPU RenderThread cho trải nghiệm mượt mà 120fps).
- **Cơ chế Animation & Tối ưu UI Shell (v2.6):**
  - **Đồng bộ thời gian thực:** Tính toán vị trí vuốt liên tục `currentContinuousPosition = currentPage + currentPageOffsetFraction` từ `PagerState` để điều khiển thanh BottomBar và Capsule Indicator chuyển động mượt theo ngón tay sinh viên.
  - **Tối ưu RenderThread GPU:** Sử dụng `Modifier.graphicsLayer` để điều khiển `scale`, `alpha` và `translation` trên kênh RenderThread của GPU, giữ nguyên `FontWeight` cố định nhằm triệt tiêu hoàn toàn việc re-measure text layout trên Main Thread khi chuyển Tab.
  - **Vật lý Spring Physics:** Tích hợp `spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)` cho hiệu ứng chuyển trang `HorizontalPager` tự nhiên, không bị khựng.
- **Cơ chế theo dõi tiến trình (Progress Tracking):** Truyền callback trạng thái từ `SyncRepository` qua `ScheduleViewModel` để hiển thị tiến độ chi tiết theo thời gian thực (real-time progress updates).
- **Cơ chế xử lý Đổi phòng học (v2.5+):**
  - `SyncRepository` đối chiếu trực tiếp `room` từ `PortalScheduleRepository` với cơ sở dữ liệu cục bộ (`EventStore`).
  - Khi phát hiện thay đổi phòng (`roomChanged = true`), hệ thống tự động cập nhật bản ghi địa điểm trên Google Calendar và đặt lại cờ thông báo.
  - `ReminderCheckWorker` kiểm tra định kỳ mỗi 15 phút, kích hoạt thông báo nhắc nhở phòng học mới trước **24 giờ** và **1 giờ** so với thời gian bắt đầu buổi học.

### 2. Các thư viện và công nghệ sử dụng (Stack & Dependencies)

- **Networking (Kết nối mạng):** `OkHttp` (gửi Request, xử lý Session/Cookie đăng nhập hệ thống UTH).
- **HTML Parsing (Trích xuất dữ liệu):** `Jsoup` (parse HTML trích xuất lịch học từ Moodle/Courses/THNN).
- **Background Task (Chạy ngầm):** `WorkManager` (`ScheduleSyncWorker` chạy đồng bộ định kỳ và `ReminderCheckWorker` kiểm tra nhắc nhở/đổi phòng mỗi 15 phút).
- **Local Storage (Lưu trữ):** `DataStore` (Lưu thông tin đăng nhập và danh sách `SyncedEvent` dưới dạng JSON).
- **Mã hóa bảo mật:** Mã hóa thông tin xác thực sinh viên bằng EncryptedSharedPreferences/CryptoManager.
