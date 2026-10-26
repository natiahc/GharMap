#include <jni.h>
#include "paisapilot/finance.h"
#include <exception>
#include <string>
namespace {
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), message);
}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_paisapilot_app_Engine_parse(JNIEnv* env, jclass, jstring text) {
    if (!text) { fail(env, "Amount is required"); return 0; }
    const char* chars = env->GetStringUTFChars(text, nullptr);
    if (!chars) return 0;
    const std::string decimal(chars);
    env->ReleaseStringUTFChars(text, chars);
    try { return paisapilot::parse_amount(decimal); }
    catch (const std::exception& e) { fail(env, e.what()); return 0; }
}
extern "C" JNIEXPORT jstring JNICALL Java_com_paisapilot_app_Engine_format(JNIEnv* env, jclass, jlong value) {
    try { return env->NewStringUTF(paisapilot::format_amount(value).c_str()); }
    catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_paisapilot_app_Engine_plan(JNIEnv* env, jclass, jlongArray values) {
    if (!values || env->GetArrayLength(values) != 11) { fail(env, "Invalid budget inputs"); return nullptr; }
    jlong v[11]; env->GetLongArrayRegion(values, 0, 11, v);
    if (env->ExceptionCheck()) return nullptr;
    if (v[7] < 0 || v[7] > 120 || v[8] < 0 || v[8] > 10000 || (v[10] != 0 && v[10] != 1)) {
        fail(env, "Invalid reserve duration, risk share or pause"); return nullptr;
    }
    try {
        paisapilot::BudgetInput in;
        in.monthly_take_home=v[0]; in.monthly_spending=v[1]; in.monthly_debt_payments=v[2];
        in.monthly_reserve_contribution=v[3]; in.monthly_goal_contribution=v[4];
        in.accessible_reserves=v[5]; in.essential_monthly_outgoings=v[6];
        in.reserve_target_months=static_cast<unsigned>(v[7]); in.risk_basis_points=static_cast<unsigned>(v[8]);
        in.remaining_risk_cap=v[9]; in.risk_paused=v[10]!=0;
        const auto b=paisapilot::plan(in);
        const jlong out[]={b.monthly_surplus,b.monthly_shortfall,b.long_term_contribution,b.risk_contribution,b.reserve_target,b.reserve_gap};
        auto result=env->NewLongArray(6);
        if (result) env->SetLongArrayRegion(result,0,6,out);
        return result;
    } catch(const std::exception& e) { fail(env,e.what()); return nullptr; }
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_paisapilot_app_Engine_project(JNIEnv* env,jclass,jlong initial,jlong monthly,jint years,jint returns,jint inflation,jint stepup) {
    try {
        const auto p=paisapilot::project(initial,monthly,years,returns,inflation,stepup);
        const jlong values[]={p.nominal,p.real}; auto out=env->NewLongArray(2);
        if(out) env->SetLongArrayRegion(out,0,2,values);
        return out;
    } catch(const std::exception& e) { fail(env,e.what()); return nullptr; }
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_paisapilot_app_Engine_totals(JNIEnv* env,jclass,jobjectArray ids,jintArray kinds,jlongArray amounts,jbooleanArray reviewed) {
    if(!ids||!kinds||!amounts||!reviewed) {fail(env,"Missing ledger fields");return nullptr;}
    const auto n=env->GetArrayLength(ids);
    if(n>100000 || env->GetArrayLength(kinds)!=n || env->GetArrayLength(amounts)!=n || env->GetArrayLength(reviewed)!=n) {
        fail(env,"Invalid ledger field lengths");return nullptr;
    }
    std::vector<jint> k(n);std::vector<jlong> a(n);std::vector<jboolean> r(n);
    env->GetIntArrayRegion(kinds,0,n,k.data());env->GetLongArrayRegion(amounts,0,n,a.data());env->GetBooleanArrayRegion(reviewed,0,n,r.data());
    if(env->ExceptionCheck())return nullptr;
    try {
        std::vector<paisapilot::Transaction> txs;txs.reserve(n);
        for(jsize i=0;i<n;++i) {
            auto id=static_cast<jstring>(env->GetObjectArrayElement(ids,i));
            if(!id || k[i]<0 || k[i]>10) {if(id)env->DeleteLocalRef(id);fail(env,"Invalid ledger row");return nullptr;}
            const char* chars=env->GetStringUTFChars(id,nullptr);if(!chars){env->DeleteLocalRef(id);return nullptr;}
            std::string key(chars);env->ReleaseStringUTFChars(id,chars);env->DeleteLocalRef(id);
            // Lent principal/returns, like transfers, are excluded from earnings/spending.
            auto kind=k[i]>=9?paisapilot::Kind::transfer:static_cast<paisapilot::Kind>(k[i]);
            txs.push_back({key,"INR",kind,a[i],r[i]!=0});
        }
        auto t=paisapilot::summarise(txs,"INR");const jlong values[]={t.income,t.expenses,t.refunds,static_cast<jlong>(t.pending_review)};
        auto out=env->NewLongArray(4);if(out)env->SetLongArrayRegion(out,0,4,values);return out;
    } catch(const std::exception& e) {fail(env,e.what());return nullptr;}
}
