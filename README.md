# PaisaPilot

**Your money. Your plan. Your breathing room.**

PaisaPilot is the new direction for this repository: an Android-first personal
finance planner with on-device transaction capture, records in the user's own
Google Sheets, and optional household sharing.

## Current status

Product planning only. PaisaPilot transaction capture, Google authorization,
spreadsheet sync, sharing and financial calculations are not implemented yet.

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

Create the finance data model and an offline transaction-review flow, then add
tested Android SMS parsing. Retire the legacy scanning app through explicit,
reviewable migration commits. Add Google Sheets sync after local accounting and
deduplication are reliable.
