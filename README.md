# Reader - Reddit Client for Android

A modern Reddit client built with Kotlin Multiplatform (KMP), inspired by Reddit is Fun (RIF).

## Features

- **Feed Browsing** - Browse Reddit's front page, popular, and subreddit feeds
- **Post Viewing** - View posts with full comment threads
- **Voting & Saving** - Upvote, downvote, and save posts/comments
- **Subreddit Management** - Browse and subscribe to subreddits
- **User Profiles** - View user profiles and karma
- **Inbox** - Read and manage messages
- **Search** - Search for posts and communities
- **OAuth2 Authentication** - Secure login with Reddit

## Tech Stack

### Shared (Kotlin Multiplatform)
- **Ktor** - HTTP client for API requests
- **Kotlinx Serialization** - JSON parsing
- **Kotlinx Coroutines** - Async operations
- **Koin** - Dependency injection
- **SQLDelight** - Local database (for caching)
- **Multiplatform Settings** - Key-value storage

### Android
- **Jetpack Compose** - Modern declarative UI
- **Material 3** - Material Design components
- **Coil** - Image loading
- **Navigation Compose** - Navigation

## Project Structure

```
├── shared/                    # Kotlin Multiplatform shared code
│   ├── commonMain/           # Common code for all platforms
│   │   ├── domain/model/     # Data models (Post, Comment, etc.)
│   │   ├── data/api/         # Reddit API client & DTOs
│   │   ├── data/repository/  # Repository layer
│   │   └── di/               # Dependency injection
│   ├── androidMain/          # Android-specific implementations
│   └── iosMain/              # iOS-specific implementations
│
├── androidApp/               # Android application
│   └── src/main/
│       ├── kotlin/.../ui/    # Compose UI screens
│       │   ├── feed/         # Feed screen
│       │   ├── post/         # Post detail screen
│       │   ├── subreddit/    # Subreddit screens
│       │   ├── profile/      # Profile screen
│       │   ├── inbox/        # Inbox screen
│       │   ├── search/       # Search screen
│       │   └── components/   # Reusable components
│       └── res/              # Android resources
│
└── gradle/                   # Gradle configuration
```

## Setup

### Prerequisites
- Android Studio Otter 3 or later
- JDK 25+
- Android SDK 36

### Reddit API Setup
1. Create a Reddit app at https://www.reddit.com/prefs/apps
2. Select "installed app" type
3. Set redirect URI to `reader://oauth`
4. Copy the client ID

### Configuration
Update the client ID in `AuthManager.kt`:
```kotlin
private const val CLIENT_ID = "your_client_id_here"
```

### Build & Run
```bash
./gradlew :androidApp:assembleDebug
```

## License

GPLv3 License