# QQ Mafia для Android

Приложение-оболочка над сайтом игры (https://mafia.qqhtml.online): тот же сайт в WebView, push-уведомления и автообновление.

**Скачать:** https://github.com/RPbo/qq-mafia-app/releases/latest/download/qq-mafia.apk

При установке Android спросит разрешение на установку из браузера или файлового менеджера. Это нужно один раз.

## Как работает
- Каждая сборка (`push` в `main`) собирается в GitHub Actions, подписывается постоянным ключом и выкладывается в Releases (`build-<номер>`).
- При запуске приложение смотрит последний релиз; если он новее, предлагает обновиться, скачивает APK и открывает установку.
  Сам сайт обновляется без релизов: он всегда открывается с сервера.
- «Назад» не закрывает игру, а сворачивает приложение. Партия продолжается, о ходах и фазах напомнит уведомление.

## Секреты репозитория (Settings → Secrets and variables → Actions)
| Секрет | Зачем |
|---|---|
| `KEYSTORE_B64` | ключ подписи APK в base64 (без него сборка не запустится) |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | параметры ключа |
| `GOOGLE_SERVICES_JSON` | файл `google-services.json` из Firebase в base64. Без него приложение собирается без push |

**Ключ подписи не теряйте и не меняйте:** обновления поверх установленного приложения возможны только с тем же ключом.

## Push-уведомления (Firebase)
1. console.firebase.google.com → создать проект → добавить Android-приложение с пакетом `online.qqhtml.mafia`.
2. Скачать `google-services.json`, превратить в base64 (`base64 -w0 google-services.json`) и сохранить как секрет `GOOGLE_SERVICES_JSON`.
3. Project settings → Service accounts → Generate new private key. Содержимое JSON целиком положить в переменную `FCM_SERVICE_ACCOUNT` сервера игры (Railway).
4. Запустить сборку (Actions → Build APK → Run workflow) и установить новую версию.

## Структура
`app/src/main/java/online/qqhtml/mafia`: `MainActivity` (WebView, вход через бота), `PushService` (приём push), `UpdateManager` (автообновление), `Notif` (каналы уведомлений).
