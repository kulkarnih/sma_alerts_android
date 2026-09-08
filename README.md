# SMA Alerts 📈 (Android App)

An Android app that generates trading signals based on Simple Moving Average (SMA) analysis for major market indices. It runs a daily background analysis and sends a notification when the signal changes.

## 🚀 Features

### **Trading Strategy**
- **Configurable SMA Period**: Set any period from 1 to 200 days (default: 200 days)
- **Multi-Index Watchlist**: Track S&P 500 (`^GSPC`), NASDAQ 100 (`^NDX`), and MSCI World (`URTH`)
- **Smart Signal Generation**:
  - 🟢 **BUY**: When index is X% above SMA
  - 🔴 **SELL**: When index is Y% below SMA
  - 🟡 **HOLD**: When between thresholds
  - 🔴 **SELL 80%**: When 30% above SMA
  - 🔴 **SELL ALL**: When 40% above SMA

### **Android Experience**
- **Daily Background Analysis**: Scheduled with WorkManager once per day (weekdays)
- **Notifications**: Single master toggle; alerts only when the signal changes
- **Notification Time**: Configurable in your local timezone (default = 30 minutes before NYSE close, with DST handling)
- **Auto-fetch on Launch**: Data fetch and signal generation run on app open.
- **Persistent Settings**: Thresholds, SMA period, tracked indices, notifications.

### **Data & Security**
- **Yahoo Finance API**: Daily market data via the public JSON chart endpoint (no API key required)
- **Local SMA Calculation**: Closing prices fetched, SMA computed on-device for reliability
- **Resilience**: Retry/backoff for network; robust error handling in background worker

## 🛠️ Android Setup

### **Prerequisites**
- Android Studio (latest)
- Android SDK + build tools
- Java 17 (the project is pre-configured to use JDK 17 via Gradle settings)

### **Install and Run**
1) Clone the repository
```bash
git clone https://github.com/yourusername/sma_alerts.git
cd sma_alerts
```

2) Open the Android project in Android Studio
- File → Open → select `android/` folder
- Let Gradle sync and install any missing SDK components

3) Build and run
- Select a device/emulator
- Run ▶ (or Build → Rebuild Project if needed)

4) Generate APK
- Build → Build Bundle(s) / APK(s) → Build APK(s)
- The APK will be in `android/app/build/outputs/apk/`

## 📊 Using the App

### **Basic Workflow**
1. Open the app (first launch may ask for notification permissions on Android 13+)
2. Set thresholds (Buy X%, Sell Y%) and SMA period
3. Choose which indices to track (S&P 500, NASDAQ 100, MSCI World)
4. Enable notifications and set your preferred local notification time
5. Tap "Generate Signal" to fetch now; background analysis runs daily and notifies on signal change

### **Configuration Options**
- **Buy Signal (X%)**: Percent above SMA to trigger BUY (default: 4%)
- **Sell Signal (Y%)**: Percent below SMA to trigger SELL (default: 3%)
- **SMA Period**: Moving average days (default: 200)
- **Index Selection**: S&P 500 (`^GSPC`), NASDAQ 100 (`^NDX`), MSCI World (`URTH`)
- **Notifications**: Master toggle + time in your timezone (default = 30 min before NYSE close)

## 🔔 Notifications & Scheduling

- Uses WorkManager for reliable daily execution
- Reschedules after reboot (BootReceiver)
- Only notifies when the signal changes from the last stored value
- Notification time is set in the user's local timezone; internally converted relative to NYSE (handles DST)

## 🔧 Technical Details

### **Architecture**
- **Android**: Java + WorkManager + Notification channels
- **WebView UI**: HTML/CSS/JS bundled via `android/app/src/main/assets/public/index.html`
- **Data Source**: Yahoo Finance JSON chart API (`query1.finance.yahoo.com/v8/finance/chart/`)
- **Persistence**: SharedPreferences (native) + localStorage (web layer for initial capture)

### **SMA Calculation**
- **Method**: Simple Moving Average calculated locally
- **Data**: Uses closing prices from historical daily data
- **Period**: Configurable from 1 to 200 days
- **Accuracy**: More reliable than API-provided SMA

### **API Integration**
- **Provider**: Yahoo Finance (public JSON chart endpoint, no key required)
- **Endpoint**: `/v8/finance/chart/{symbol}?interval=1d&range={1y|5y}` (`^` URL-encoded as `%5E`)
- **Symbols**: `^GSPC` (S&P 500), `^NDX` (NASDAQ 100), `URTH` (MSCI World)

## 🧭 Permissions

Declared in `AndroidManifest.xml`:
- INTERNET, ACCESS_NETWORK_STATE
- WAKE_LOCK, RECEIVE_BOOT_COMPLETED
- POST_NOTIFICATIONS (Android 13+)

## 🔒 Security

### **Privacy**
- **No API Key Required**: Market data comes from Yahoo Finance's public endpoint
- **Native Storage**: Settings persisted in SharedPreferences (not exported)
- **No Personal Data**: Requests contain only the index symbol being fetched

## 🎯 Trading Strategy

### **Signal Logic**
```javascript
if (percentage >= 40) return 'SELL ALL';
if (percentage >= 30) return 'SELL 80%';
if (percentage >= buyThreshold) return 'BUY';
if (percentage <= -sellThreshold) return 'SELL';
return 'HOLD';
```

## 🤝 Contributing

### Branch Protection Policy

**All changes to `main` must go through Pull Requests.** Direct commits to `main` are not allowed.

### Development Workflow

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   # or for bug fixes
   git checkout -b fix/your-bug-fix
   ```

2. **Make your changes and commit:**
   ```bash
   git add .
   git commit -m "Description of your changes"
   ```

3. **Push to GitHub:**
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Create a Pull Request:**
   - Go to your repository on GitHub
   - Click **Pull requests** → **New pull request**
   - Select your branch and fill in the description
   - Wait for CI checks to pass
   - Merge the PR (no approval needed)

### Setting Up Branch Protection

To enforce PR-only merges, set up branch protection:

**Quick Setup (with GitHub CLI):**
```bash
./.github/setup-branch-protection.sh
```

**Manual Setup:**
See [`.github/BRANCH_PROTECTION.md`](.github/BRANCH_PROTECTION.md) for detailed instructions.

### Pull Request Requirements

- ✅ All CI checks must pass
- ✅ Branch must be up to date with main
- ⚠️ **PR reviews not required** (solo contributor setup)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

This application is for educational and informational purposes only. It is not intended as financial advice. Always do your own research and consult with a qualified financial advisor before making investment decisions. Past performance does not guarantee future results.

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/sma_alerts/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/sma_alerts/discussions)

## 🙏 Acknowledgments

- [Yahoo Finance](https://finance.yahoo.com/) for providing market data

---

## 🖼️ App Icon

This project includes an SVG icon (`icon.svg`). For Android launcher icons, use the generated assets from icon.kitchen or Android Studio Image Asset tool:
- Place `ic_launcher.png` variants in `android/app/src/main/res/mipmap-*`
- Adaptive icons XML in `res/mipmap-anydpi-v26/`
- If needed, see `ANDROID_ICON_SETUP.md` for step-by-step guidance

---

**Happy Trading! 📈🚀**
