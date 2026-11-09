# MyMusicPlayer

Современный, легкий и красивый музыкальный плеер для Android, созданный с использованием Material Design 3.

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue.svg)
![Material3](https://img.shields.io/badge/UI-Material%203-green.svg)
![Media3](https://img.shields.io/badge/Media-ExoPlayer-orange.svg)

## Описание

MyMusicPlayer — это нативное Android-приложение, разработанное для обеспечения бесшовного и визуально привлекательного прослушивания музыки. Приложение полностью автономно, сканирует локальное хранилище устройства и использует современные API Android для фоновой работы и управления через системные уведомления.

## ✨ Особенности

* **Material Design 3:** Современный, чистый интерфейс с правильными скруглениями, тенями и адаптивной типографикой.
* **Фоновое воспроизведение:** Непрерывная работа плеера с использованием AndroidX Media3 (ExoPlayer) и MediaSessionService.
* **Системные уведомления:** Rich Media Notifications с элементами управления воспроизведением и обложкой альбома.
* **Мини-плеер:** Постоянная панель внизу экрана для быстрого управления без выхода из списка треков.
* **Полноэкранный плеер:** Иммерсивный экран с большим слайдером перемотки, крупными кнопками управления и анимированной обложкой.
* **Умная загрузка изображений:** Эффективная загрузка и кэширование обложек с помощью библиотеки Coil.
* **Fallback-обложки:** Автоматическая подстановка красивой заглушки из кэша, если у трека нет встроенной обложки.

## 📸 Скриншоты

| Главный экран | Полноэкранный плеер | Начальная настройка |
| :---: | :---: |
| ![Home](screnshots/home.jpg) | ![Player](screnshots/player.jpg) | ![Player](screnshots/setup.jpg) |

## 🛠️ Технологический стек

* **Язык:** Kotlin
* **Архитектура:** Activity-based с выделенным MediaSessionService
* **UI:** XML, Material Design 3 Components (CardView, Slider, FAB, ShapeableImageView)
* **Воспроизведение:** AndroidX Media3 (ExoPlayer)
* **Загрузка изображений:** Coil
* **Асинхронность:** Kotlin Coroutines

## 🚀 Начало работы

### Требования

* Android Studio Iguana / Jellyfish (или новее)
* JDK 17+
* Android SDK 34+ (Android 14)

### Установка
1. Клонируйте репозиторий:

```bash
git clone https://github.com/your-username/MyMusicPlayer.git
```
2. Откройте проект в Android Studio.
3. Дождитесь синхронизации Gradle.
4. Запустите приложение на эмуляторе или физическом устройстве.

## 📂 Структура проекта

```text
app/src/main/kotlin/ru/deltagroup/mymusicplayer/
//MainActivity.kt - Главный экран со списком треков и мини-плеером
//PlayerActivity.kt - Полноэкранный иммерсивный плеер
//MusicService.kt - Фоновое воспроизведение и управление MediaSession
//PlaylistManager.kt - Формирование MediaItems и обработка fallback-обложек
//SongAdapter.kt - Адаптер RecyclerView со стилизацией Material 3
//Utils.kt - Утилиты (например, получение цветов из темы)
```

## ⚙️ Конфигурация

Для корректной работы убедитесь, что в `AndroidManifest.xml` предоставлены необходимые разрешения:

```xml
//Разрешение для чтения аудиофайлов (Android 13+)
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
//Разрешение для старых версий Android
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
//Разрешение для фоновой работы
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

## 🤝 Вклад в проект
Pull Request'ы приветствуются! Если у вас есть идеи по улучшению UI или добавлению новых функций, создавайте Issue или Fork репозитория.

## 📄 Лицензия
//Этот проект распространяется под лицензией MIT. См. файл LICENSE для подробностей.