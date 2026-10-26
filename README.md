# PaisaPilot

**Your money. Your plan. Your breathing room.**

PaisaPilot is the new direction for this repository: an Android-first personal
finance planner with on-device transaction capture, records in the user's own
Google Sheets, and optional household sharing.

## Current status

The first platform-independent C++17 finance core is implemented and tested.
It calculates reviewed income/expense totals, monthly contribution budgets and
emergency runway. It is not yet connected to Android or iOS screens.
Transaction capture, Google authorization, spreadsheet sync and sharing remain
unimplemented.

GharMap room-scanning development has been discontinued. Its Android/iOS source,
geometry engine, build scripts and existing workflows remain temporarily as
legacy code; building them still produces the GharMap prototype, not PaisaPilot.
Existing architecture and device-test documents describe that legacy project.
Git history is preserved.

## Planned first release

- Parse supported financial SMS on Android with permission and platform approval.
- Review uncertain transactions and handle refunds, transfers and card repayments.
- Sync structured records to an editable Google Sheet owned by the user.
- Track upcoming card bills, EMIs and household commitments.
- Estimate money available until payday and emergency savings runway.
- Invite a partner and share only explicitly selected household information.

Later phases cover investments, money lent/borrowed, business expenses and
financial tasks. These are roadmap items, not shipped capabilities.

## Data ownership

The intended design stores financial records on the device and in the user's
Google account, without a PaisaPilot-operated central financial database.
Google stores synced spreadsheets. Partner sharing is optional and requires
each person's explicit agreement. Cloud AI, if introduced, requires a separate
choice before sending financial information.

See the [product brief and phased roadmap](docs/PAISAPILOT_PRODUCT.md).

## Next implementation milestone

Connect the finance core to an offline transaction-review flow, then add
tested Android SMS parsing. Retire the legacy scanning app through explicit,
reviewable migration commits. Add Google Sheets sync after local accounting and
deduplication are reliable.

## Finance core

Run `bash scripts/test_finance.sh` with a C++17 compiler on Linux. The checks use
address/undefined-behaviour sanitizers; CI runs them independently of Android.
The standalone `finance/CMakeLists.txt` can also build the library and tests.

- All amounts use integer minor units (paise for INR), with range/overflow checks.
- Reporting accepts one currency and a caller-selected period. Only reviewed
  records count; exact repeated IDs count once and conflicting IDs fail.
  Stable IDs must be assigned by capture/reconciliation: different IDs are not
  automatically recognised as the same economic transaction.
- Purchases count as expenses; card repayments, account transfers, borrowed
  principal and investment principal do not count as income/expenses. Interest,
  fees and realised investment gains need separate income/expense records.
  Refunds are separate and can produce negative net expenses for a period.
  These totals are not an account-balance reconciliation or cash-flow statement.
- The budget uses take-home pay minus spending, debt payments, reserve saving
  and other goal contributions. Enter spending excluding debt payments and
  contributions to avoid double counting. A deficit produces no investment
  allocation. Accessible reserves never become monthly income.
- The user chooses a risk percentage (basis points); zero is the default.
  Risk contributions cannot exceed supplied remaining funding headroom, and
  pause mode redirects that contribution to long-term savings. The caller must
  persist and decrement headroom across months; the calculator holds no state
  and cannot enforce broker limits or stop losses.
- Runway uses accessible reserves divided by essential monthly outgoings,
  including essential debt payments. Zero outgoings returns unknown runway.
  Keep credit limits, market-dependent investments and expected repayments out
  of accessible reserves. Reserve duration is configurable (default six months).

The calculations are scenarios, not product recommendations, return forecasts
or trade execution. Only synthetic fixtures are committed.
