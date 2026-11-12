# Basil <img width="32" alt="basil_logo" src="https://github.com/user-attachments/assets/d08b078c-a572-430e-9b5a-a40157d0da66" />
### *A high-performance Grocy client for Android: scan with a dedicated scanner device or your phone's camera.*

**Basil** is an inventory management front-end for Grocy, built for scanning. While the official Grocy web interface is powerful, it can be cumbersome on a small screen with a box of groceries in your other hand. Basil was designed for dedicated Android devices with barcode reader hardware: by leveraging hardware scanner intents, it listens at the OS level, calls the Grocy API in realtime, and provides haptic feedback so you can keep your eyes on the physical stock.

**New in 2.0:** no scanner device needed. Any Android phone can scan with its camera, and every part of Basil works the same either way.

---

## ✨ Key Features

### 📷 Two Ways to Scan
* **Hardware Scanner:** On PDAs and other scanner devices, Basil receives scans straight from the built-in reader.
* **Phone Camera:** On any Android phone, tap the "Tap to Scan" box and it becomes a live camera right where it is. Elsewhere, a camera button opens a small pop-up camera. Reads UPC/EAN, Code 128/39/93, ITF, QR and Data Matrix (including Grocycodes).
* **Volume Buttons:** With the camera, either volume button turns the camera on and off, like a trigger. (This can be switched off in Settings.)
* Basil asks which one you'll use when you first open it.

### 🛒 On-Device Selectable Workflow
* **Purchase Mode:** Quickly add items to your stock. Basil intelligently checks the product's Grocy product and only prompts for an expiration date if the item requires it or if a default shelf-life isn't set. A one-tap label print option is displayed after adding to stock.
* **Consume Mode:** Simply scan to remove from stock. Basil follows your Grocy instance's transaction logic to ensure virtual inventory matches physical reality.
* **Inventory Lookup:** Instantly view a details of items in stock including specific expiration dates and quantities for each.
* **Shopping Mode:** Open your Grocy shopping list and scan items as they go in the cart. Each scan purchases the item and checks it off, with Undo. The cart button puts the next scan on the list instead, and Clear completed tidies up when you're done.
* **Transfer Mode:** Pick a location in Settings, then scan items to move them there. With the camera, it stays on between items.

### 🔍 Intelligent Product Resolution
* **Grocy-First:** Basil first checks your Grocy database for the barcode.
* **OpenFoodFacts-Fallback:** If unknown, it triggers a lookup with **OpenFoodFacts** via Grocy's API.
* **User Defaults:** Newly discovered items are automatically assigned to the Default Location (configured in Grocy User Settings of the user the API key is associated with), preventing the "Missing Location" errors common in other apps.
* **Gemini AI Enrichment:** If enabled, Gemini AI can determine estimated item cost, due dates, and storage location based upon the product name identified. Basil uses Google's newest Flash model by default; you can pick a specific model in Settings.

### 📱 Industrial Design
* **Deep Purple UI:** High-contrast, dark-themed interface designed to elimninate need for light/dark selection, reduce visual strain, and look sleek on industrial displays.
* **Haptic Language:**
    * *Short Pulse:* Success / Scan Accepted.
    * *Double Pulse:* Error / Item Not Found.
    * *Triple Pulse:* Input Required (e.g., Expiration Date needed).
* **Sounds & Vibration:** Both on by default, and each can be switched off in Settings.

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

Basil runs on Android 13 or newer.

### 1. Choose How You'll Scan
On first launch, Basil asks **"How will you scan?"**: *Phone camera* or *Built-in scanner*. On a scanner device, you can also just scan something; Basil picks *Built-in scanner* for you. To change your choice later, use **Logout / Disconnect** in Settings, which resets Basil and asks again.

### 2. The "Magic" QR Code
Basil uses a zero-config setup. Generate an API key in Grocy and show the QR code to scan in Basil. With the camera, tap **Scan QR Code**. If it won't scan, the back arrow returns to the scanner choice.
*You could also generate your own if desired using format: Grocy URL and API Key separated by a pipe* (`|`): `https://grocy.yourdomain.com/api|your_secret_api_key_here`

💡 In environments where you may share the scanner device with multiple people, it may be desireable to create a generic "Basil" user (or the name of your chosing) in Grocy for the device and create the API key attached to that user account. Thus the journals will show "Basil" vs. an indiviuals name who may not have been using the shared device.

### 3. Scanner Intent Configuration (scanner devices only)
Skip this if you scan with the phone camera. For the hardware buttons to work, configure your PDA's "Scanner" or "DataWedge" to output the following:
* **Intent Action:** `com.basil.grocyscanner.SCAN`
* **Intent Delivery:** `Broadcast Intent`
* **String Extra:** `barcode_data` *Output the complete UPC including check digit. NO spaces or other formatting prefix/suffix.*

💡 You may want to disable other types of barcode formats to eliminate 'false scans'. Many products have Datamatrix/QR Code on the packaging for consumer and non-consumer purpoposes. However, if you are using Grocycode or custom printed Datamatrix/QR Code labels, make sure those formats are not disabled. Basil _can_ process them!

---

## 💻 Technical Architecture

Basil is built using modern Android standards to ensure long-term maintainability:

* **Language:** Kotlin 2.x
* **UI Framework:** Jetpack Compose (Declarative UI)
* **Networking:** Retrofit 2 + OkHttp 4
* **JSON Parsing:** Gson
* **Async Logic:** Kotlin Coroutines & Flow
* **Camera Scanning:** CameraX + ML Kit barcode scanning (bundled, so it works without Google Play Services)
* **Architecture:** MVVM (Model-View-ViewModel)

### API Implementation
Basil communicates with the <a href="https://demo.grocy.info/api">Grocy REST API</a>. Key endpoints include:
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
*Developed with ❤️ in Massachusetts by Justin Sabourin for the <a href="https://grocy.info/">Grocy Community</a>.*
