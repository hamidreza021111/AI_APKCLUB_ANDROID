# ساخت APK با GitHub Actions — AI APKCLUB

این پوشه برای ساخت آنلاین APK آماده شده است. نیازی نیست روی ویندوز Gradle، JDK یا Android Studio را درست کنید.

## آپلود در GitHub

1. در GitHub یک Repository جدید بسازید؛ مثلاً `ai-apkclub-android`.
2. ZIP را روی کامپیوتر Extract کنید.
3. داخل Repository گزینه `uploading an existing file` را بزنید.
4. **محتویات داخل پوشه** `AI_APKCLUB_GITHUB_READY` را آپلود کنید، نه خود فایل ZIP.
   حتماً پوشه مخفی `.github` هم آپلود شود؛ Workflow داخل آن قرار دارد.
5. Commit changes را بزنید.

## گرفتن APK

1. وارد تب `Actions` در Repository شوید.
2. Workflow با نام `Build AI APKCLUB APK` را باز کنید.
3. اگر خودکار اجرا نشده بود، `Run workflow` را بزنید.
4. وقتی تیک سبز شد، صفحه همان Run را باز کنید.
5. پایین صفحه، از بخش `Artifacts` فایل `AI-APKCLUB-APK` را دانلود کنید.
6. فایل ZIP دانلودی GitHub را باز کنید؛ داخل آن `app-debug.apk` قرار دارد.

## امنیت

API Key را داخل سورس یا GitHub قرار ندهید. برنامه بعد از نصب، کلید را از کاربر دریافت می‌کند و سورس این پروژه کلید شخصی شما را ندارد.

## تنظیمات Build

- Android Gradle Plugin: 8.9.0
- Gradle: 8.11.1
- Java: 17
- Compile / Target SDK: 35
- خروجی: APK آزمایشی قابل نصب (`app-debug.apk`)
