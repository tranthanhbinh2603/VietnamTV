# Vietnam TV - Ứng dụng xem truyền hình VTV & HTV trực tiếp

Ứng dụng xem truyền hình trực tuyến dành cho Android & Android TV, phát trực tiếp các kênh VTV và HTV chọn lọc với trải nghiệm toàn màn hình đỉnh cao, khả năng chạy âm thanh nền mượt mà và kiểm tra tín hiệu động.

---

## 🌟 Tính năng chính

1. **Lấy danh sách kênh động từ M3U Online**:
   - Tự động tải danh sách kênh mới nhất từ kho: `https://raw.githubusercontent.com/khanh71/All-In-One-IPTV/main/http-iptv.m3u`.
   - Danh sách không bị cố định cứng trong mã nguồn (mọi cập nhật kênh, luồng phát hay logo từ M3U đều được phản ánh tức thì).
2. **Bộ lọc kênh chuẩn xác**:
   - **VTV**: Lấy toàn bộ các kênh thuộc đài VTV (VTV1, VTV2, VTV3, VTV4, VTV5, VTV5 Tây Nam Bộ, VTV5 Tây Nguyên, VTV6, VTV7, VTV8, VTV9, VTV10,...).
   - **HTV**: Chỉ lấy các kênh có số từ **1 đến 9** (HTV1, HTV2, HTV3, HTV4, HTV5, HTV7, HTV9). Loại bỏ hoàn toàn các kênh tạp khác.
3. **Thứ tự ưu tiên hàng đầu**:
   - Thứ tự hiển thị 3 kênh đầu tiên luôn là: **VTV1 ➔ HTV9 ➔ HTV7**.
   - Các kênh còn lại sắp xếp theo thứ tự thông thường.
   - Không gắn nhãn, icon đánh dấu thừa hay gây rối mắt.
4. **Tự động phát và kiểm tra luồng thông minh**:
   - Khi khởi động, app tự động tải và phát ngay kênh **VTV1**.
   - Nếu VTV1 có tín hiệu, phát liền mạch cho người xem; nếu luồng chết, tự động chuyển ngay sang kênh khả dụng tiếp theo.
   - Trong lúc người dùng đang thưởng thức kênh đang phát, tiến trình nền tự động dò kiểm tra (Health Check) toàn bộ các kênh còn lại mà không gây nghẽn băng thông.
   - **Ẩn kênh mất tín hiệu**: Bất kỳ kênh nào kiểm tra thấy không khả dụng / luồng chết sẽ tự động bị ẩn hoàn toàn khỏi thanh chọn kênh (không để nút xám hay nút disable).
5. **Giao diện Full màn hình tối giản & Thao tác vuốt**:
   - Khi vào ứng dụng: Mặc định hiển thị **full màn hình 100%** không viền, không thanh chọn kênh, không bảng menu che khuất hình ảnh.
   - **Vuốt lên (Swipe Up)** hoặc chạm vào màn hình: Thanh chọn kênh ngang sẽ trượt lên gọn gàng ở cạnh dưới để người dùng bấm chọn kênh nhanh chóng.
   - **Vuốt xuống (Swipe Down)** hoặc sau vài giây không chạm: Thanh chọn kênh tự động ẩn đi để trả lại không gian xem phim toàn màn hình.
6. **Phát âm thanh ngầm (Background Audio) & Không mở PiP**:
   - Thoát ra màn hình chính (Home) hoặc chuyển sang ứng dụng khác: Kênh **tiếp tục phát âm thanh nền** liên tục thông qua Foreground Media Service chuẩn Android.
   - **Tuyệt đối không bật cửa sổ thu nhỏ (Picture-in-Picture)** che màn hình.
   - Khi mở lại ứng dụng: Hình ảnh video lập tức tự động hiển thị tiếp tục đồng bộ cùng âm thanh mà không bị gián đoạn.

---

## 🛠 Yêu cầu môi trường phát triển & Build

- **Hệ điều hành**: Windows 10/11, macOS hoặc Linux.
- **Java**: OpenJDK 17 trở lên.
- **Android SDK**: Compile SDK 34, Target SDK 34, Min SDK 24.
- **Gradle**: Bản 8.13 (đã tích hợp sẵn Gradle Wrapper trong dự án).
- **ADB**: Đã cấu hình tại `C:\adb` hoặc có sẵn trong biến môi trường `PATH`.

---

## ⚙️ Cấu hình Android SDK (Dành cho máy mới clone lần đầu)

Nếu bạn vừa clone dự án về máy tính và gặp thông báo lỗi:
> `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path...`

Hãy thực hiện một trong các cách sau:

### Cách 1: Tự động phát hiện (Đã tích hợp trong `settings.gradle.kts`)
Dự án đã được tích hợp bộ tự động dò tìm Android SDK ở các đường dẫn thông dụng (`C:\Android\sdk`, biến môi trường `ANDROID_HOME`, hoặc thư mục SDK của Android Studio). Nếu SDK nằm ở các vị trí này, Gradle sẽ tự động tạo `local.properties` cho bạn.

### Cách 2: Tạo file `local.properties` thủ công
Tạo một file tên `local.properties` tại thư mục gốc của dự án (`VietnamTV\local.properties`) với nội dung sau:
```properties
sdk.dir=C:\\Android\\sdk
```
*(Nếu Android SDK của bạn nằm ở thư mục khác, hãy thay đổi đường dẫn tương ứng và dùng 2 dấu gạch chéo `\\`)*.

### Cách 3: Đặt biến môi trường hệ thống trong PowerShell
Mở PowerShell và chạy lệnh sau (chỉ cần chạy 1 lần duy nhất):
```powershell
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android\sdk", "User")
[System.Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "C:\Android\sdk", "User")
```

---

## 🚀 Hướng dẫn tự Build file APK

Mở PowerShell / Command Prompt tại thư mục dự án (`VietnamTV`):

### 1. Build bản Release APK (Khuyên dùng)
```powershell
.\gradlew.bat assembleRelease
```
- File APK hoàn chỉnh sẽ được tạo tại:
  `app\build\outputs\apk\release\app-release.apk`
- Bản build này đã được cấu hình sẵn signing tự động, sẵn sàng cài đặt và sử dụng ngay.

### 2. Build bản Debug APK
```powershell
.\gradlew.bat assembleDebug
```
- File APK được tạo tại:
  `app\build\outputs\apk\debug\app-debug.apk`

### 3. Dọn dẹp cache và build lại từ đầu (Clean Build)
```powershell
.\gradlew.bat clean assembleRelease
```

---

## 📱 Hướng dẫn cài đặt và chạy thử qua ADB

1. **Kiểm tra thiết bị kết nối**:
   ```powershell
   & C:\adb\adb.exe devices
   ```

2. **Cài đặt file APK trực tiếp vào thiết bị**:
   ```powershell
   & C:\adb\adb.exe install -r "VietnamTV.apk"
   ```

3. **Khởi chạy ứng dụng**:
   ```powershell
   & C:\adb\adb.exe shell am start -n com.vietnam.tvlive/.MainActivity
   ```

---

## 📦 Cấu trúc dự án

```
VietnamTV/
├── app/
│   ├── src/main/
│   │   ├── java/com/vietnam/tvlive/
│   │   │   ├── adapter/ChannelHorizontalAdapter.kt # Quản lý danh sách kênh ngang & ẩn kênh chết
│   │   │   ├── model/Channel.kt                   # Data model kênh và trạng thái
│   │   │   ├── network/M3uParser.kt               # Lấy & lọc động VTV/HTV, ưu tiên VTV1 -> HTV9 -> HTV7
│   │   │   ├── network/ChannelHealthChecker.kt    # Tiến trình nền kiểm tra luồng phát sống/chết
│   │   │   ├── service/TvPlayerService.kt         # Dịch vụ Foreground phát ExoPlayer & âm thanh nền
│   │   │   ├── MainActivity.kt                    # Giao diện chính, cử chỉ vuốt, tối giản toàn màn hình
│   │   │   └── TvApplication.kt                   # Khởi tạo Notification Channel
│   │   ├── res/                                   # Giao diện XML, màu sắc, icon TV, selector
│   │   └── AndroidManifest.xml                    # Khai báo quyền, Service & tắt Picture-in-Picture
│   └── build.gradle.kts                           # Cấu hình Media3 ExoPlayer, OkHttp, Glide
├── gradle/wrapper/                                # Gradle Wrapper 8.13
├── gradlew.bat                                    # Script build trên Windows
├── build.gradle.kts                               # Cấu hình root Gradle
├── settings.gradle.kts                            # Cấu hình auto-detect SDK & repositories
├── local.properties                               # Khai báo đường dẫn SDK máy hiện tại
├── VietnamTV.apk                                  # File APK hoàn chỉnh cuối cùng đã build sẵn
└── README.md                                      # Tài liệu hướng dẫn sử dụng & build
```
