# Basil <img width="32" alt="basil_logo" src="https://github.com/user-attachments/assets/d08b078c-a572-430e-9b5a-a40157d0da66" />
### *A high-performance Grocy client for Android devices with native barcode scanner.*

**Basil** is a purpose-built inventory management application designed specifically for dedicated Android Barcode PDAs. While the official Grocy web interface is powerful, it can be cumbersome on small, industrial screens. By leveraging **Hardware Scanner Intents**, the app doesn't need to initialize a camera or manage autofocus. It listens at the OS level, processes the API logic in the background, and provides immediate haptic feedback so you can keep your eyes on the physical stock.

---

## ✨ Key Features

### 🛒 On-Device Selectable Workflow
* **Purchase Mode:** Quickly add items to your stock. Basil intelligently checks the product's Grocy category—it only prompts for an expiration date if the category requires it or if a default shelf-life isn't set.
* **Consume Mode:** Scan to remove. Basil follows your Grocy instance's transaction logic to ensure your virtual inventory matches the physical reality.
* **Inventory Lookup:** Instantly view a detailed breakdown of every batch currently in stock, including specific expiration dates and quantities for each.

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

## 🖼️ Screenshots

### Setup & AI Configuration
| Grocy Config | Gemini Enable | Gemini Config | Settings |
|:---:|:---:|:---:|:---:|
| <img alt="Scanning the Grocy API Key" src="https://github.com/user-attachments/assets/58426d39-e764-418d-922c-a3f9a4d04914" width="200" /> | <img alt="Opt-in for Gemini features" src="https://github.com/user-attachments/assets/786399c3-a4b9-4bbc-82e0-ca11e1b08359" width="200" /> | <img alt="Scanning the Gemini API Key" src="https://github.com/user-attachments/assets/2b87f5b3-89ed-426a-9562-8d98c25af5f9" width="200" /> | <img alt="Settings & About" src="https://github.com/user-attachments/assets/2d810aa6-7570-47b8-aafd-0d2dc0ae6fdd" width="200" /> |

### Workflow Modes
| Purchase Scan | Purchase Success | Inventory Example 1| Inventory Example 2 |
|:---:|:---:|:---:|:---:|
| <img alt="Ready to scan barcodes" src="https://github.com/user-attachments/assets/ac84abea-4039-4e66-a35c-9d33cb034e0c" width="200" /> | <img alt="Confirmation of added stock" src="https://github.com/user-attachments/assets/eb144846-a442-47cb-9328-a3be55a993e1" width="200" /> | <img alt="Inventory-MacCheese" src="https://github.com/user-attachments/assets/5c87a7bb-36ac-4f3d-966f-15098fd652cb" width="200" /> | <img alt="Inventory-Bread" src="https://github.com/user-attachments/assets/eb1d56ba-42f2-449b-bc76-fac90835a249" width="200" /> |

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
*Developed with ❤️ in Massachusetts by Justin Sabourin for the Grocy Community.*
