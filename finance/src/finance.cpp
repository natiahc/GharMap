#include "paisapilot/finance.h"

#include <algorithm>
#include <stdexcept>
#include <unordered_map>

namespace paisapilot {
namespace {
void validate(Money value) {
    if (value < 0 || value > max_money)
        throw std::invalid_argument("Money must be nonnegative and within the supported range");
}
Money add(Money left, Money right) {
    if (left > max_money - right) throw std::overflow_error("Money total exceeds supported range");
    return left + right;
}
bool same(const Transaction& a, const Transaction& b) {
    return a.currency == b.currency && a.kind == b.kind &&
           a.amount == b.amount && a.reviewed == b.reviewed;
}
void validate_kind(Kind kind) {
    switch (kind) {
        case Kind::income: case Kind::expense: case Kind::expense_refund:
        case Kind::transfer: case Kind::card_repayment: case Kind::borrowed_principal:
        case Kind::loan_principal_repayment: case Kind::investment_contribution:
        case Kind::investment_withdrawal: return;
    }
    throw std::invalid_argument("Unknown transaction kind");
}
} // namespace

Money parse_amount(std::string_view decimal) {
    // Longest valid representation is 10000000000000.00 (16 digits + dot).
    if (decimal.empty() || decimal.size() > 17)
        throw std::invalid_argument("Amount must contain decimal digits within supported range");
    Money whole = 0;
    Money fraction = 0;
    unsigned fraction_digits = 0;
    bool decimal_point = false;
    bool whole_digit = false;
    for (char c : decimal) {
        if (c == '.' && !decimal_point && whole_digit) {
            decimal_point = true;
            continue;
        }
        if (c < '0' || c > '9')
            throw std::invalid_argument("Use unsigned decimal digits without grouping");
        const Money digit = c - '0';
        if (decimal_point) {
            if (++fraction_digits > 2)
                throw std::invalid_argument("Amount supports at most two fractional digits");
            fraction = fraction * 10 + digit;
        } else {
            whole_digit = true;
            if (whole > (max_money / 100 - digit) / 10)
                throw std::overflow_error("Amount exceeds supported range");
            whole = whole * 10 + digit;
        }
    }
    if (!whole_digit || (decimal_point && fraction_digits == 0))
        throw std::invalid_argument("Amount needs digits before and after a decimal point");
    if (fraction_digits == 1) fraction *= 10;
    return add(whole * 100, fraction);
}

std::string format_amount(Money minor_units) {
    if (minor_units < -max_money || minor_units > max_money)
        throw std::invalid_argument("Amount exceeds supported range");
    const bool negative = minor_units < 0;
    const Money magnitude = negative ? -minor_units : minor_units;
    const auto fraction = magnitude % 100;
    return (negative ? "-" : "") + std::to_string(magnitude / 100) + "." +
           (fraction < 10 ? "0" : "") + std::to_string(fraction);
}

Totals summarise(const std::vector<Transaction>& transactions, const std::string& currency) {
    if (currency.size() != 3 || !std::all_of(currency.begin(), currency.end(),
        [](char c) { return c >= 'A' && c <= 'Z'; }))
        throw std::invalid_argument("Use a three-letter uppercase currency code");
    Totals out;
    std::unordered_map<std::string, const Transaction*> seen;
    for (const auto& tx : transactions) {
        validate(tx.amount);
        validate_kind(tx.kind);
        if (tx.id.empty() || tx.currency != currency)
            throw std::invalid_argument("Transaction needs a stable ID and matching currency");
        const auto inserted = seen.emplace(tx.id, &tx);
        if (!inserted.second) {
            if (!same(*inserted.first->second, tx))
                throw std::invalid_argument("Conflicting transaction ID requires review");
            ++out.duplicates;
            continue;
        }
        if (!tx.reviewed) { ++out.pending_review; continue; }
        switch (tx.kind) {
            case Kind::income: out.income = add(out.income, tx.amount); break;
            case Kind::expense: out.expenses = add(out.expenses, tx.amount); break;
            case Kind::expense_refund: out.refunds = add(out.refunds, tx.amount); break;
            default: break;
        }
    }
    out.net_expenses = out.expenses - out.refunds;
    return out;
}

Budget plan(const BudgetInput& input) {
    for (Money amount : {input.monthly_take_home, input.monthly_spending,
         input.monthly_debt_payments, input.monthly_reserve_contribution,
         input.monthly_goal_contribution, input.accessible_reserves,
         input.essential_monthly_outgoings, input.remaining_risk_cap}) validate(amount);
    if (input.risk_basis_points > 10000 || input.reserve_target_months > 120)
        throw std::invalid_argument("Risk share or reserve duration outside supported range");
    Budget out;
    Money commitments = add(input.monthly_spending, input.monthly_debt_payments);
    commitments = add(commitments, input.monthly_reserve_contribution);
    commitments = add(commitments, input.monthly_goal_contribution);
    if (commitments > input.monthly_take_home)
        out.monthly_shortfall = commitments - input.monthly_take_home;
    else out.monthly_surplus = input.monthly_take_home - commitments;
    // Divide before multiplying to avoid overflow, preserving paise rounding down.
    const Money share = (out.monthly_surplus / 10000) * input.risk_basis_points +
        ((out.monthly_surplus % 10000) * input.risk_basis_points) / 10000;
    out.risk_contribution = input.risk_paused ? 0 : std::min(share, input.remaining_risk_cap);
    out.long_term_contribution = out.monthly_surplus - out.risk_contribution;
    if (input.reserve_target_months != 0 &&
        input.essential_monthly_outgoings > max_money / input.reserve_target_months)
        throw std::overflow_error("Reserve target exceeds supported range");
    out.reserve_target = input.essential_monthly_outgoings * input.reserve_target_months;
    out.reserve_gap = std::max<Money>(0, out.reserve_target - input.accessible_reserves);
    if (input.essential_monthly_outgoings > 0)
        out.runway_months = static_cast<double>(input.accessible_reserves) /
                            static_cast<double>(input.essential_monthly_outgoings);
    return out;
}
} // namespace paisapilot
