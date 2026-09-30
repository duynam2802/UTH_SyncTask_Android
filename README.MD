# UTH_SyncTask_Android (v2.7)

Một ứng dụng Android nhỏ gọn, siêu mượt mà giúp tự động cào lịch học từ hệ thống sinh viên Đại học Giao thông Vận tải TP.HCM (UTH) và đồng bộ trực tiếp vào lịch mặc định (Google Calendar) của điện thoại.

> Đây là phiên bản di động v2.7 được nâng cấp toàn diện từ nền tảng gốc [UTH_SyncTask](https://github.com/duynam2802/UTH_SyncTask).

## ✨ Tính năng chính (Minimal & Smooth)
- **Giao diện & Animation mượt mà 120fps (v2.6+):** Thanh BottomBar phong cách One UI / Material 3 với hiệu ứng nền capsule active chuyển đổi mượt mà theo cử chỉ vuốt thực tế của sinh viên, tối ưu phần cứng GPU RenderThread giúp phản hồi tức thì và hoàn toàn không bị khựng/giật.
- **Xử lý & Thông báo Tạm ngưng học (v2.7):** Nhận diện các buổi học bị tạm ngưng (`isTamNgung: true`) từ Portal UTH, hiển thị badge đỏ **TẠM NGƯNG HỌC** cùng dòng gạch ngang tiêu đề, đồng thời gửi thông báo đẩy thông tin buổi nghỉ và tự động cập nhật nhãn `🚫 [TẠM NGƯNG]` lên Google Calendar.
- **Cảnh báo hết hạn Token Portal & Điều hướng 1-chạm (v2.7):** Tự động phát hiện khi phiên đăng nhập Portal bị hết hạn (Mã 401 / Invalid JWT), phát thông báo nhắc nhở và tự động chuyển thẳng người dùng tới màn hình Đăng nhập Portal UTH khi chạm vào thông báo.
- **Phát hiện & Đồng bộ đổi phòng học:** Tự động phát hiện khi hệ thống Portal đổi phòng học (ví dụ: `H205`), tự động cập nhật lại vào bộ nhớ máy và Google Calendar.
- **Thông báo đổi phòng học trước 24h & 1h:** Tự động gửi thông báo nhắc nhở đặc biệt trước 24 giờ và 1 giờ khi có lịch đổi phòng học để sinh viên không bị nhầm lẫn.
- **Đăng nhập 1 lần:** Lưu thông tin đăng nhập an toàn, không cần nhập lại lần sau.
- **Đồng bộ một chạm (One-tap Sync):** Kích hoạt cào dữ liệu lịch học và deadline với dòng tiến trình chi tiết từng bước (Moodle Courses, THNN, Portal, Google Calendar).
- **Tông màu Teal chủ đạo (`#008588`) & Logo tùy chỉnh:** Giao diện trực quan, đồng bộ màu sắc thương hiệu và hỗ trợ hình ảnh logo cá nhân hóa.
- **Tự động chạy ngầm (Auto-Sync):** Sử dụng WorkManager tự động kiểm tra và cập nhật lịch học mới định kỳ.

## 🛠️ Công nghệ sử dụng (Tech Stack)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Declarative UI with RenderThread GPU optimization & Physics Spring Animations)
- **Asynchronous:** Kotlin Coroutines
- **Networking:** OkHttp, Retrofit & Gson (Xử lý Session/Cookie và API JSON Portal)
- **HTML Parser:** Jsoup (Bóc tách dữ liệu lịch học từ HTML Moodle/Courses/THNN)
- **Background Task:** Android Jetpack WorkManager
- **Storage:** Jetpack DataStore & EncryptedSharedPreferences (Mã hóa và lưu cấu hình an toàn)

## 📦 Cài đặt & Phát triển
1. Clone dự án về máy:
   ```bash
   git clone https://github.com/duynam2802/UTH_SyncTask_Android.git
   ```
