#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace paisapilot {
// One currency per ledger. Money is integer minor units (INR: paise).
using Money = std::int64_t;
constexpr Money max_money = 1'000'000'000'000'000;

// Exact decimal conversion for currencies with two minor-unit digits (e.g. INR).
// Input: unsigned ASCII digits, optionally '.' and one or two fractional digits.
// No whitespace, grouping, signs, exponents, currency symbols or silent rounding.
Money parse_amount(std::string_view decimal);
// Canonical two-decimal output; signed net totals are supported within +/-max_money.
std::string format_amount(Money minor_units);

enum class Kind {
    income, expense, expense_refund, transfer, card_repayment,
    borrowed_principal, loan_principal_repayment,
    investment_contribution, investment_withdrawal
};
struct Transaction {
    std::string id;
    std::string currency;
    Kind kind;
    Money amount;
    bool reviewed;
};
struct Totals {
    Money income = 0;
    Money expenses = 0;
    Money refunds = 0;
    Money net_expenses = 0; // Can be negative when prior-period purchases are refunded.
    std::size_t pending_review = 0;
    std::size_t duplicates = 0;
};
// Caller selects the reporting period before calling. Transfers, borrowing,
// card repayments and investment principal do not count as earnings/spending.
// Repeated identical IDs are counted once; conflicting IDs are rejected.
Totals summarise(const std::vector<Transaction>& transactions,
                 const std::string& currency);

struct BudgetInput {
    Money monthly_take_home = 0;
    Money monthly_spending = 0;
    Money monthly_debt_payments = 0;
    Money monthly_reserve_contribution = 0;
    Money monthly_goal_contribution = 0;
    Money accessible_reserves = 0;
    Money essential_monthly_outgoings = 0; // Include essential debt obligations.
    unsigned reserve_target_months = 6;
    unsigned risk_basis_points = 0; // User choice: 100 = 1%; no default risk allocation.
    Money remaining_risk_cap = 0; // Lifetime/current experiment funding headroom.
    bool risk_paused = false;
};
struct Budget {
    Money monthly_surplus = 0;
    Money monthly_shortfall = 0;
    Money long_term_contribution = 0;
    Money risk_contribution = 0;
    Money reserve_target = 0;
    Money reserve_gap = 0;
    std::optional<double> runway_months; // Unknown when essential outgoings are zero.
};
// A scenario calculator, not a return forecast or investment recommendation.
// Reserves are not added to monthly income. Negative or out-of-range inputs fail.
Budget plan(const BudgetInput& input);
struct Projection { Money nominal; Money real; };
// End-of-month contributions, effective annual rates, annual contribution step-up.
// Rates in basis points; return -99%..50%, inflation/step-up 0%..25%, horizon 1..60.
// Rounded down to paise. Rejects overflow; excludes taxes/fees and market variability.
Projection project(Money initial, Money monthly, int years, int returns,
                   int inflation, int stepup);
} // namespace paisapilot
