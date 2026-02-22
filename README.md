# Reader - Modern Reddit Client for Android

A modern, feature-rich Reddit client built with Kotlin Multiplatform (KMP) and Jetpack Compose. Inspired by Reddit is Fun (RIF) but rebuilt with modern Android development practices.

## Features

### Core Reddit Functionality
- **Feed Browsing** - Browse Reddit's front page, popular, all, and subreddit feeds with customizable sorting
- **Post Viewing** - View posts with full comment threads, nested comments, and real-time updates
- **Voting & Saving** - Upvote, downvote, and save posts/comments with instant feedback
- **Comment System** - Reply to posts and comments, edit/delete own comments, draft support
- **Subreddit Management** - Browse, subscribe, and manage subreddits with sidebar information
- **User Profiles** - View user profiles, karma, post history, and comment history
- **Inbox & Messages** - Read and manage private messages, comment replies, and mentions with push notifications
- **Search** - Comprehensive search for posts, communities, and users
- **OAuth2 Authentication** - Secure Reddit login with proper token management

### Advanced Features
- **Rich Media Support** - Images, GIFs, videos (including HLS streams), and YouTube integration
- **Full-Screen Media** - Immersive image and video viewing with gesture controls
- **Web Browser** - Built-in browser for external links with reader mode
- **Markdown Rendering** - Complete markdown support including tables, code blocks, spoilers, and nested elements
- **NSFW Controls** - Granular NSFW preview and history settings
- **Notifications** - Background inbox polling with configurable intervals
- **Video Caching** - Smart video caching for improved performance
- **Quote Sharing** - Quote and share text from posts and comments
- **Settings** - Comprehensive app settings with theme preferences and behavior controls

## Tech Stack

### Shared (Kotlin Multiplatform)
- **Ktor 3.4.0** - HTTP client for API requests with auth and logging
- **Kotlinx Serialization 1.10.0** - JSON parsing and serialization
- **Kotlinx Coroutines 1.10.2** - Async operations and concurrency
- **Koin 4.1.1** - Dependency injection across platforms
- **Multiplatform Settings 1.3.0** - Key-value storage with coroutine support
- **Kotlinx DateTime 0.7.1** - Date and time handling
- **Napier 2.7.1** - Multiplatform logging
- **Lifecycle ViewModel 2.9.6** - KMP-compatible ViewModels

### Android
- **Jetpack Compose BOM 2026.02.00** - Modern declarative UI framework
- **Material 3** - Latest Material Design components and theming
- **Coil 3.3.0** - Image loading with GIF support and Ktor integration
- **Navigation Compose 2.9.7** - Type-safe navigation
- **Media3 1.9.2** - ExoPlayer for video playback and HLS streaming
- **WorkManager 2.11.1** - Background processing and notifications
- **Android YouTube Player 13.0.0** - Native YouTube video integration

### Testing & Quality
- **Comprehensive Test Suite** - 75+ unit tests covering repositories, ViewModels, and utilities
- **JaCoCo Coverage** - Code coverage reporting for Android tests
- **Ktor Mock Client** - HTTP client mocking for API testing
- **Integration Tests** - Real API integration testing with test environment

## Project Structure

```
├── shared/                           # Kotlin Multiplatform shared code
│   ├── commonMain/                   # Common code for all platforms
│   │   ├── domain/model/             # Data models (Post, Comment, User, etc.)
│   │   ├── domain/markdown/          # Markdown domain models (Block, Inline)
│   │   ├── data/api/                 # Reddit API client, AuthManager, DTOs
│   │   ├── data/repository/          # Repository layer with caching logic
│   │   ├── ui/                       # Shared ViewModels and state management
│   │   ├── util/markdown/            # Custom markdown parser (Block, Inline)
│   │   ├── util/                     # Utilities (LinkParser, etc.)
│   │   └── di/                       # Dependency injection setup
│   ├── commonTest/                   # Shared unit tests
│   ├── androidHostTest/              # Android integration tests
│   ├── androidMain/                  # Android-specific implementations
│   └── iosMain/                      # iOS-specific implementations (future)
│
├── androidApp/                       # Android application
│   └── src/main/
│       ├── kotlin/.../ui/            # Compose UI screens and components
│       │   ├── feed/                 # Feed browsing and post lists
│       │   ├── post/                 # Post detail and comment threads
│       │   ├── subreddit/            # Subreddit views and management
│       │   ├── profile/              # User profiles and history
│       │   ├── inbox/                # Messages and notifications
│       │   ├── search/               # Search functionality
│       │   ├── settings/             # App settings and preferences
│       │   ├── components/           # Reusable UI components
│       │   │   ├── MarkdownText.kt   # Markdown rendering component
│       │   │   ├── PostCard.kt       # Post list item
│       │   │   ├── CommentItem.kt    # Comment display
│       │   │   ├── VideoPlayer.kt    # Video playback
│       │   │   └── ...               # Media, notifications, etc.
│       │   └── theme/                # Material 3 theme and typography
│       └── res/                      # Android resources
│
└── gradle/                           # Gradle configuration and version catalog
```

## Setup

### Prerequisites
- Android Studio Otter 3 or later (minimum for AGP 9.0.1)
- JDK 25+
- Android SDK 36 (API level 36)
- Gradle 9.3.1+

### Reddit API Setup
1. Visit https://www.reddit.com/prefs/apps
2. Click "Create App" or "Create Another App"
3. Select "installed app" type
4. Set redirect URI to `reader://oauth`
5. Copy the client ID (14-character string)

### Test Configuration
Create a `.env` file in the project root:
```env
REDDIT_CLIENT_ID=your_client_id_here
```

### Build & Run
```bash
# Debug build
./gradlew :androidApp:assembleDebug

# Install debug APK
./gradlew :androidApp:installDebug

# Run tests
./gradlew test

# Generate test coverage report
./gradlew :shared:jacocoTestReport
```

## Development

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew :shared:testDebugUnitTest --tests "com.reader.shared.data.repository.PostRepositoryTest"

# Generate coverage report (outputs to shared/build/reports/jacoco/)
./gradlew :shared:jacocoTestReport
```

### Code Style
- Follow Kotlin official coding conventions
- Use Compose naming conventions for UI components
- Repository pattern for data access
- MVVM architecture with ViewModels
- Dependency injection with Koin

## AI Disclosure

This project contains source code that was generated with the assistance of artificial intelligence tools. The AI was used for:

- **Code Generation**: Writing boilerplate code, implementing features, and creating test cases
- **Code Refactoring**: Improving code structure and applying best practices
- **Documentation**: Generating documentation and code comments
- **Debugging**: Identifying and fixing bugs

### What's NOT AI-generated:
- **Assets**: All images, icons, graphics, and visual assets are human-created or sourced from legitimate asset libraries
- **Design Decisions**: Architecture choices, feature specifications, and user experience decisions are made by human developers
- **Final Review**: All AI-generated code is reviewed, tested, and approved by human developers before inclusion

The AI tools serve as development assistants to accelerate coding while maintaining quality and human oversight of the final product.

## License

GNU General Public License v3 (GPLv3) - see LICENSE file for details