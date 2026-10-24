# PaisaPilot paused checkpoint

Paused at the user's request on 25 October 2026 to prioritise VLA work for the next 3–7 weeks. Resume only when requested; no overnight/background development is scheduled.

## Saved work

- Main README and product brief capture the intended app and privacy model.
- Repository rename to natiahc/PaisaPilot is verified.
- Experimental standalone finance.js covers integer-paise calculations, transaction types, SMS candidate parsing, three-way spreadsheet merging, and CSV export.
- Seventeen Node tests exist. Last run: 16 passed, 1 failed. The parser incorrectly accepts a future debit notice ('INR 100 will be debited tomorrow'). Fix this before integrating capture.

## Not implemented or audited

The existing Android/iOS apps still build the legacy GharMap scanner. No PaisaPilot UI, SMS permissions/receiver, Google authorization, spreadsheet networking, partner sharing, business workflow or mobile integration has been implemented. The experimental JavaScript is not connected to those apps. A complete security/financial audit and device validation remain outstanding. These files are an unfinished checkpoint, not a finance release.

## Resume

Read docs/PAISAPILOT_PRODUCT.md and this checkpoint. Run node --test tests/finance.test.cjs, fix the known parser failure, then implement the phased roadmap. Preserve VLA repositories and simulation processes.
