# Etoile CCTV
عارض ٦٤ كاميرا Hikvision (NVR/DVR) عبر RTSP.

## التشغيل
1. افتح المجلد في Android Studio (Hedgehog أو أحدث) واستنى الـ Gradle Sync.
2. Build > Build APK(s) ثم ثبّت الـ APK على الـ TV box.
3. أول تشغيل: ادخل IP الـ NVR، الـ port (554)، اليوزر والباسورد.
4. فعّل RTSP على الـ NVR لو مقفول، واتأكد إن الباسورد مش فيه رموز غريبة.

## الإعدادات المناسبة لـ TV box بـ ٢ رام
- Cameras per page: 9 أو 16 (مش أكتر)
- الـ sub-stream على الـ NVR: CIF أو D1، بيت ريت 256-512kbps، H.264
- Auto-switch: 10 ثواني لو عايز تبديل تلقائي

## الشكل
- الواجهة والأيقونة: app/src/main/res/drawable/logo.jpg
- الألوان: Navy #1F3A4F / Gold #C29F5C / Cream #FFFDF0 (من اللوجو)
