# 🛡️ Android Secure Auth System

> A high-security, full-stack authentication system for Android, enforcing **Google OAuth** and **Time-based One-Time Password (TOTP)** verification on every single login session.

![Kotlin](https://img.shields.io/badge/Kotlin-Mobile-7F52FF?style=for-the-badge&logo=kotlin)
![FastAPI](https://img.shields.io/badge/FastAPI-Backend-009688?style=for-the-badge&logo=fastapi)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Infrastructure-2496ED?style=for-the-badge&logo=docker)

## 🌟 Features

*   **🔒 Strict Authentication:** Users must authenticate via **Google Sign-In** AND **TOTP Code** (Google Authenticator) on every login. No "remember me" for maximum security.
*   **🔑 Explicit OAuth Flow:** Forces a Google Account picker (`signOut` before `signIn`) to prevent accidental unauthorized access via cached sessions.
*   **🌍 Remote Access:** Integrated **Cloudflare Tunnel** support to expose the local Docker backend to the internet for real-device testing without USB cables.
*   **🐳 Dockerized Stack:** Entire backend (API + DB + Tunnel) runs with a single `docker-compose up` command.
*   **📱 Modern Android:** Built with **Jetpack Compose**, Retrofit, and Material Design 3.

## 🏗️ Architecture

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Mobile App** | Kotlin, Jetpack Compose | Native Android UI, Google Identity Services, Secure Storage. |
| **Backend API** | Python, FastAPI | Validates ID Tokens, generates/verifies TOTP secrets. |
| **Database** | PostgreSQL | Stores user records and encrypted TOTP secrets. |
| **Infrastructure** | Docker Compose | Orchestrates API, DB, and Cloudflare Tunnel. |

## 🚀 Getting Started

### Prerequisites
*   Docker & Docker Compose
*   Android Studio (for building the APK)
*   Google Cloud Console Project (with Web & Android Client IDs)

### 1. Backend Setup
The backend runs in Docker containers.

```bash
cd server
docker-compose up -d --build
```
*   **API:** `http://localhost:11000`
*   **Tunnel:** Check logs (`docker-compose logs tunnel`) for the public HTTPS URL.

### 2. Android Setup
1.  Open `android/` in Android Studio.
2.  Update `MainActivity.kt` with your **Google Web Client ID**.
3.  Update the `BASE_URL` in `MainActivity.kt` with your Cloudflare Tunnel URL.
4.  Build and Run:

```bash
cd android
./gradlew.bat assembleDebug
```

## 🛡️ Security Flow
1.  **Step 1:** User clicks "Login with Google".
    *   *App forces account selection.*
2.  **Step 2:** App sends Google ID Token to Backend.
    *   *Backend verifies token and email whitelist (`keremrgur@gmail.com`).*
3.  **Step 3:** Backend checks `totp_secret`.
    *   *If New User:* Returns `has_secret=False`. App shows QR/Seed setup.
    *   *If Registered:* Returns `is_verified=False`. App forces TOTP entry.
4.  **Step 4:** User enters 6-digit code.
    *   *Backend verifies against secret.*
5.  **Success:** Access granted for this session.

## 📂 Project Structure

```
├── android/           # Native Android Project
│   ├── app/           # Source code (Kotlin)
│   └── gradle/        # Build wrappers
├── server/            # Backend Infrastructure
│   ├── main.py        # FastAPI Application
│   ├── docker-compose.yml
│   └── Dockerfile
└── plan.md            # Implementation Timeline
```

## 👤 Author
**KereMath** - *Secure Android Architectures*
