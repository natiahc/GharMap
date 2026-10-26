#include "paisapilot/finance.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

using namespace paisapilot;
void check(bool condition) { if (!condition) throw std::runtime_error("Check failed"); }
template<class F> void rejects(F action) {
    bool failed = false;
    try { action(); } catch (const std::invalid_argument&) { failed = true; }
    catch (const std::overflow_error&) { failed = true; }
    check(failed);
}
int main() {
    // Text boundaries for UI/statement imports must never go through float/double.
    check(parse_amount("0") == 0 && parse_amount("0.29") == 29);
    check(parse_amount("123.4") == 12340 && parse_amount("001.01") == 101);
    check(parse_amount("10000000000000.00") == max_money);
    check(parse_amount("9999999999999.99") == max_money - 1);
    for (const char* invalid : {"", ".", ".5", "1.", "-1", "+1", " 1", "1 ",
         "1,000", "1e2", "NaN", "inf", "1.001", "1..0", "1.2.3", "₹1", "١",
         "10000000000000.01", "10000000000001", "99999999999999999",
         "000000000000000000"}) {
        rejects([&] { parse_amount(invalid); });
    }
    rejects([] { parse_amount(std::string_view("1\0.00", 5)); });
    check(format_amount(0) == "0.00" && format_amount(29) == "0.29");
    check(format_amount(-29) == "-0.29" && format_amount(-12340) == "-123.40");
    check(format_amount(-max_money) == "-10000000000000.00");
    rejects([] { format_amount(std::numeric_limits<Money>::min()); });
    rejects([] { format_amount(std::numeric_limits<Money>::max()); });
    for (Money amount : {Money{0}, Money{1}, Money{99}, Money{100}, Money{101},
                         Money{29000000}, max_money - 1, max_money})
        check(parse_amount(format_amount(amount)) == amount);
    // Check every paise value across many decimal boundaries, plus large values.
    for (Money amount = 0; amount <= 100000; ++amount)
        check(parse_amount(format_amount(amount)) == amount);
    for (Money amount = max_money - 10000; amount <= max_money; ++amount)
        check(parse_amount(format_amount(amount)) == amount);

    // Synthetic paise amounts; card purchase and repayment must not double count.
    std::vector<Transaction> ledger = {
        {"salary", "INR", Kind::income, 25000000, true},
        {"purchase", "INR", Kind::expense, 29000000, true},
        {"repayment", "INR", Kind::card_repayment, 29000000, true},
        {"transfer", "INR", Kind::transfer, 5000000, true},
        {"loan", "INR", Kind::borrowed_principal, 10000000, true},
        {"principal", "INR", Kind::loan_principal_repayment, 1000000, true},
        {"sip", "INR", Kind::investment_contribution, 10000000, true},
        {"redemption", "INR", Kind::investment_withdrawal, 1000000, true},
        {"refund", "INR", Kind::expense_refund, 100000, true},
        {"uncertain", "INR", Kind::income, 5000000, false}
    };
    ledger.push_back(ledger[1]);
    auto totals = summarise(ledger, "INR");
    check(totals.income == 25000000 && totals.expenses == 29000000);
    check(totals.net_expenses == 28900000 && totals.pending_review == 1 && totals.duplicates == 1);
    auto conflict = ledger;
    conflict.back().amount++;
    rejects([&] { summarise(conflict, "INR"); });
    rejects([&] { summarise(ledger, "USD"); });
    rejects([&] { summarise(ledger, "inr"); });
    rejects([] { summarise({{"", "INR", Kind::income, 1, true}}, "INR"); });
    rejects([] { summarise({{"bad", "INR", Kind::expense, -1, true}}, "INR"); });
    rejects([] { summarise({{"bad", "INR", static_cast<Kind>(99), 0, true}}, "INR"); });
    rejects([] { summarise({{"a", "INR", Kind::income, max_money, true},
                           {"b", "INR", Kind::income, 1, true}}, "INR"); });
    check(summarise({{"refund", "INR", Kind::expense_refund, 500, true}}, "INR").net_expenses == -500);

    BudgetInput in;
    in.monthly_take_home = 25000000;
    in.monthly_spending = 8000000;
    in.monthly_reserve_contribution = 2000000;
    in.accessible_reserves = 45000000;
    in.essential_monthly_outgoings = 7500000;
    in.risk_basis_points = 667;
    in.remaining_risk_cap = 5000000;
    auto budget = plan(in);
    check(budget.monthly_surplus == 15000000 && budget.monthly_shortfall == 0);
    check(budget.risk_contribution == 1000500);
    check(budget.long_term_contribution + budget.risk_contribution == budget.monthly_surplus);
    check(budget.reserve_target == 45000000 && budget.reserve_gap == 0);
    check(budget.runway_months && std::abs(*budget.runway_months - 6.0) < 1e-12);
    in.remaining_risk_cap = 123;
    check(plan(in).risk_contribution == 123);
    in.risk_paused = true;
    check(plan(in).risk_contribution == 0 && plan(in).long_term_contribution == 15000000);
    in.risk_paused = false;
    in.monthly_debt_payments = 29000000;
    budget = plan(in);
    check(budget.monthly_shortfall == 14000000 && budget.monthly_surplus == 0);
    check(budget.risk_contribution == 0 && budget.long_term_contribution == 0);
    in.essential_monthly_outgoings = 0;
    check(!plan(in).runway_months);
    in.monthly_spending = -1;
    rejects([&] { plan(in); });
    in = BudgetInput{};
    in.risk_basis_points = 10001;
    rejects([&] { plan(in); });
    in = BudgetInput{};
    in.essential_monthly_outgoings = max_money;
    rejects([&] { plan(in); });
    in = BudgetInput{};
    in.monthly_take_home = max_money;
    in.risk_basis_points = 10000;
    in.remaining_risk_cap = max_money;
    check(plan(in).risk_contribution == max_money);
    in.monthly_take_home = 10001;
    in.risk_basis_points = 1;
    check(plan(in).risk_contribution == 1 && plan(in).long_term_contribution == 10000);
    in.monthly_spending = max_money;
    in.monthly_debt_payments = 1;
    rejects([&] { plan(in); });
    check(plan(BudgetInput{}).monthly_surplus == 0);
    std::cout << "PaisaPilot finance checks passed\n";
}
