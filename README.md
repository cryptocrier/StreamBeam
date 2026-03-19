# StreamBeam


A premium native Android streaming app with built-in Stremio addon support, Real-Debrid integration, and seamless Chromecast casting.

## ✨ Features

- **Stremio Addons** - Supports Torrentio, Comet, and custom addons
- **Real-Debrid Integration** - Premium torrent caching for instant streaming
- **Native Chromecast** - First-class casting support using Google Cast SDK
- **Modern UI** - Built with Jetpack Compose
- **ExoPlayer** - Hardware-accelerated video playback

## 🏗️ Project Structure

```
app/
├── src/main/java/com/streambeam/
│   ├── MainActivity.kt              # Main entry point
│   ├── StremioCloneApp.kt           # Application class
│   ├── model/                        # Data models
│   │   ├── StremioModels.kt         # Stremio API models
│   │   └── RealDebridModels.kt      # Real-Debrid API models
│   ├── addons/                       # Stremio addon integration
│   │   ├── StremioAddonApi.kt       # Addon API interface
│   │   └── AddonManager.kt          # Addon client manager
│   ├── realdebrid/                   # Real-Debrid integration
│   │   ├── RealDebridApi.kt         # RD API interface
│   │   └── RealDebridManager.kt     # RD operations
│   ├── cast/                         # Chromecast support
│   │   ├── CastManager.kt           # Cast functionality
│   │   └── CastOptionsProvider.kt   # Cast configuration
│   ├── ui/                           # User interface
│   │   ├── screens/                 # Compose screens
│   │   │   ├── HomeScreen.kt        # Movie grid
│   │   │   ├── PlayerScreen.kt      # Video player
│   │   │   └── SettingsScreen.kt    # Configuration
│   │   ├── state/                   # UI state management
│   │   │   ├── PlayerState.kt       # Player sealed classes
│   │   │   └── StreamLoadingState.kt
│   │   └── theme/                   # Material theme
│   ├── viewmodel/
│   │   ├── MainViewModel.kt         # App state management
│   │   └── PlayerViewModel.kt       # Player screen state
│   └── util/                        # Utilities
│       └── Constants.kt             # App constants
```

## 🚀 Setup Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin 1.9+
- Real-Debrid account (premium recommended)

### Build Steps

1. **Open in Android Studio**
   ```bash
   cd stremio-clone
   ```
   Open the project in Android Studio

2. **Sync Gradle**
   - Let Android Studio download dependencies
   - This may take a few minutes

3. **Get Real-Debrid API Token**
   - Go to https://real-debrid.com/apitoken
   - Copy your API token

4. **Run the App**
   - Connect your Android device or start an emulator
   - Click "Run" in Android Studio

## 📱 Usage

### First Launch
1. Open Settings (gear icon in top right)
2. Enter your Real-Debrid API token
3. Tap "Save"

### Browsing Content
- Home screen shows movies from Torrentio addon
- Tap any movie poster to load available streams

### Playing Content
- Tap a stream to play locally
- Or tap the Cast button to cast to Chromecast
- Streams are cached through Real-Debrid for instant playback

### Casting to Chromecast
1. Tap the Cast icon in the top bar
2. Select your Chromecast device
3. Select a stream - it will play on your TV
4. Control playback from your phone

## 🔧 Configuration

### Adding Custom Addons
Edit `AddonManager.kt`:
```kotlin
val builtinAddons = listOf(
    AddonConfig(
        name = "Your Addon",
        url = "https://your-addon-url.com/",
        enabled = true
    )
)
```

### Supported Addons
- **Torrentio** - `https://torrentio.strem.fun/`
- **Comet** - `https://comet.elfhosted.com/`
- Any Stremio-compatible addon

## 🛠️ Technical Details

### Architecture
- **UI Layer**: Jetpack Compose with Material3
- **State Management**: Sealed classes + StateFlow
- **Network**: Retrofit + OkHttp
- **Video**: ExoPlayer with Cast extension
- **Casting**: Google Cast SDK v3

### Design Patterns
- **PlayerState** - Sealed class for player states (Idle, Buffering, Playing, Paused, Error)
- **StreamLoadingState** - Sealed class for stream loading states
- **MVVM** - ViewModel pattern for state management

### Key Dependencies
- `androidx.media3:media3-exoplayer:1.2.0` - Video playback
- `androidx.media3:media3-cast:1.2.0` - Chromecast integration
- `com.google.android.gms:play-services-cast-framework:21.4.0` - Cast SDK
- `com.squareup.retrofit2:retrofit:2.9.0` - HTTP client
- `io.coil-kt:coil-compose:2.5.0` - Image loading

## 🎨 Branding

**StreamBeam** features a sleek green/grey/dark grey theme matching the logo:
- Primary: `#22C170` (Green)
- Background: `#141414` (Dark)
- Surface: `#1F1F1F` (Dark Grey)
- Accent: `#54B9C5` (Blue)

Icon design incorporates radiating signal waves representing streaming/casting.

## 📝 API References

### Stremio Addon Protocol
- Manifest: `/manifest.json`
- Catalogs: `/catalog/{type}/{id}.json`
- Streams: `/stream/{type}/{id}.json`

### Real-Debrid API
- Base URL: `https://api.real-debrid.com/rest/1.0/`
- Authentication: Bearer token
- Docs: https://api.real-debrid.com/

## ⚠️ Important Notes

- **Legal**: Only stream content you have rights to access
- **Real-Debrid**: Premium account recommended for best experience
- **Network**: Requires internet connection
- **Casting**: Chromecast device must be on same WiFi network

## 🔮 Future Enhancements

- [ ] TV shows and episodes support
- [ ] Subtitles support
- [ ] Multiple addon management UI
- [ ] Search functionality
- [ ] Continue watching
- [ ] Watchlist/favorites
- [ ] External player support (VLC, MX Player)

## 📄 License

This project is for educational purposes. Respect content creators' rights.

## 🤝 Contributing

Feel free to fork and submit pull requests!

## 🐛 Troubleshooting

### Cast button not showing
- Make sure Google Play Services is updated
- Check that device and Chromecast are on same network

### Streams not loading
- Verify Real-Debrid API token is correct
- Check internet connection
- Try different content

### App crashes on startup
- Clean and rebuild project
- Check Android Studio logs
- Verify all dependencies are downloaded
