# Vietnam TV - Ứng dụng xem truyền hình VTV & HTV trực tiếp

## ⚙️ Cấu hình Android SDK (Dành cho máy mới clone lần đầu)

Nếu bạn vừa clone dự án về máy tính và gặp thông báo lỗi:

> `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path...`

Bạn sẽ tạo một file tên `local.properties` tại thư mục gốc của dự án (`VietnamTV\local.properties`) với nội dung sau:

```properties
sdk.dir=C:\\Android\\sdk
```

## 🚀 Hướng dẫn tự Build file APK

Mở PowerShell / Command Prompt tại thư mục dự án (`VietnamTV`):

### 1. Build bản Release APK (Khuyên dùng)

```powershell
.\gradlew.bat assembleRelease
```

- File APK hoàn chỉnh sẽ được tạo tại:
  `app\build\outputs\apk\release\app-release.apk`
- Bản build này đã được cấu hình sẵn signing tự động, sẵn sàng cài đặt và sử dụng ngay.
