# DeepSeek Chat — Android WebView App

Простое Android-приложение, которое открывает https://chat.deepseek.com в WebView.
Работает на Huawei и любых Android-устройствах без Google Play Services.

## Что делает
- Загружает DeepSeek Chat в полноэкранном WebView.
- Включён JavaScript, DOM Storage, медиа.
- Кнопка «Назад» возвращает по истории WebView, иначе закрывает приложение.
- Поддержка загрузки файлов (вложения в чат).
- Внешние ссылки открываются в браузере.

## Сборка APK (вариант 1 — локально)
1. Установи Android Studio (https://developer.android.com/studio).
2. Открой папку `deepseek-chat-android` в Android Studio.
3. Подожди синхронизации Gradle.
4. Build → Build Bundle(s) / APK(s) → Build APK(s).
5. APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Сборка APK (вариант 2 — через GitHub Actions, без локального SDK)
1. Создай репозиторий на GitHub и загрузи эту папку.
2. Перейди во вкладку **Actions** → запусти workflow **Build APK** (или сделай push).
3. Скачай артефакт `deepseek-chat-apk` — внутри будет `app-debug.apk`.

## Установка на Huawei
1. Скопируй APK на телефон (USB, облако, мессенджер).
2. На телефоне: Настройки → Безопасность → разреши установку из неизвестных источников для файлового менеджера/браузера.
3. Открой APK и установи.
4. При первом запуске войди в свой аккаунт DeepSeek.

## Google-авторизация (синхронизация чатов)
Приложение использует desktop User-Agent и принимает сторонние cookies, чтобы Google OAuth работал внутри WebView. Войди в DeepSeek через «Sign in with Google» — чаты привяжутся к твоему аккаунту и будут доступны с ПК при входе в тот же аккаунт.

Если Google всё же покажет «This browser or app may not be secure»:
- Войди по email/паролю (всегда работает в WebView), или
- Открой https://chat.deepseek.com в Chrome на телефоне, войди через Google, затем вернись в приложение — сессия подхватится из общих cookies (если Chrome установлен).

## Примечания
- Приложение — обёртка над сайтом, интернет обязателен.
- Huawei AppGallery не требуется — APK ставится напрямую.
- minSdk 24 (Android 7.0+).