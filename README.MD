# UTH_SyncTask_Android

Một ứng dụng Android nhỏ gọn, mượt mà giúp tự động cào lịch học từ hệ thống sinh viên Đại học Giao thông Vận tải TP.HCM (UTH) và đồng bộ trực tiếp vào lịch mặc định (Google Calendar/Samsung Calendar) của điện thoại.

> Đây là phiên bản di động được tối ưu từ dự án gốc chạy bằng Python [UTH_SyncTask](https://github.com/duynam2802/UTH_SyncTask).

## ✨ Tính năng chính (Minimal & Smooth)
- **Đăng nhập 1 lần:** Lưu thông tin đăng nhập an toàn, không cần nhập lại lần sau.
- **Một chạm (One-tap Sync):** Nhấn nút để kích hoạt cào dữ liệu và cập nhật lịch ngay lập tức.
- **Tự động chạy ngầm (Auto-Sync):** Sử dụng WorkManager tự động kiểm tra và cập nhật lịch học mới định kỳ mà không cần mở app.
- **Nhẹ & Mượt:** Xây dựng hoàn toàn bằng Jetpack Compose, không thư viện thừa, dung lượng APK tối thiểu.

## 🛠️ Công nghệ sử dụng (Tech Stack)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Declarative UI)
- **Asynchronous:** Kotlin Coroutines
- **Networking:** OkHttp (Xử lý Session/Cookie)
- **HTML Parser:** Jsoup (Bóc tách dữ liệu lịch học từ HTML)
- **Background Task:** Android Jetpack WorkManager
- **Storage:** Jetpack DataStore (Mã hóa và lưu cấu hình)

## 🎯 Lộ trình phát triển (Roadmap)
- [ ] **Phase 1:** Khởi tạo Project & Thiết kế UI cơ bản bằng Compose.
- [ ] **Phase 2:** Viết Module Đăng nhập & Parser bằng Jsoup (Core logic).
- [ ] **Phase 3:** Tích hợp Android Calendar Provider để ghi dữ liệu vào lịch máy.
- [ ] **Phase 4:** Thiết lập WorkManager để tự động đồng bộ hóa ngầm.
- [ ] **Phase 5:** Tối ưu hóa hiệu năng, đóng gói file `.apk`.

## 📦 Cài đặt & Phát triển
1. Clone dự án về máy:
   ```bash
   git clone [https://github.com/YOUR_USERNAME/UTH_SyncTask_Android.git](https://github.com/YOUR_USERNAME/UTH_SyncTask_Android.git)