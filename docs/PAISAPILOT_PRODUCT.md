# PaisaPilot product brief

Decision recorded: 25 October 2026.

## Product direction

Reuse this repository and its history for PaisaPilot. GharMap is discontinued.
The intended GitHub repository name is `paisapilot`; this document does not
assert that the hosting rename has happened.

Purpose: automatically organise money, help users choose what to share, and
plan financial commitments and savings using records they own.

Initial users: individuals and working couples. The product should remain
manageable as a side project. Business support is a later phase.

## Capture and data quality

Android financial SMS parsing runs on-device with explicit permission and
applicable Google Play review. Support specific tested message formats first;
do not claim complete coverage of banks, transactions or devices.

Chats are an explicit share/import input initially, not unrestricted background
access to personal conversations. A promise to pay is a task or expected amount,
not a confirmed transaction. Exclude OTPs and unrelated messages from exports.

iPhone needs a separately validated capture approach; do not promise Android
SMS-inbox parity. Manual corrections and statement imports remain necessary.

Keep record IDs, source references, provenance and review status. Deduplicate
repeated alerts and reconcile balances against statements. Show incomplete data
and last successful sync. Never commit real financial messages to this public repo.

## Accounting behaviour

- Credit-card purchases count as expenses once; repayments reduce liabilities.
- Account and partner transfers are not new income or additional expenses.
- Borrowed principal increases cash and debt; it is not earnings.
- Principal lent and repaid is distinct from interest earned.
- Track refunds, partial repayments, charges and interest separately.
- Distinguish realised investment returns, unrealised changes and distributions.
- Separate expected income and planned investments from confirmed transactions.
- Track business expenses paid personally and subsequent reimbursements without
  duplicating the expense.

Use integer minor currency units or exact decimal arithmetic, explicit currency
and dates, and transaction-level evidence for calculated totals.

## Storage and spreadsheet editing

Intended storage: local device database plus Google Sheets in the user's account.
No operator-held central financial-record database is planned. Any future
licensing/account metadata must be documented separately.

Google sign-in and spreadsheet authorization are separate. Prefer narrowly scoped
file access. Keep credentials in platform-protected storage.

Editable records need stable IDs, schema versioning, validation, change history,
conflict resolution and offline retry/deduplication. Custom reports may be added
without altering required fields. Do not silently overwrite external edits.

Suggested tabs: Transactions, Accounts, Cards, Commitments, Goals and Tasks.
Later tabs: Loans Taken, Money Lent, Investments and Business Reports.

## Household sharing

Both people independently accept sharing and choose its scope: selected expenses,
category totals, selected accounts or a broader shared view. Historical sharing
is a separate choice. Allow equal, income-based and custom contributions.

Private records stay in separate files. Hidden tabs are not access control.
Only approved records go into the shared household spreadsheet. Record ownership,
sharing changes and reconciliation must be explicit. Revoking future access
cannot retract copies another person has already obtained.

## Planning

- Upcoming bills and EMI calendar.
- Estimated money remaining until payday, with assumptions and missing data shown.
- Essential spending categories chosen by the user.
- Emergency runway based on accessible savings and essential outgoings.
- One-income and income-loss scenarios for a household.
- User-selected emergency-fund goals and recovery plans.
- Purchase-affordability and future investment-contribution scenarios.

Credit limits are borrowing capacity, not savings. Market-dependent assets and
expected repayments are separate from available cash. Investment projections use
visible assumptions and do not promise returns. Tracking is separate from
executing payments, lending or investments.

## Delivery phases

1. Offline finance model, transaction review and tested accounting rules.
2. Android SMS capture for supported formats, with corrections and reconciliation.
3. Google authorization and editable Sheets sync, including offline conflicts.
4. Explicit household sharing, commitments, cash-flow and emergency planning.
5. Investment tracking, loans, money lent, refund/subscription tracking and tasks.
6. Separate business spaces: receipts, receivables/payables, reimbursements,
   approval roles and selected CA access. Business-owned files stay under a
   business-controlled account. Full accounting and tax filing are additional scope.

The first household pilot should test whether users trust the totals, retain
capture permissions, return to planning and are willing to pay. Demand, revenue,
store approval, domain and trademark availability remain unvalidated.

## Implementation status — Android and iPhone pilots

Both legacy scanning apps have been replaced with native finance screens and a
shared C++ calculation engine. Android uses SQLite; iPhone uses protected atomic
storage. A versioned full snapshot and AES-GCM/PBKDF2 encrypted backup format
supports cross-platform restore, with independent interoperability fixtures.

Android implements manual/reviewed transactions, conservative pasted-alert import (no inbox permission),
CSV interchange, local spaces, goals, holdings, loan/bill settlements, contribution
splits, payday/risk/emergency/income-loss/retirement scenarios. iPhone implements
manual/reviewed transactions, spaces, goals, holdings, settlements and financial
planning. Android-specific SMS capture is not claimed on iPhone.

Android Google integration implements authorization, sheet create/join, record
sync, explicit version-conflict resolution, foreground auto-sync and collaborator
writer access/revocation. It requires Cloud OAuth registration, enabled APIs and
live account/device testing before being described as validated. Tokens are not
persisted. Received transactions require local review. Other spaces are excluded.

Sync uses an append-only journal; custom reports may be created in separate tabs.
Direct arbitrary spreadsheet-cell transaction editing is not supported. User
choices resolve concurrent versions instead of a silent last-writer overwrite.
Automatic sync only runs while the app is open. Connections are cleared by full
restore. Revocation cannot retract downloaded copies.

Pending: iPhone Google sync/CSV import, arbitrary bank statement adapters,
receipt OCR, recurring reminders, closed-app background sync and tax/accounting
features. Real iPhone installation/TestFlight needs Apple signing credentials.
Store policy review and cloud consent/verification remain deployment work.
