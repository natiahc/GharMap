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
