# PaisaPilot

**Your money. Your plan. Your breathing room.**

An Android-first, offline INR finance pilot. Native Java screens and SQLite
storage share one C++17 engine for exact money conversion, accounting totals,
budget allocation and growth projections. No app-operated backend, analytics or cloud AI. Google sync is optional.

## Working in the Android pilot

- Manual transactions, review/correction/deletion, month selection, categories,
  account labels and ownership labels. Imported records start unreviewed.
- Separate income, purchases, refunds, transfers, card repayments, borrowing,
  loan principal and investment principal. No double counting card repayments.
- Opt-in live incoming SMS capture, on-demand inbox import and pasted financial
  alerts, using one conservative candidate parser and review flow.
  OTP/authentication messages, future-payment messages and ambiguous multi-amount
  messages are skipped. Raw message bodies are not saved. Manual review is vital.
- Exact-decimal transaction CSV import/export through Android's file picker.
  Exports open in Google Sheets/Excel; text cells are escaped against formulas.
  Stable-ID duplicates are skipped; conflicts reject the entire import.
- Local personal, household and business spaces. Copy selected transactions to
  another space only after approval; copied records need review. Export only
  the selected space. Equal or income/custom-weight household cost calculator.
- Goals with target/saved amounts, deadline and no-growth monthly requirement.
- Manual investment holdings with cost, valuation date and unrealised change.
- One-off bills, card bills, EMI principal, loans taken, money lent and tasks.
  Partial/full settlement updates the outstanding amount and records the correct
  principal/expense transaction atomically. Interest must be entered separately.
- Monthly long-term/risk contribution scenarios, essential-expense reserves,
  emergency gap, no-income/one-income runway, risk funding cap and loss pause.
- Password-encrypted full backup/restore across all spaces and record sections.
  Restore is atomic and rejects invalid data; credentials/connections are excluded.
- Money-until-payday scenarios from available cash, daily spending and unpaid
  commitments, excluding expected income and money lent.
- Retirement growth scenarios with visible annual return, inflation, contribution
  step-up and horizon assumptions. Rates are user-editable, not predictions.

## Important boundaries

This is a pilot, not complete accounting, investment advice or trade execution.
It does not place investments, promise returns, fetch live prices or enforce a
broker's loss limits. Plan amounts and risk headroom are entered manually; keep
funding/losses current. Holdings/goals do not automatically become transactions.
Recorded income/spending is not a reconciled account balance.

**Integration status:** Android includes Google authorization, create/join sheet,
append-only record sync, explicit conflict review, foreground automatic sync,
and Google-account partner writer access/revocation. Live Google testing requires
a configured Cloud project; see setup below. The iPhone finance app uses local
records and interoperable backups; Google sync is not implemented on iPhone.
Arbitrary bank statement adapters, receipts/OCR, recurring notifications and tax
filing remain unimplemented. Legacy geometry code under `core/` is unused.

Chats require explicit paste/share; there is no background conversation access.
Full SMS capture is retained. Internet-sideloaded apps requesting SMS access
can be blocked by Play Protect. Store publication and
SMS policy approval have not been obtained. This debug APK is for sideload testing.
Android 8+ is required; compatibility with every device is not established.

## Privacy and data ownership

Records live in app-private SQLite storage; Android backup is disabled. Internet permission supports optional Google-authorized sync; no camera
permission is requested. SMS reading/receiving permissions are requested only for enabled capture/import;
notification-listener and accessibility access are not requested. Device security remains important; there
is no separate app database encryption or app lock yet.

CSV exports are readable by recipients and contain selected-space transactions,
including notes and pending entries. They exclude other spaces. They do not
include goals, plans, holdings or commitments and are **not a complete backup**.
Use the encrypted full-backup action for those records.
Uninstalling deletes local records. External edits with conflicting IDs require
manual reconciliation rather than silent replacement. Different IDs/sources
may still represent the same transaction; verify against statements.

## Build and verify

JDK 17, Android SDK 35, NDK 27.2.12479018 and CMake 3.22.1:

```sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
bash scripts/test_finance.sh
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (CI artifact: `PaisaPilot-debug`).
Android CI compiles arm64, armv7 and x86_64, runs lint and exercises the app on an
API-30 emulator. Tests cover review, accounting, exact CSV round trips, conflicts,
space isolation, loan/card settlements and real-screen persistence.

The finance core also builds independently with `finance/CMakeLists.txt`.
Integer money is paise, capped at 10^15 paise. Exact parsing supports currencies
with two minor-unit digits; the pilot itself accepts INR only. Projections use
effective annual return, end-of-month contributions and annual step-ups, round
down to paise, and exclude taxes/fees, market variability and withdrawals.

See [the product roadmap](docs/PAISAPILOT_PRODUCT.md) for the longer-term scope.

## Portable encrypted backups

Format v1: `PPBK` ASCII, version byte `01`, 16 random salt bytes, 12 random nonce
bytes, then AES-256-GCM ciphertext and its 16-byte authentication tag. The first
33 bytes are authenticated additional data. Key derivation is PBKDF2-HMAC-SHA256,
210,000 iterations, 32-byte output. Passphrases use 12–128 printable ASCII
characters for platform-independent encoding; keep a strong passphrase separately.
The app does not store it and cannot recover it. Limit: 10 MB.

Plaintext schema: `{format:"PaisaPilot",version:1,spaces:[{id,name,type}],records:[…]}`.
Records use the same IDs/sections/paise fields on both platforms. All spaces are
replaced only after validation; invalid restores preserve existing data. Network
credentials and sync metadata are excluded and need explicit reconnection.
`fixtures/backup-v1.b64` is a synthetic independently generated interoperability
fixture; its test-only password is `fixture passphrase only`. Never use that
password or deterministic fixture salt/nonce for real backups.

## iPhone finance app

`ios/` now contains native SwiftUI finance screens backed by the same C++ engine:
reviewed/manual transactions, separate spaces, goals, holdings, commitments/loan
settlements, budgets, risk caps, retirement and income-loss scenarios. Local
records use protected atomic storage excluded from device backups. Full encrypted
backups share the Android format and interoperability fixture. iOS has no Android
SMS-inbox access. Google sync and transaction CSV import/export are Android-only
work at this checkpoint; iPhone currently uses manual entry and portable backups.

Generate with XcodeGen using `ios/project.yml`; scheme `PaisaPilot`, iOS 16+.
CI builds and runs accounting, persistence and backup tests on an iPhone simulator.
Installing on a real iPhone/TestFlight requires Apple signing and provisioning;
the simulator `.app` artifact is not a sideloadable iPhone `.ipa`.

## Google Sheets and partner setup (Android)

The app uses Google Identity Services `AuthorizationClient`, not pasted tokens
or a server-held refresh token. Register an **Android OAuth client** in your
Google Cloud project for `com.paisapilot.app` and the SHA-1 in the CI artifact's
`signing-certificate.txt`. Enable Google Sheets API and Google Drive API. Configure
the OAuth consent screen, privacy information and test users; publication or
verification is a separate step. No OAuth client secret belongs in this repo.
A new debug signing key may need re-registration on another build; use your own
stable signing configuration for ongoing distribution.

Scopes: `spreadsheets` (access needed to join a partner's existing spreadsheet)
and `drive.file` (create/manage permissions on app-created files). The Sheets
scope is broader than one file; explain it in consent. The app only targets the
explicitly connected sheet. Tokens are requested on demand and held in memory,
never saved in records, backups or logs. Offline use needs no Google account.

Create a separate household space; copy only approved records into it. Connect
it after approving the scope, then grant the partner's exact Google email writer
access. No notification email is sent. The partner authorizes Google separately
and explicitly joins the spreadsheet link from their chosen local space. Google
permissions enforce access; the app's local owner label is not a security boundary.
Revoke collaborators in the owner UI or Google Drive. Disconnecting a device only
removes its local connection and does not revoke other collaborators/copies.

Foreground auto-sync checks roughly once a minute while the app is open, and
after edits. It pauses on errors/consent/conflicts. There is no closed-app
background sync or iPhone Google sync yet. Last success/errors are shown.
Restoring a backup clears connection state and requires explicit reconnection.

`PaisaPilotEvents` is an append-only change journal, including a human-readable
description/type/amount. Do not edit/delete/reorder journal rows; create report
formulas/custom views in separate tabs. Direct arbitrary spreadsheet edits are
not a supported transaction-editing interface. The app does not silently replace
external rows. Revisions keep IDs and parents; independent records merge,
conflicting edits require a version choice, and missing/edited synced history
stops sync. Local edits during network requests abort the local apply and are
retried by the next merge. Received transactions need local review; review state
is not shared. The sync journal is not end-to-end encrypted: Google and authorized
collaborators can read that selected space. Full file backups are encrypted.

Live OAuth, permissions and multi-device API testing remain required after the
Cloud project is configured. Automated tests exercise protocol merges, conflicts,
deletions, private-space collision protection and restore disconnect behavior.

## Live Android capture — 0.4.0

The full-feature build retains `READ_SMS` and adds `RECEIVE_SMS` for opt-in live
capture, as requested. Enable it in Data after reading the disclosure and granting
permissions. A protected system SMS receiver processes supported incoming alerts
in the background; candidates are saved to Personal, unreviewed. Open screens
refresh when records arrive. OTP/authentication content and raw SMS bodies are
not stored. Stop capture in Data or revoke permission in Android settings.

Capture and history import share IDs when message sender, body and sent timestamp
match. Missing timestamps, unsupported/ambiguous formats and cross-source imports
still need statement reconciliation. Android force-stop and device policy can
prevent delivery; this is not a promise of complete capture on every model.
Google Sheets auto-sync remains foreground polling, not instant cross-device push.

Play Protect can block Internet-sideloaded APKs with SMS permissions. Rebuilding,
renaming or changing the signing key is not an approved-distribution substitute.
Google Play policy lists SMS-based money management as a potential exception,
subject to declaration/review. A Play Console testing/review setup is needed for
that route. No store approval is claimed; keep Play Protect enabled. Version
0.3.1 was the reduced-permission test build; 0.4.0 supersedes it for full capture.

CI tests the opt-in/deduplication pipeline and injects a synthetic SMS into an
emulator to verify the actual system receiver. Run `bash scripts/test_live_sms.sh`
after connected tests with emulator/test APKs installed. Tests clear app data;
use an emulator rather than a phone containing your records.
