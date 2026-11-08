# Building Chess-Book on GitHub (no Android Studio)

## 1. Put the project on GitHub

1. Sign in to GitHub and create a new repository (for example `Chess-Book`).
2. Unzip the project on your computer. Make sure you can see the hidden `.github` folder.
3. Upload **everything inside the unzipped folder** to the repository (web: *Add file > Upload files*).
   The `.github/workflows` folder must be uploaded, or no build will start.
   If the web uploader skips hidden folders, use the `git` command line or GitHub Desktop instead.
4. Commit to the `main` branch.

## 2. Debug APK (for testing)

The **Android Debug Build** workflow starts on every push to `main`, on pull requests, and manually
(*Actions > Android Debug Build > Run workflow*). It:

1. Builds Stockfish from the official source (optional step; see `ENGINE_SETUP.md`)
2. Runs the unit tests
3. Builds the debug APK
4. Uploads it as the artifact **Chess-Book-debug-apk**

Open the finished run, scroll to *Artifacts*, download, unzip, install.
The run summary says whether real Stockfish or the fallback engine was bundled.

## 3. Release signing

Play Store and public releases need your own signing key. Never commit it to the repository.

Create a key once on any computer that has Java (`keytool` is included with Java):

```
keytool -genkeypair -v -keystore chessbook.jks -alias chessbook \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 chessbook.jks     # on macOS use: base64 -i chessbook.jks
```

Keep `chessbook.jks` and its passwords somewhere safe. If you lose them you cannot update the app.

Then in the repository open *Settings > Secrets and variables > Actions* and add four secrets:

| Secret | Value |
|---|---|
| `CHESSBOOK_KEYSTORE_B64` | the base64 output above |
| `CHESSBOOK_KEYSTORE_PASSWORD` | the keystore password |
| `CHESSBOOK_KEY_ALIAS` | `chessbook` (or the alias you chose) |
| `CHESSBOOK_KEY_PASSWORD` | the key password |

## 4. Release build

Create a tag such as `v1.0.0` (*Releases > Draft a new release > choose a tag*), or run
**Android Release Build** manually and enter a version.

- With the four secrets: it builds the signed APK and AAB, verifies the signature, writes
  `SHA256SUMS.txt`, and publishes a GitHub Release with all three files.
- Without them: it publishes **nothing** and only uploads a debug APK labelled *NOT-A-RELEASE*,
  with a warning in the run summary. It never produces an unsigned file pretending to be a release.

## Troubleshooting

- **Gradle: permission denied** - the workflows already run `chmod +x gradlew`.
- **Compile error in the first run** - open the failed step and send the error text; most are one-line fixes.
- **Stockfish step failed** - the build continues with the fallback engine. See `ENGINE_SETUP.md`.
