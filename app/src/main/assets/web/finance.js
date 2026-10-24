/* Pure local finance logic. Amounts are integer paise; this pilot supports INR. */
(function (root, factory) { const api = factory(); if (typeof module === 'object') module.exports = api; else root.Pilot = api; })(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const TYPES = ['expense','income','refund','transfer','card_payment','loan_received','principal_received','interest_income','investment_buy','investment_sell','fee'];
  const KINDS = ['transaction','account','bill','debt','investment','goal','task'];
  const SPACES = ['personal','household','business'];
  const HEADERS = ['id','kind','space','name','amount_paise','balance_paise','monthly_paise','rate_percent','months','date','category','account','person','type','status','essential','updated','deleted','source_key','note','currency'];
  function money(s) {
    const t = String(s).trim().replace(/,/g,'').replace(/^(?:₹|INR|Rs\.?)[\s]*/i,'');
    if (!/^\d+(?:\.\d{1,2})?$/.test(t)) throw Error('Enter a non-negative amount with at most two decimal places.');
    const [a,b=''] = t.split('.'); const n = Number(a)*100 + Number(b.padEnd(2,'0'));
    if (!Number.isSafeInteger(n) || n > 100000000000000) throw Error('Amount exceeds the supported range.');
    return n;
  }
  function sum(xs) { const n=xs.reduce((a,b)=>a+b,0); if (!Number.isSafeInteger(n)) throw Error('Total exceeds supported precision.'); return n; }
  function dateValid(s) { return /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s+'T12:00:00Z')) && new Date(s+'T12:00:00Z').toISOString().slice(0,10)===s; }
  function today() { const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
  function id() { return typeof crypto!=='undefined' && crypto.randomUUID ? crypto.randomUUID() : 'p-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2); }
  function entry(p={}) { return validate(Object.assign({id:id(),kind:'transaction',space:'personal',name:'',amount:0,balance:0,monthly:0,rate:0,months:0,date:today(),category:'Other',account:'',person:'Me',type:'expense',status:'confirmed',essential:false,updated:new Date().toISOString(),deleted:false,sourceKey:'',note:'',currency:'INR'},p)); }
  function validate(e) {
    if (!e || typeof e !== 'object') throw Error('Invalid record.');
    for (const k of ['id','name','date','category','account','person','type','status','updated','sourceKey','note','currency']) if (typeof e[k]!=='string') throw Error('Invalid field: '+k);
    if (!/^[a-zA-Z0-9_-]{1,120}$/.test(e.id) || !KINDS.includes(e.kind) || !SPACES.includes(e.space)) throw Error('Invalid record identity or space.');
    if (!e.name.trim() || e.name.length>200 || e.note.length>2000 || e.sourceKey.length>250) throw Error('A name is required (up to 200 characters); note limit is 2000.');
    if (!dateValid(e.date) || Number.isNaN(Date.parse(e.updated))) throw Error('Invalid record date.');
    for (const k of ['amount','balance','monthly']) if (!Number.isSafeInteger(e[k]) || e[k]<0 || e[k]>100000000000000) throw Error('Invalid money field: '+k);
    if (!Number.isFinite(e.rate) || e.rate<0 || e.rate>1000 || !Number.isInteger(e.months) || e.months<0 || e.months>1200) throw Error('Invalid interest rate or term.');
    if (typeof e.deleted!=='boolean' || typeof e.essential!=='boolean' || e.currency!=='INR') throw Error('Only INR is supported in this pilot.');
    const status = e.kind==='task'?['open','done']:['review','confirmed','paid','archived'];
    if (!status.includes(e.status)) throw Error('Invalid record status.');
    if (e.kind==='transaction' && !TYPES.includes(e.type)) throw Error('Unknown transaction type.');
    return e;
  }
  function active(es,space) { return es.filter(e=>!e.deleted && e.space===space && !['review','archived'].includes(e.status)); }
  function summary(es,space,month=today().slice(0,7)) {
    const a=active(es,space);const tx=a.filter(e=>e.kind==='transaction' && e.date.startsWith(month));
    const expense=sum(tx.filter(e=>['expense','fee'].includes(e.type)).map(e=>e.amount));
    const refunds=sum(tx.filter(e=>e.type==='refund').map(e=>e.amount));
    const income=sum(tx.filter(e=>['income','interest_income'].includes(e.type)).map(e=>e.amount));
    const liquid=sum(a.filter(e=>e.kind==='account' && ['bank','cash'].includes(e.type)).map(e=>e.balance));
    const debt=sum(a.filter(e=>e.kind==='debt' && e.type==='borrowed').map(e=>e.balance));
    const cards=sum(a.filter(e=>e.kind==='account' && e.type==='card').map(e=>e.balance));
    const lent=sum(a.filter(e=>e.kind==='debt' && e.type==='lent').map(e=>e.balance));
    const invested=sum(a.filter(e=>e.kind==='investment').map(e=>e.balance));
    const bills=a.filter(e=>e.kind==='bill' && e.status!=='paid');
    const due=sum(bills.map(e=>e.amount));
    return {income,expense,refunds,netSpending:expense-refunds,liquid,debt,cards,lent,invested,due,afterCommitments:liquid-due,netWorth:liquid+lent+invested-debt-cards,review:es.filter(e=>!e.deleted&&e.space===space&&e.status==='review').length};
  }
  function scenario({savings,essential,otherIncome=0,oneOff=0,months=6}) {
    for(const n of [savings,essential,otherIncome,oneOff]) if(!Number.isSafeInteger(n)||n<0) throw Error('Scenario amounts must be non-negative paise.');
    if(!Number.isInteger(months)||months<1||months>120) throw Error('Choose 1–120 months.');
    const available=savings-oneOff;const burn=essential-otherIncome;
    return {available,burn,runway:available<0?0:burn>0?available/burn:null,target:Math.max(0,burn)*months+oneOff,gap:Math.max(0,Math.max(0,burn)*months+oneOff-savings)};
  }
  function emi(principal,annualRate,months) {
    if(!Number.isSafeInteger(principal)||principal<0||!Number.isFinite(annualRate)||annualRate<0||annualRate>1000||!Number.isInteger(months)||months<1||months>1200) throw Error('Invalid loan inputs.');
    if(!annualRate) return Math.round(principal/months);
    const r=annualRate/1200, factor=Math.pow(1+r,months); return Math.round(principal*r/(1-1/factor));
  }
  function futureValue(initial,monthly,annualRate,months) {
    if(!Number.isSafeInteger(initial)||!Number.isSafeInteger(monthly)||initial<0||monthly<0||!Number.isFinite(annualRate)||annualRate< -99||annualRate>100||!Number.isInteger(months)||months<1||months>600) throw Error('Invalid investment assumptions.');
    const r=Math.pow(1+annualRate/100,1/12)-1;const f=Math.pow(1+r,months);
    const n=Math.round(initial*f+(Math.abs(r)<1e-12?monthly*months:monthly*(f-1)/r));
    if(!Number.isSafeInteger(n))throw Error('Projection exceeds supported range.'); return n;
  }
  function split(total,aIncome,bIncome,mode='equal',aPercent=50) {
    if(![total,aIncome,bIncome].every(n=>Number.isSafeInteger(n)&&n>=0))throw Error('Invalid contribution amounts.');
    let ratio=.5;if(mode==='income'){if(aIncome+bIncome===0)throw Error('Income-based split needs income.');ratio=aIncome/(aIncome+bIncome);}else if(mode==='custom'){if(!Number.isFinite(aPercent)||aPercent<0||aPercent>100)throw Error('Choose a percentage from 0 to 100.');ratio=aPercent/100;}else if(mode!=='equal')throw Error('Unknown split mode.');
    const a=Math.round(total*ratio);return {a,b:total-a};
  }
  function hash(s) { let h=2166136261; for(const c of s){h^=c.charCodeAt(0);h=Math.imul(h,16777619);}return (h>>>0).toString(16); }
  function parseSms(body,sender='',timestamp=Date.now(),space='personal') {
    if(typeof body!=='string'||body.length>5000)return null;
    const s=body.replace(/\s+/g,' ').trim();
    if (/\b(?:otp|one[ -]time password|verification code|offer|cashback offer|eligible|pre-approved)\b/i.test(s)) return null;
    if (/\b(?:will be|scheduled|reminder|minimum (?:amount )?due|total (?:amount )?due|payment due|bill due|available limit)\b/i.test(s) && !/\b(?:debited|credited|spent|paid successfully|payment received|refund(?:ed)?|withdrawn|purchase of)\b/i.test(s)) return null;
    const m=s.match(/(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)(?![\d.])/i);if(!m)return null;
    const actual=/\b(?:debited|credited|spent|paid|withdrawn|refund(?:ed)?|received|purchase of|sent|transferred)\b/i.test(s);
    if(!actual||/\b(?:failed|declined|unsuccessful|request|will pay|please pay|can you|shall pay)\b/i.test(s))return null;
    let type='expense';
    if(/refund|reversal|reversed/i.test(s))type='refund';
    else if(/credit card.*(?:payment received|payment of|payment successful)|(?:payment.*credit card)|(?:card.*credited.*payment)/i.test(s))type='card_payment';
    else if(/\bcredited\b|\breceived\b/i.test(s))type='income';
    const ref=s.match(/(?:UPI\s*(?:Ref(?:erence)?\s*(?:No\.?)?|RRN)|(?:Txn|Transaction|Ref)(?:\s*(?:ID|No\.?))?)\s*[:#-]?\s*([A-Z0-9]{6,30})/i);
    const account=s.match(/(?:a\/?c|account|card)(?:\s*(?:no\.?|ending|number|xx|x|\*))*\s*[:*-]?\s*[xX*]*(\d{4})\b/i);
    const when=new Date(timestamp);if(Number.isNaN(when.getTime()))return null;
    const date=when.getFullYear()+'-'+String(when.getMonth()+1).padStart(2,'0')+'-'+String(when.getDate()).padStart(2,'0');
    const key=ref ? 'ref-'+ref[1].toUpperCase()+'-'+money(m[1])+'-'+type+'-'+(account?.[1]||'') : 'sms-'+timestamp+'-'+hash(sender+'|'+s);
    const merchant=s.match(/(?:\bat\b|\bto\b|\bfrom\b)\s+([A-Za-z][A-Za-z0-9 .@_-]{1,55})/i);
    return entry({space,name:merchant?merchant[1].trim():(sender||'Imported transaction'),date,amount:money(m[1]),account:account?'••'+account[1]:'',type,status:'review',sourceKey:key,note:'Detected on device. Verify payment type and account; original message is not stored.'});
  }
  function deduplicate(es,candidates) {
    const keys=new Set(es.filter(e=>e.sourceKey).map(e=>e.sourceKey));const out=[];
    for(const e of candidates){if(e.sourceKey&&keys.has(e.sourceKey))continue;if(e.sourceKey)keys.add(e.sourceKey);out.push(e);}return out;
  }
  function row(e) {validate(e);return [e.id,e.kind,e.space,e.name,String(e.amount),String(e.balance),String(e.monthly),String(e.rate),String(e.months),e.date,e.category,e.account,e.person,e.type,e.status,String(e.essential),e.updated,String(e.deleted),e.sourceKey,e.note,e.currency];}
  function fromRow(r,space) {
    if(r.length>HEADERS.length)throw Error('Unexpected record columns. Keep custom reports in another tab.');
    const v=Array.from({length:HEADERS.length},(_,i)=>String(r[i]??''));
    for(const i of [4,5,6,8]) if(!/^\d+$/.test(v[i]))throw Error('Money and month fields must be non-negative integers.');
    for(const i of [15,17])if(!['true','false'].includes(v[i]))throw Error('Boolean cells must be true or false.');
    if(!/^\d+(?:\.\d+)?$/.test(v[7]))throw Error('Invalid rate cell.');
    const e=validate({id:v[0],kind:v[1],space:v[2],name:v[3],amount:Number(v[4]),balance:Number(v[5]),monthly:Number(v[6]),rate:Number(v[7]),months:Number(v[8]),date:v[9],category:v[10],account:v[11],person:v[12],type:v[13],status:v[14],essential:v[15]==='true',updated:v[16],deleted:v[17]==='true',sourceKey:v[18],note:v[19],currency:v[20]});
    if(space&&e.space!==space)throw Error('Record belongs to a different space.');return e;
  }
  function fingerprint(e){return e?JSON.stringify(row(e)):null;}
  function merge(local,remote,base,space) {
    const make=es=>{const m=new Map();for(const e of es){validate(e);if(e.space!==space)throw Error('Cross-space data rejected.');if(m.has(e.id))throw Error('Duplicate record ID.');m.set(e.id,e);}return m;};
    const l=make(local),r=make(remote),b=make(base),merged=[],conflicts=[];
    for(const id of new Set([...l.keys(),...r.keys(),...b.keys()])){
      const le=l.get(id),re=r.get(id),be=b.get(id);const ls=fingerprint(le),rs=fingerprint(re),bs=fingerprint(be);
      if(be&&(!le||!re))throw Error('A previously synced row is missing. Restore it and set deleted=true to remove it safely.');
      if(ls===rs) {if(le)merged.push(le);}else if(ls===bs) {if(re)merged.push(re);}else if(rs===bs) {if(le)merged.push(le);}else conflicts.push({id,local:le,remote:re});
    }return {merged,conflicts};
  }
  function csv(es){const q=s=>'"'+String(s).replace(/"/g,'""')+'"';return [HEADERS,...es.map(row)].map(r=>r.map(v=>q(/^[=+@\-\t\r]/.test(String(v))?"'"+v:v)).join(',')).join('\r\n');}
  return {TYPES,KINDS,SPACES,HEADERS,money,sum,today,id,entry,validate,active,summary,scenario,emi,futureValue,split,parseSms,deduplicate,row,fromRow,fingerprint,merge,csv};
});
