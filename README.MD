# UTH_SyncTask_Android (v2.6)

Một ứng dụng Android nhỏ gọn, siêu mượt mà giúp tự động cào lịch học từ hệ thống sinh viên Đại học Giao thông Vận tải TP.HCM (UTH) và đồng bộ trực tiếp vào lịch mặc định (Google Calendar) của điện thoại.

> Đây là phiên bản di động v2.6 được nâng cấp toàn diện từ nền tảng gốc [UTH_SyncTask](https://github.com/duynam2802/UTH_SyncTask).

## ✨ Tính năng chính (Minimal & Smooth)
- **Giao diện & Animation mượt mà 120fps (v2.6):** Thanh BottomBar phong cách One UI / Material 3 với hiệu ứng nền capsule active chuyển đổi mượt mà theo cử chỉ vuốt thực tế của sinh viên, tối ưu phần cứng GPU RenderThread giúp phản hồi tức thì và hoàn toàn không bị khựng/giật.
- **Đăng nhập 1 lần:** Lưu thông tin đăng nhập an toàn, không cần nhập lại lần sau.
- **Đăng nhập Portal trực quan:** Theo dõi tiến trình đăng nhập Portal qua thanh trạng thái thời gian thực ở đáy màn hình.
- **Đồng bộ một chạm (One-tap Sync):** Kích hoạt cào dữ liệu lịch học và deadline với dòng tiến trình chi tiết từng bước (Moodle Courses, THNN, Portal, Google Calendar).
- **Phát hiện & Đồng bộ đổi phòng học:** Tự động phát hiện khi hệ thống Portal đổi phòng học (ví dụ: `H205`), tự động cập nhật lại vào bộ nhớ máy và Google Calendar.
- **Thông báo đổi phòng học trước 24h & 1h:** Tự động gửi thông báo nhắc nhở đặc biệt trước 24 giờ và 1 giờ khi có lịch đổi phòng học để sinh viên không bị nhầm lẫn.
- **Tông màu Teal chủ đạo (`#008588`) & Logo tùy chỉnh:** Giao diện trực quan, đồng bộ màu sắc thương hiệu và hỗ trợ hình ảnh logo cá nhân hóa.
- **Tự động chạy ngầm (Auto-Sync):** Sử dụng WorkManager tự động kiểm tra và cập nhật lịch học mới định kỳ.

## 🛠️ Công nghệ sử dụng (Tech Stack)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Declarative UI with RenderThread GPU optimization & Physics Spring Animations)
- **Asynchronous:** Kotlin Coroutines
- **Networking:** OkHttp (Xử lý Session/Cookie)
- **HTML Parser:** Jsoup (Bóc tách dữ liệu lịch học từ HTML)
- **Background Task:** Android Jetpack WorkManager
- **Storage:** Jetpack DataStore (Mã hóa và lưu cấu hình)

## 📦 Cài đặt & Phát triển
1. Clone dự án về máy:
   ```bash
   git clone https://github.com/duynam2802/UTH_SyncTask_Android.git
   ```
