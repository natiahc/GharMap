# PaisaPilot

**Your money. Your plan. Your breathing room.**

An Android-first, offline INR finance pilot. Native Java screens and SQLite
storage share one C++17 engine for exact money conversion, accounting totals,
budget allocation and growth projections. No backend, analytics or cloud AI.

## Working in the Android pilot

- Manual transactions, review/correction/deletion, month selection, categories,
  account labels and ownership labels. Imported records start unreviewed.
- Separate income, purchases, refunds, transfers, card repayments, borrowing,
  loan principal and investment principal. No double counting card repayments.
- Explicit on-demand Android SMS import (last 30 days, at most 500 messages),
  conservative supported-format parsing, and paste/import for one shared alert.
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

**Not implemented:** Google authorization and automatic Sheets sync, cross-device
partner invitations/access/revocation, statement adapters for arbitrary banks,
receipts/OCR, recurring notifications and tax filing. The `core/` and old geometry
documents are legacy GharMap code; neither finance app depends on them.

Chats require explicit paste/share; there is no background conversation access.
SMS access is optional and may be denied or restricted. Store publication and
SMS policy approval have not been obtained. This debug APK is for sideload testing.
Android 8+ is required; compatibility with every device is not established.

## Privacy and data ownership

Records live in app-private SQLite storage; Android backup is disabled. No
Internet or camera permission is requested. SMS permission is requested only
when the user chooses inbox import. Device security remains important; there
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
