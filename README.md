# 🌿 Basil
### *A high-performance, native Grocy client for Android PDAs.*

**Basil** is a purpose-built inventory management application designed specifically for dedicated Android Barcode PDAs (like those from Zebra, Chainway, or Munich). While the official Grocy web interface is powerful, it can be cumbersome on small, industrial screens. Basil solves this by providing a "Headless-Ready," high-contrast, and lightning-fast scanning experience.

---

## 📖 Overview
Basil turns your PDA into a professional-grade warehouse tool. It is designed for speed, reliability, and one-handed operation in your pantry or kitchen. 

By leveraging **Hardware Scanner Intents**, the app doesn't need to initialize a camera or manage autofocus. It listens at the OS level, processes the API logic in the background, and provides immediate haptic feedback so you can keep your eyes on the physical stock.

## ✨ Key Features

### 🛒 Multi-Mode Workflow
* **Purchase Mode:** Quickly add items to your stock. Basil intelligently checks the product's Grocy category—it only prompts for an expiration date if the category requires it or if a default shelf-life isn't set.
* **Consume Mode:** Scan to remove. Basil follows your Grocy instance's transaction logic (typically FIFO) to ensure your virtual inventory matches the physical reality.
-   **Inventory Lookup:** Instantly view a detailed breakdown of every batch currently in stock, including specific expiration dates and quantities for each.

### 🔍 Intelligent Product Resolution
* **Local-First:** Basil first checks your Grocy database for the barcode.
* **Global Fallback:** If unknown, it triggers a background sync with **OpenFoodFacts** via Grocy's external lookup API.
* **Smart Defaults:** Newly discovered items are automatically assigned to your user's **Default Location** (configured in Grocy User Settings), preventing the "Missing Location" errors common in other apps.

### 📱 Industrial Design
* **Deep Purple UI:** High-contrast, dark-themed interface designed to reduce eye strain and look sleek on industrial displays.
* **Haptic Language:** * *Short Pulse:* Success / Scan Accepted.
    * *Double Pulse:* Error / Item Not Found.
    * *Triple Pulse:* Input Required (e.g., Expiration Date needed).

---

## 🛠 Setup & Configuration

### 1. The "Magic" QR Code
Basil uses a zero-config setup. Generate a QR code (using any generator) with your Grocy URL and API Key separated by a pipe (`|`):
`https://grocy.yourdomain.com/api|your_secret_api_key_here`

Scan this inside the app to instantly link your device.

### 2. Scanner Intent Configuration
For the hardware buttons to work, configure your PDA's "Scanner" or "DataWedge" app:
* **Intent Action:** `com.basil.grocyscanner.SCAN`
* **Intent Delivery:** `Broadcast Intent`
* **String Extra:** `barcode_data`

---

## 💻 Technical Architecture

Basil is built using modern Android standards to ensure long-term maintainability:

* **Language:** Kotlin 2.x
* **UI Framework:** Jetpack Compose (Declarative UI)
* **Networking:** Retrofit 2 + OkHttp 4
* **JSON Parsing:** Gson
* **Async Logic:** Kotlin Coroutines & Flow
* **Architecture:** MVVM (Model-View-ViewModel)

### API Implementation
Basil communicates with the Grocy REST API. Key endpoints include:
* `/stock/products/by-barcode/{barcode}`: Primary resolution.
* `/stock/barcodes/external-lookup/{barcode}?add=true`: The "magic" auto-add trigger.
* `/stock/products/{productId}/entries`: Powers the Inventory table.

---

## 🤝 Contributing & Open Source
Basil is open-source because the self-hosting community thrives on shared tools. 

1.  **Fork** the repository.
2.  **Create** a feature branch (`git checkout -b feature/AmazingFeature`).
3.  **Commit** your changes (`git commit -m 'Add some AmazingFeature'`).
4.  **Push** to the branch (`git push origin feature/AmazingFeature`).
5.  **Open** a Pull Request.

## 📜 License
Distributed under the **MIT License**. See `LICENSE` for more information.

---
*Developed with ❤️ by Justin P. Sabourin for the Grocy Community.*
