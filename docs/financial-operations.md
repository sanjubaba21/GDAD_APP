# Expense, cash movement, transfer, and reversal backend contract

Migrations `20260728030000_cash_movement_journal_kinds.sql` through
`20260728050000_system_financial_account_bootstrap.sql` expose protected financial
operations for the first release. Android must use these RPCs; authenticated clients
cannot write expense, journal, entry, account, or private request tables directly.

## Shared contract

- Every operation requires an active authenticated Owner in the active `p_shop_id`.
- `p_idempotency_key` is a caller-generated retry key of 1–160 trimmed characters.
  Persist and reuse it until the authoritative result arrives. Exact retry returns the
  original result; changed reuse fails.
- `p_business_date` is a Nepal business date, never future, at most seven days old, and
  inside a locked open accounting period.
- Amounts are positive whole paisa. Descriptions and notes are trimmed and bounded.
- Cash and bank balances derive from immutable journal entries. Expenses, withdrawals,
  transfer sources, and reversals that remove funds cannot make a balance negative.
- Each success posts one balanced journal and secret-free audit evidence atomically.
  Failure rolls back all request, expense, journal, entry, and audit effects.

## Expense

Call `public.post_expense` with `p_source_account_id`, `p_amount_paisa`,
`p_business_date`, `p_category`, and optional `p_payee`/`p_note`. The source must be an
active same-shop cash or bank account with sufficient funds. The server selects the
protected `expense_control` account.

The result contains `expense_id`, `journal_transaction_id`, `amount_paisa`, and
`source_balance_after_paisa`. The journal debits expense and credits the source account.

## Deposit or withdrawal

Call `public.post_cash_movement` with `p_movement_type` (`deposit` or `withdrawal`), an
active same-shop cash/bank `p_account_id`, `p_amount_paisa`, `p_business_date`, and
`p_description`. The server uses `cash_movement_clearing`. A deposit debits cash/bank
and credits clearing; a withdrawal posts the reverse and requires sufficient funds.

The result contains `movement_type`, `journal_transaction_id`, `amount_paisa`, and
`account_balance_after_paisa`.

## Transfer

Call `public.post_account_transfer` with distinct active same-shop
`p_from_account_id`/`p_to_account_id`, `p_amount_paisa`, `p_business_date`, and
`p_description`. Both accounts must be cash or bank. They lock in stable order and the
source must have sufficient funds.

The result contains `journal_transaction_id`, `amount_paisa`,
`from_balance_after_paisa`, and `to_balance_after_paisa`. One journal debits the
destination and credits the source; there is no partial transfer state.

## Reversal

Call `public.reverse_financial_operation` with the original
`p_journal_transaction_id`, `p_business_date`, and `p_reason`. Only Task 3.7 expense,
deposit, withdrawal, and transfer journals are eligible, and each can be reversed once.

The server locks involved accounts, verifies that reversing previously added funds will
not cause an overdraft, and posts a new journal with every original debit/credit
exchanged. Original evidence is never rewritten. The result contains
`journal_transaction_id`, `reversal_journal_id`, and `original_kind`.

## System account bootstrap

Existing shops are backfilled and application-created shops are provisioned with 11
protected purpose accounts: cash, bank, receivable, payable, inventory, revenue, cost
of goods sold, expense, opening equity, inventory adjustment clearing, and cash-movement
clearing. Existing purpose rows are preserved. The deterministic seed uses fixed fixture
IDs while containing the same required purpose set.

## Failure categories

- `42501`: unauthenticated/non-Owner, inactive membership/shop, cross-shop or inactive
  account, or ineligible/already-reversed journal.
- `22023`: invalid key, amount, date, text, movement type, same-account transfer, or
  changed retry payload.
- `23514`: insufficient funds for debit or reversal.
- `55000`: closed accounting period or missing protected control account.

Clients should map SQL states to stable user-facing errors and display only the
server-returned balances and identifiers after success.
