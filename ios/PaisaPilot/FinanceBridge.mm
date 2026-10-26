#import "FinanceBridge.h"
#include "paisapilot/finance.h"
#include <stdexcept>
#include <algorithm>
@implementation PPFinance
+ (NSDictionary*)run:(NSString*)operation input:(NSDictionary*)input {
    try {
        if([operation isEqualToString:@"parse"]) {
            NSString* text=input[@"text"];if(![text isKindOfClass:NSString.class])throw std::invalid_argument("Amount is required");
            return @{@"value":@(paisapilot::parse_amount(text.UTF8String))};
        }
        if([operation isEqualToString:@"format"])return @{@"value":[NSString stringWithUTF8String:paisapilot::format_amount([input[@"amount"] longLongValue]).c_str()]};
        if([operation isEqualToString:@"budget"]) {
            paisapilot::BudgetInput p;
            p.monthly_take_home=[input[@"takehome"] longLongValue];p.monthly_spending=[input[@"spending"] longLongValue];p.monthly_debt_payments=[input[@"debt"] longLongValue];
            p.monthly_reserve_contribution=[input[@"reserveadd"] longLongValue];p.monthly_goal_contribution=[input[@"goalsadd"] longLongValue];
            p.accessible_reserves=[input[@"reserves"] longLongValue];p.essential_monthly_outgoings=[input[@"essentials"] longLongValue];
            long long months=input[@"months"]?[input[@"months"] longLongValue]:6,risk=[input[@"riskbp"] longLongValue];
            if(months<0||months>120||risk<0||risk>10000)throw std::invalid_argument("Invalid plan range");
            p.reserve_target_months=(unsigned)months;p.risk_basis_points=(unsigned)risk;
            p.remaining_risk_cap=std::max<int64_t>(0,[input[@"riskcap"] longLongValue]-[input[@"riskfunded"] longLongValue]);
            const auto threshold=[input[@"losslimit"] longLongValue];p.risk_paused=[input[@"paused"] boolValue]||(threshold>0&&[input[@"riskloss"] longLongValue]>=threshold);
            auto b=paisapilot::plan(p);return @{@"surplus":@(b.monthly_surplus),@"shortfall":@(b.monthly_shortfall),@"longterm":@(b.long_term_contribution),@"risk":@(b.risk_contribution),@"target":@(b.reserve_target),@"gap":@(b.reserve_gap)};
        }
        if([operation isEqualToString:@"projection"]) {
            auto p=paisapilot::project([input[@"initial"] longLongValue],[input[@"monthly"] longLongValue],[input[@"years"] intValue],[input[@"returnbp"] intValue],[input[@"inflationbp"] intValue],[input[@"stepupbp"] intValue]);return @{@"nominal":@(p.nominal),@"real":@(p.real)};
        }
        if([operation isEqualToString:@"totals"]) {
            NSArray* rows=input[@"rows"];std::vector<paisapilot::Transaction> txs;
            NSArray* kinds=@[@"Income",@"Expense",@"Refund",@"Transfer",@"Card repayment",@"Borrowed principal",@"Loan principal repayment",@"Investment contribution",@"Investment withdrawal",@"Money lent",@"Lent principal returned"];
            for(NSDictionary* r in rows){NSUInteger index=[kinds indexOfObject:r[@"kind"]];if(index==NSNotFound)throw std::invalid_argument("Unknown transaction type");
                auto kind=index>=9?paisapilot::Kind::transfer:static_cast<paisapilot::Kind>(index);
                txs.push_back({[r[@"id"] UTF8String],"INR",kind,[r[@"amount"] longLongValue],[r[@"reviewed"] boolValue]});}
            auto t=paisapilot::summarise(txs,"INR");return @{@"income":@(t.income),@"expenses":@(t.expenses),@"refunds":@(t.refunds),@"pending":@(t.pending_review)};
        }
        throw std::invalid_argument("Unknown calculation");
    }catch(const std::exception& e){return @{@"error":[NSString stringWithUTF8String:e.what()]};}
}
@end
