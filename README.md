🌿 Basil: A Grocy Hardware Scanner App
Basil is a fast, lightweight, and native Android application designed specifically for hardware barcode scanners and Android PDAs. It connects seamlessly to your self-hosted Grocy instance, allowing you to manage your pantry inventory with warehouse-level efficiency.

Instead of fumbling with a mobile browser or complex app menus, Basil relies on hardware scanner intents, haptic feedback, and a streamlined Jetpack Compose UI to keep your eyes on your groceries, not your screen.

✨ Key Features
Three Operational Modes: * Purchase: Add items to stock. Prompts for an expiration date only if the Grocy category requires it.

Consume: Remove items from stock (automatically pulls from the oldest batch).

Inventory: View a detailed table of all current stock batches and their specific expiration dates.

OpenFoodFacts Auto-Resolution: Scan a barcode Grocy has never seen before? Basil automatically triggers Grocy's OpenFoodFacts plugin to download the product details, build the master data, and add it to your stock in a single, seamless motion.

Headless-Ready Haptics: Distinct vibration patterns for Success, Error, and User Input allow you to scan groceries without looking at the screen.

Instant Configuration: Setup the app in one second by scanning a Grocy-generated QR code.

🚀 Installation & Setup
1. Configure Your Scanner PDA
Basil listens for a specific Android Broadcast Intent emitted by your hardware scanner. Open your PDA's built-in scanner configuration app (often called "Scanner", "DataWedge", or "BarcodeLog") and set the intent output to:

Action: com.grocy.scanner.SCAN

String Extra: barcode_data

2. Connect to Grocy
In your Grocy Web UI, click the Settings icon (top right) -> Manage API keys -> Add.

Generate a QR code containing your URL and API key separated by a pipe character. Example format:
https://grocy.yourdomain.com|YOUR_API_KEY_HERE

Open Basil on your PDA and scan the QR code. The app will automatically save your credentials and connect.

Pro-Tip for New Products: To ensure newly discovered items are routed correctly, go to your Grocy User Settings (Profile Icon -> User settings) and configure the "Default location for new products". Basil relies on this server-side setting during the OpenFoodFacts fallback to keep network calls to an absolute minimum.

🧠 Architecture & API Logic
Basil is built using Kotlin, Jetpack Compose, and Retrofit following the MVVM architecture.

The OpenFoodFacts Fallback
The hardest part of home inventory is handling unknown barcodes. Basil handles this gracefully:

The app queries the local Grocy DB (GET stock/products/by-barcode/{barcode}).

If it returns a 400/404, Basil assumes it's a new item and hits the external lookup trigger (GET stock/barcodes/external-lookup/{barcode}?add=true).

Grocy's backend reaches out to OpenFoodFacts, builds the product, and assigns your user's default location entirely server-side.

Basil fetches the newly minted product ID and proceeds to the Expiration Date / Success screen.

API Endpoints Used
Basil is incredibly lightweight and only interacts with the following native Grocy REST endpoints:

GET stock/products/by-barcode/{barcode} - Resolves barcodes to Product IDs.

GET stock/products/{productId} - Fetches current total stock amounts.

GET stock/products/{productId}/entries - Populates the detailed Inventory table.

POST stock/products/{productId}/add - Executes Purchase workflows.

POST stock/products/{productId}/consume - Executes Consume workflows.

GET stock/barcodes/external-lookup/{barcode}?add=true - Triggers the OFF plugin.

🛠 Building from Source
Clone the repository.

Open the project in Android Studio.

Build the Release APK: Build -> Generate Signed Bundle / APK...

Sideload the generated app-release.apk onto your Android PDA.

📜 License
[Insert your chosen open-source license here, e.g., MIT, GPL-3.0]
