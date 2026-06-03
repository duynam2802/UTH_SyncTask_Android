# HỒ SƠ KỸ THUẬT (TECHNICAL PROFILE)

### 1. Kiến trúc ứng dụng (Architecture)

- **Kiến trúc:** MVVM (Model-View-ViewModel) kết hợp với Clean Architecture đơn giản.
- **UI Framework:** Jetpack Compose (Declarative UI giúp giao diện mượt và dung lượng app cực nhẹ).
- **Định dạng dữ liệu đồng bộ:** iCalendar (`.ics`) để tương thích với Google Calendar/Device Calendar.

### 2. Các thư viện và công nghệ sử dụng (Stack & Dependencies)

- **Networking (Kết nối mạng):** `OkHttp` (để gửi Request, xử lý Session/Cookie khi đăng nhập vào hệ thống UTH).
- **HTML Parsing (Trích xuất dữ liệu):** `Jsoup` (thư viện Java/Kotlin tốt nhất để parse HTML, tìm các thẻ chứa lịch học tương đương BeautifulSoup của Python).
- **Background Task (Chạy ngầm):** `WorkManager` (Đảm bảo tác vụ tự động đồng bộ chạy đúng giờ, tiết kiệm pin và không bị Android "giết" ngầm).
- **Local Storage (Lưu trữ):** `DataStore` (Lưu thông tin đăng nhập đã mã hóa hoặc cấu hình đồng bộ một cách nhẹ nhàng, thay thế cho SharedPreferences cũ).
- **Mã hóa bảo mật:** `EncryptedSharedPreferences` hoặc thư viện mã hóa của Jetpack để bảo vệ mật khẩu sinh viên.