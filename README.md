# Batwa — lightweight Android expense tracker

Native Android Java expense tracker. Offline-first with optional Google account sign-in and private Firestore sync. No analytics or ads. Uses Google Material Components for Android UI.

## Included
- First-launch configurable opening balance; tap the balance card on Home to edit the opening amount later. Editing adjusts the current balance by the difference without changing later expenses or daily credits.
- One-time v1.1 data repair: corrects the old setup bug that double-counted the opening balance.
- Home screen: centered running balance, newest-first transactions, and a top-right Add popup.
- Expense popup: amount, description, and editable tag. New tags are created automatically; matching tags are reused (case-insensitive).
- Daily income screen: amount and daily credit time. Daily credits appear as transactions.
- Spending screen: totals expenses by tag for a selectable inclusive date range; defaults to the current calendar month and sorts highest spend first.
- Tag management: add, rename/update, choose from a searchable offline catalogue of 700+ emoji icons, and delete. Renaming updates historical transactions. Deleting a tag keeps its transactions but makes them "Untagged".
- PKR and `dd-MM-yy hh:mm a` timestamps.
- Material 3 green theme, system-bar safe spacing for Samsung/Pixel layouts, animated Material bottom navigation with Home, Daily, Spending and Tags icons.
- Daily screen content scrolls independently while bottom navigation stays anchored at the bottom.
- Local app-private SharedPreferences storage.

## Upgrade/data preservation
This update deliberately keeps:
- `applicationId`: `com.example.dailybalance`
- SharedPreferences file: `daily_balance_store`
- Existing balance and transaction preference keys and JSON fields

The new `tag` transaction field is optional, so older transactions remain readable. `versionCode` is now 4 (`versionName` 1.4). For future updates, keep the same applicationId, increment versionCode, and sign with the same signing key. On this laptop, Android's debug signing key is normally `%USERPROFILE%\.android\debug.keystore`; do not delete it if you use debug APKs for upgrades.

**Do not uninstall Batwa or clear its storage when updating.** Install the newer APK over the existing app. If Android reports a signature mismatch, the APK was signed with a different key; do not uninstall if preserving data matters. Back up first.

## Lightweight Windows build (no Android Studio, no emulator)

This project uses Android Gradle Plugin 8.7.3, which requires Gradle 8.9 and JDK 17 or newer. The Material Components dependency is fetched by Gradle on the first build.

1. Install a JDK (Temurin 17 is a good choice): https://adoptium.net/temurin/releases/?version=17
2. Download the **Gradle 8.9 binary-only ZIP**: https://services.gradle.org/distributions/gradle-8.9-bin.zip . Extract it, e.g. to `C:\Gradle\gradle-8.9`.
3. Download **Android SDK Command-line Tools for Windows** from https://developer.android.com/studio#command-line-tools-only . Extract the contents so the SDK manager is at `C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat` (create the `cmdline-tools\latest` folders if needed).
4. Set Windows environment variables:
   - `JAVA_HOME` = your JDK 17 folder
   - `ANDROID_HOME` = `C:\Android\Sdk`
   - Add `%JAVA_HOME%\bin`, `C:\Gradle\gradle-8.9\bin`, and `%ANDROID_HOME%\cmdline-tools\latest\bin` to `Path`.
5. Open a **new** Command Prompt and run:

   ```bat
   sdkmanager.bat --licenses
   sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```

   Accept the licenses. No emulator/system image is needed.
6. Extract this ZIP. Open Command Prompt in the `Batwa` project folder (the folder containing `build.gradle`) and run:

   ```bat
   build.bat
   ```

   Or run `gradle --no-daemon assembleDebug`.
7. Your APK will be at `app\build\outputs\apk\debug\app-debug.apk`. Copy it to your Android phone and install it. Enable "Install unknown apps" for the file manager/browser if Android asks.

Gradle downloads the Android Gradle Plugin and dependencies on the first build, so the first build needs internet. Later builds are faster. Do not install an emulator; it consumes significant disk space and RAM.

## Daily scheduling note
Android may delay alarms if exact-alarm access is unavailable or battery restrictions intervene. If the phone is off at the scheduled time, the app catches up when opened. For the most reliable scheduled time, allow Batwa's exact-alarm access in Android settings when prompted.

## Future code updates
Keep this folder as the master project. For each update, change the source, increment `versionCode` in `app/build.gradle`, rebuild with `build.bat`, then install the APK over the existing installation. Keep the package name and signing key unchanged to preserve app data and allow in-place updates.


## v1.4 icon catalogue update

Tag icon selection now uses an offline searchable catalogue containing 700+ emoji icons across food, home, finance, transport, health, work, education, entertainment, clothing, family, pets, sports, outdoors, events and general symbols. Search matches icon keywords (for example: `fuel`, `rent`, `coffee`, `medicine`, `school`, `travel`) and also supports searching/pasting the emoji itself. No network access or additional dependency is required.


## v1.5 icon picker performance update

The icon picker now uses AndroidX RecyclerView with a 5-column grid. Only visible icon cells are created and recycled, rather than inflating hundreds of Material buttons at once. Search continues to work offline and updates the visible grid without rebuilding the entire dialog. Added searchable entries/aliases for cigarettes/smoking (`🚬`), internet/Wi-Fi/mobile data (`🌐`, `📶`, `📡`, `🛜`), and cup of tea/chai (`🍵`, `☕`). Version code is 5 and version name is 1.5. The application ID and existing SharedPreferences storage remain unchanged.


## Batwa v1.6 changes
- Restored tap-to-edit on Home transaction rows.
- Edit amount (signed positive/negative), description, tag, date and time.
- Delete transactions with balance recalculation.
- Starting balance row opens the dedicated starting-balance editor.
- Package ID and SharedPreferences storage remain unchanged for in-place upgrades.


## v1.7 update
- The Add transaction dialog now includes a Debit checkbox. It is checked by default, so ordinary expenses continue to subtract from the balance. Uncheck it to add a positive transaction (credit) to the balance.
- Positive and negative transactions both retain the entered description and optional tag and appear in the transaction history.
- Version name 1.7 / version code 7. Package ID and local storage remain unchanged for in-place updates.


## Current development branch: Google sync and usability

The `codex/firestore-sync` branch contains the next development batch. It keeps the existing application ID, signing expectations, SharedPreferences file and legacy JSON fields so it can be installed as an in-place update after validation.

- Google account sign-in through Firebase Authentication.
- Per-user Firestore storage under `users/{uid}/batwa`; one account cannot read another account's data.
- Additive first-sync merge: local and cloud records are combined by stable IDs. Existing local records are not replaced by an empty cloud collection.
- Stable IDs and update timestamps are added to existing transaction/tag records without changing their financial values.
- Deletion tombstones are synced so records deleted on one device do not reappear from another.
- Home transaction rows use RecyclerView. Current-month entries are shown first; older history is loaded in batches as the list is scrolled.
- Home search matches transaction descriptions and tags.
- Dark-mode toggle is available on Home.
- Spending date controls and tag action controls have improved spacing; tag actions use edit/delete icons.
- Transaction and tag deletion remain protected by confirmation dialogs.

### Firebase setup for cloud sync

The Firebase project and Android app configuration already exist. Before using sign-in/sync, confirm:

1. In Firebase Console, open **Authentication → Sign-in method** and enable **Google**.
2. Deploy the included Firestore rules. With Firebase CLI installed and authenticated, run:

   ```sh
   firebase deploy --only firestore:rules --project batwa-f920b
   ```

   Or publish the contents of `firestore.rules` in Firestore **Rules** in the Firebase Console.
3. Keep the existing `google-services.json` associated with Firebase project `batwa-f920b`; do not replace it with a different project's configuration.

Firestore records are stored under each authenticated user's UID. The first sync merges local records with that account's cloud records. Sign-out does not delete local data.

### Build and validation status

This is a development branch, not yet a release APK. The GitHub Actions workflow in `.github/workflows/android-build.yml` attempts a debug build on branch updates. Do not install a build until CI succeeds and the APK has been checked on the phone. Keep `versionCode` unchanged until the release build is ready; increment it for the eventual release.
