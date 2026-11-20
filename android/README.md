# Urbex-T Android PDA Project

Модульный нативный Android-проект (Kotlin + Jetpack Compose + Material 3 + Room + DataStore + MapLibre) для автономного полевого PDA **Urbex-T**.

## Модульная структура

- `:app` — точка входа (`UrbexApplication`, `MainActivity`), тема Material 3, граф навигации и ленивое переключение разделов.
- `:core` — гео-математика (`GeoUtils`), формат `.urbexdb` (`UrbexDbSerializer`, совместимый с веб-версией), фреймы Meshtastic, адаптивный GPS-контроллер (`AdaptiveLocationClient`).
- `:domain` — `@Immutable`-модели данных, интерфейсы репозиториев и use-case логика слияния конфликтов.
- `:data` — база данных `Room` с индексами и транзакциями, `Preferences DataStore`, репозитории, `WorkManager` для отложенной проверки резервных копий и начальные данные сектора.
- `:feature-map` — офлайн-карта на MapLibre с жизненным циклом `MapView`, точками и привязкой к GPS.
- `:feature-objects` — список объектов на `LazyColumn` со стабильными ключами, фильтрами достоверности и карточкой редактирования.
- `:feature-mesh` — каналы Meshtastic, конструктор фреймов, прикрепление координат и подтверждаемый маяк `SOS`.
- `:feature-base` — офлайн-справочник и личные заметки.
- `:feature-archive` — локальный архив медиа и документов через Storage Access Framework (`OpenDocument`).
- `:feature-profile` — профиль оператора, Node ID и роль в группе.
- `:feature-settings` — профили энергопотребления GPS, формат координат, экспорт/импорт `.urbexdb` с проверкой контрольной суммы.

## Локальная сборка APK

1. Откройте папку `android/` в **Android Studio** (Ladybug / Meerkat или новее, JDK 17+).
2. Дождитесь Gradle Sync (зависимости загрузятся из Google Maven и Maven Central).
3. Соберите debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   Готовый файл будет в `app/build/outputs/apk/debug/app-debug.apk`.
4. Для профилирования и release-сборки с R8:
   ```bash
   ./gradlew :app:assembleRelease
   ```
5. Запуск unit-тестов логики (`:core`, `:domain`):
   ```bash
   ./gradlew :core:testReleaseUnitTest :domain:testReleaseUnitTest
   ```

## Автосборка APK через GitHub Actions

В репозиторий и архив включён готовый workflow `.github/workflows/build-apk.yml`.
1. Загрузите проект (или содержимое архива `urbex-t-android.zip`) в GitHub-репозиторий, сохранив папку `.github/workflows/build-apk.yml` в корне репозитория.
2. Перейдите во вкладку **Actions → Build Urbex-T Android APK** (запуск произойдёт автоматически при push в `main`/`master` или вручную кнопкой **Run workflow**).
3. После завершения откройте запуск и скачайте артефакт **`urbex-t-release-apk`** (подписанный релизный APK `app-release.apk`).

