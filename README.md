# MessWise 🍽️

**MessWise** is a professional-grade Android app for mess and hostel management. It brings meal tracking, expense auditing, cash-in, and monthly settlement into one secure, real-time platform built for student messes.

[![Live site](https://img.shields.io/badge/website-mess--wise.web.app-00BFA5?style=flat-square)](https://mess-wise.web.app)
[![GitHub](https://img.shields.io/badge/source-GitHub-181717?style=flat-square&logo=github)](https://github.com/sayan21m/mess-wise)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/sayan21m/mess-wise)

**Live site:** [mess-wise.web.app](https://mess-wise.web.app) · **Download APK:** [MessWise.apk](https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk) · **Latest release:** v1.7

---

## ✨ Key Features

### 📊 Analytics & Reporting
- **Interactive dashboard** — monthly cash flow and expense charts via MPAndroidChart (settlement transfers are excluded from cash-in series)
- **Category breakdown** — Food, LPG, rent, and more with live progress indicators
- **Contributors** — top contributors with a full member list view
- **Monthly reports** — share this month or previous month via WhatsApp/email; previous month’s text is archived on Firebase so it stays available after settlement

### 🍱 Meal Management
- **Real-time attendance** — meal booking and status sync instantly across members
- **Smart menus** — date-seeded menu suggestions based on your goal meal rate
- **Leave system** — members apply in advance; manager summaries update automatically
- **Monthly awards** — Meal Champion and Golden Duck badges to keep the mess engaged

### 💰 Finance
- **Cash-in & expenses** — role-based permissions for treasurers and admins; this-month and all-time expense totals
- **Full history by month** — View All opens a month strip with arrow buttons; only that month’s cash-in or expense rows are shown
- **Pending due (Home)** — live balance of dues up to today across due history
- **Due reminders** — background alerts plus an app-open prompt until last month’s settlement is cleared
- **Month-end settlement** — previous-month dues matched debtor → creditor (who you pay / who pays you)
- **Pay via UPI** — member UPI IDs AES-encrypted on Firebase (`member/{uid}/upi_id`); one-tap pay when the receiver has a UPI set
- **Mark as Paid Offline** — clear matched dues for cash/other payments; written to settlement history
- **One transfer, one record** — payer and receiver cannot both apply the same matched payment (deterministic lock under `settlement_transfers/`)
- **Settlement history** — `{messId}/settlements/{id}` with payer, receiver, amount, method (upi/offline), status, timestamp
- **Admin tools** — edit any member’s UPI; clear previous-month carry; clear mess wallet & expenses (with dues reset)

### 🛡️ Security
- **Encrypted storage** — EncryptedSharedPreferences backed by the Android Keystore
- **UPI at rest** — AES-256-GCM before Firebase write (mess-scoped key; still apply RTDB rules)
- **Anti-screenshot** — blocks captures on sensitive financial and admin screens
- **Root detection** — integrity checks on compromised devices
- **R8 obfuscation** — hardened release builds

### 📦 Distribution & Updates
- **Direct APK distribution** via Firebase Hosting and GitHub
- **In-app APK updates** — downloads the APK from Firebase `apk_url` / GitHub, then prompts install (no Play Store)
- **Forced updates** — `AppUpdateManager` checks Firebase `min_version_code` against the installed app

Firebase `version_control` example:

```json
{
  "min_version_code": 6,
  "latest_version_code": 7,
  "apk_url": "https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk",
  "update_url": "https://mess-wise.web.app",
  "update_message": "Bug fixes and improvements"
}
```

Use a **direct `.apk` link** in `apk_url` (recommended). Website links alone cannot be installed in-app.

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
| Security | Android Security Crypto, AES-GCM (UPI), R8 |

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
   - Create **Realtime Database** and apply your security rules (client permission checks are not enough alone)
   - Optional — for forced updates, set in Realtime Database:
     ```json
     {
       "min_version_code": 6,
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
├── fragment_ui/        # Home, cash-in, expenses, summary, …
├── admin_ui/           # Member / meal admin
├── ui/                 # Settings, auth, meal bank, …
├── data_models/        # Firebase models
├── workers/            # Due reminder WorkManager jobs
└── utils/              # FinanceUtils, SettlementUtils, UpiCrypto,
                        # MonthlyReportUtils, HistoryMonthNavigator,
                        # PermissionUtils, AppUpdateManager, …
```

### How dues & settlement work (short)

| Concept | Behavior |
| --- | --- |
| Current month due | `(meal rate × meals) − monthly cash-in` for the calendar month |
| Home pending due | Sum of `due_history` **up to today** (all months including current) |
| Settlement | Previous (ended) month only — matched who-pays-whom; not the in-progress month |
| UPI | AES-GCM encrypted on Firebase under `member/{uid}/upi_id`; editable by self or admin |
| Offline settle | Mark paid / confirm received → updates balances + `settlements/` history |
| Transfer lock | `settlement_transfers/{month_from_to}` — each matched pair recorded once |
| Monthly report | Live build for current month; previous month archived under `monthly_reports/{yyyy-MM}` |
| Old expenses | Archived into `finance/settled_expenses` with per-id claim under `finance/settled_expense_ids/` |

### Important Firebase paths (per mess)

| Path | Purpose |
| --- | --- |
| `member/{uid}/…` | Profile, meals, balances, dues, encrypted `upi_id` |
| `cash_in/`, `expenses/` | Ledgers (settlement cash_in rows use `status: settlement`) |
| `settlements/`, `settlement_transfers/` | Settlement history + anti-double-record locks |
| `monthly_reports/{yyyy-MM}` | Archived shareable summary text |
| `finance/settled_expenses` | Running total of archived old expenses |
| `finance/settled_expense_ids/{id}` | Idempotent expense archive claims |

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
