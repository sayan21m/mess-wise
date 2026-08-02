# Security Policy

## Supported Versions

The following versions of MessWise are currently being supported with security updates.

| Version | Supported          |
| ------- | ------------------ |
| 1.6.x   | :white_check_mark: |
| 1.5.x   | :white_check_mark: |
| 1.2.x   | :white_check_mark: |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Data protection (app)

- **Device prefs:** EncryptedSharedPreferences (Android Keystore).
- **UPI IDs:** AES-256-GCM before write to Firebase (`member/{uid}/upi_id`). The key is derived per mess so authenticated mess clients can decrypt for one-tap pay — this is **not** a substitute for Firebase Realtime Database security rules.
- **Emails:** Login email is handled by Firebase Auth; the profile `mail` field on the mess member node is stored as provided by the app (not AES-encrypted).
- **Financial screens:** FLAG_SECURE where enabled to reduce screenshots.
- **Release builds:** R8 obfuscation; root / emulator checks on sensitive entry points.

## Firebase Realtime Database

Client-side role checks (`PermissionUtils`, admin flags) alone are **not** sufficient. Deploy RTDB rules that:

- Scope reads/writes to members of the mess
- Restrict admin-only paths (permissions, clear wallet, member role changes)
- Limit who can write `settlements/`, `settlement_transfers/`, `cash_in/`, and `expenses/`

Without tight rules, a modified client can rewrite financial data.

## Reporting a Vulnerability

We take the security of MessWise seriously. If you believe you have found a security vulnerability, please report it responsibly.

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them to the maintainers via email at: **srtechfly83@gmail.com**

Please include the following in your report:
- Type of issue (e.g. insecure storage, auth bypass, privilege escalation, injection)
- Full paths of source file(s) related to the manifestation of the issue
- The location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit the issue

We will acknowledge receipt of your report within 48 hours and provide a timeline for resolution.
