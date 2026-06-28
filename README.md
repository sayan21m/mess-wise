# MessWise 🍽️

**MessWise** is a professional-grade Android application designed for high-performance mess and hostel management. It simplifies meal tracking, financial auditing, and community administration into one seamless, secure, and modern platform.

---

## ✨ Key Features

### 📊 Advanced Analytics & Reporting
*   **Interactive Dashboard**: Visualize monthly cash flow and expenses with **MPAndroidChart** integration.
*   **Category Breakdown**: See exactly where money is spent (Food, LPG, Rent, etc.) with real-time progress indicators.
*   **Professional Reports**: Generate precision financial reports including "Debt vs. Surplus" lists, shareable via WhatsApp or Email.

### 🍱 Smart Meal Management
*   **Automated Tracking**: Easy attendance logging for members and administrators.
*   **Dynamic Menus**: A date-seeded smart menu system that suggests meals based on your **Goal Meal Rate**.
*   **Leave System**: Members can apply for leaves in advance, automatically updating manager summaries.

### 🛡️ Enterprise-Grade Security
*   **Encrypted Storage**: Sensitive user data is protected using **EncryptedSharedPreferences** and the Android Keystore.
*   **Anti-Screenshot System**: Protects financial and administrative privacy by blocking screen captures on sensitive views.
*   **Root Detection**: Built-in integrity checks prevent the app from running on compromised or rooted devices.
*   **R8 Obfuscation**: Release builds are hardened against reverse engineering.

### 💰 Monetization & Distribution
*   **AdMob Integration**: Strategically placed App Open and Interstitial ads with a local frequency cap (max 5 ads/day).
*   **Direct Distribution**: Hosted via **Firebase Hosting** with a modern landing page for direct APK downloads.

---

## 🛠️ Technical Stack

*   **Language**: Java (Modern Android SDK)
*   **UI Framework**: Android XML (Material Design 3)
*   **Backend**: Firebase Realtime Database
*   **Authentication**: Firebase Auth
*   **Analytics**: Firebase Crashlytics
*   **Charts**: MPAndroidChart
*   **Animations**: Lottie
*   **Storage**: Android Security Crypto

---

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug (or newer)
*   JDK 17
*   A Firebase Project

### Installation
1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/sayan21m/mess-wise.git
    ```
2.  **Firebase Setup**:
    *   Add your `google-services.json` to the `app/` directory.
    *   Enable **Email/Password** authentication in the Firebase Console.
    *   Set up **Realtime Database** and apply the rules found in the security section.
3.  **Build the Project**:
    *   Open in Android Studio.
    *   Sync Gradle and run the `:app` module.

---

## 🌐 Web Hosting & Verification
The landing page and AdMob verification are managed through the `docs/` folder.

*   **Landing Page**: [https://mess-wise.web.app](https://mess-wise.web.app)
*   **AdMob Verification**: The `app-ads.txt` is served at the root for automated crawler validation.

To deploy website updates:
```bash
firebase deploy --only hosting
```

---

## 📝 License
Copyright (c) 2026 **SR Tech**. All rights reserved.
Unauthorized copying, distribution, or modification of this project is strictly prohibited. See the [LICENSE](LICENSE) file for full details.

---
Built with ❤️ for students by **SR Tech**.
