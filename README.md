# MessWise 🍽️

**MessWise** is a professional-grade Android app for mess and hostel management. It brings meal tracking, expense auditing, cash-in, and monthly settlement into one secure, real-time platform built for student messes.

[![Live site](https://img.shields.io/badge/website-mess--wise.web.app-00BFA5?style=flat-square)](https://mess-wise.web.app)
[![GitHub](https://img.shields.io/badge/source-GitHub-181717?style=flat-square&logo=github)](https://github.com/sayan21m/mess-wise)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/sayan21m/mess-wise)

**Live site:** [mess-wise.web.app](https://mess-wise.web.app) · **Download APK:** [MessWise.apk](https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk) · **Latest release:** v1.1

---

## ✨ Key Features

### 📊 Analytics & Reporting
- **Interactive dashboard** — monthly cash flow and expense charts via MPAndroidChart
- **Category breakdown** — Food, LPG, rent, and more with live progress indicators
- **Monthly reports** — debt vs. surplus lists, exportable and shareable via WhatsApp or email

### 🍱 Meal Management
- **Real-time attendance** — meal booking and status sync instantly across members
- **Smart menus** — date-seeded menu suggestions based on your goal meal rate
- **Leave system** — members apply in advance; manager summaries update automatically
- **Monthly awards** — Meal Champion and Golden Duck badges to keep the mess engaged

### 💰 Finance
- **Cash-in & expenses** — role-based permissions for treasurers and admins
- **Due reminders** — automatic alerts so pending dues don't pile up
- **Settlement** — end-of-month calculations with exportable summaries

### 🛡️ Security
- **Encrypted storage** — EncryptedSharedPreferences backed by the Android Keystore
- **Anti-screenshot** — blocks captures on sensitive financial and admin screens
- **Root detection** — integrity checks on compromised devices
- **R8 obfuscation** — hardened release builds

### 📦 Distribution & Updates
- **Direct APK distribution** via Firebase Hosting and GitHub
- **Forced updates** — `AppUpdateManager` checks Firebase `min_version_code` against the installed app

---

## 🛠️ Technical Stack

| Layer | Technology |
| --- | --- |
| Language | Java |
| UI | Android XML, Material Design 3, Jetpack Compose (partial) |
| Backend | Firebase Realtime Database |
| Auth | Firebase Auth (Email/Password) |
| Analytics | Firebase Crashlytics |
| Charts | MPAndroidChart |
| Animations | Lottie |
| Security | Android Security Crypto, R8 |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- A Firebase project with Realtime Database and Auth enabled

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/sayan21m/mess-wise.git
   cd mess-wise
   ```

2. **Firebase setup**
   - Add `google-services.json` to the `app/` directory
   - Enable **Email/Password** authentication
   - Create **Realtime Database** and apply your security rules
   - Optional — for forced updates, set in Realtime Database:
     ```json
     {
       "min_version_code": 2,
       "update_url": "https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk"
     }
     ```

3. **Build and run**
   - Open the project in Android Studio
   - Sync Gradle and run the `:app` module

   Release APK:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 🌐 Website

The marketing site lives in `docs/` and is deployed with Firebase Hosting.

| Page | URL |
| --- | --- |
| Home | [mess-wise.web.app](https://mess-wise.web.app) |
| Privacy | [mess-wise.web.app/privacy.html](https://mess-wise.web.app/privacy.html) |
| Terms | [mess-wise.web.app/terms.html](https://mess-wise.web.app/terms.html) |
| Contact | [mess-wise.web.app/contact.html](https://mess-wise.web.app/contact.html) |

**Deploy hosting updates:**
```bash
firebase deploy --only hosting
```

**Site structure:**
```
docs/
├── index.html          # Landing page
├── privacy.html
├── terms.html
├── contact.html
├── css/site.css
├── js/site.js
└── asset/screenshots/  # App screenshots
```

---

## 📁 Project Structure

```
app/src/main/java/com/srtech/messwise/
├── fragment_ui/        # Feature screens (home, cash-in, expenses, admin, …)
├── ui/                 # Activities (settings, auth, …)
└── utils/              # AppUpdateManager, MenuPlanner, PermissionUtils, …
```

---

## 📬 Contact & Security

- **General inquiries:** [srtechfly85@gmail.com](mailto:srtechfly85@gmail.com) or the [contact form](https://mess-wise.web.app/contact.html)
- **Security vulnerabilities:** see [SECURITY.md](SECURITY.md) — please do not open public issues for security reports

---

## 📝 License

Copyright (c) 2026 **SR Tech**. All rights reserved.  
Unauthorized copying, distribution, or modification of this project is strictly prohibited. See [LICENSE](LICENSE) for full details.

---

Built with ❤️ for students by **SR Tech**.
