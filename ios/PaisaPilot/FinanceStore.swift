import Foundation
import CoreFoundation
import SwiftUI

struct PilotError: LocalizedError { let message: String; var errorDescription: String? { message } }
func require(_ condition: Bool, _ message: String) throws { if !condition { throw PilotError(message: message) } }
func engine(_ operation: String, _ input: [String: Any]) throws -> [String: Any] {
    let output = PPFinance.run(operation, input: input) as? [String: Any] ?? [:]
    if let error = output["error"] as? String { throw PilotError(message: error) }
    return output
}
func paise(_ text: String) throws -> Int64 { (try engine("parse", ["text": text.isEmpty ? "0" : text])["value"] as? NSNumber)?.int64Value ?? 0 }
func decimal(_ value: Int64) -> String { (try? engine("format", ["amount": value])["value"] as? String) ?? "Invalid amount" }
func rupees(_ value: Int64) -> String { "₹" + decimal(value) }
let recordKinds = ["Income", "Expense", "Refund", "Transfer", "Card repayment", "Borrowed principal", "Loan principal repayment", "Investment contribution", "Investment withdrawal", "Money lent", "Lent principal returned"]
let sections = ["Transaction", "Commitment", "Goal", "Holding", "Settings"]
let moneyKeys = ["takehome", "spending", "debt", "reserveadd", "goalsadd", "reserves", "essentials", "retained", "initial", "riskcap", "riskfunded", "riskloss", "losslimit", "paydaycash", "dailyspend"]
let planFields: [(String, String)] = [("takehome","Take-home income"),("spending","Spending excluding debt"),("debt","Debt payments"),("reserveadd","Reserve contribution"),("goalsadd","Other goal contributions"),("reserves","Accessible reserves"),("essentials","Essential outgoings including debt"),("retained","Income retained after job loss"),("initial","Existing long-term assets"),("riskcap","Experiment funding cap"),("riskfunded","Capital already funded"),("riskloss","Cumulative experiment loss"),("losslimit","Loss pause threshold")]
func day(_ date: Date = Date()) -> String { let f = DateFormatter(); f.locale = Locale(identifier:"en_US_POSIX"); f.timeZone = TimeZone(secondsFromGMT:0); f.dateFormat = "yyyy-MM-dd"; return f.string(from:date) }
func dateValue(_ text: String) throws -> Date {
    let f=DateFormatter(); f.locale=Locale(identifier:"en_US_POSIX"); f.timeZone=TimeZone(secondsFromGMT:0); f.dateFormat="yyyy-MM-dd"; f.isLenient=false
    guard let date=f.date(from:text), f.string(from:date)==text else {throw PilotError(message:"Use date YYYY-MM-DD")};return date
}
struct PilotSpace: Identifiable { let id: String; let name: String; let type: String; var json: [String:Any] {["id":id,"name":name,"type":type]} }
struct PilotRecord: Identifiable {
    var value: [String:Any]
    var id: String { str("id") }
    func str(_ key: String, _ fallback: String = "") -> String { value[key] as? String ?? fallback }
    func num(_ key: String, _ fallback: Int64 = 0) -> Int64 { (value[key] as? NSNumber)?.int64Value ?? fallback }
    func flag(_ key: String) -> Bool { value[key] as? Bool ?? false }
    static func new(_ space: String, _ section: String) -> PilotRecord { PilotRecord(value:["id":UUID().uuidString,"space":space,"section":section,"title":"","date":day(),"amount":Int64(0),"saved":Int64(0),"kind":section=="Transaction" ? "Expense" : "Other","currency":"INR","reviewed":true,"source":"manual"]) }
}
@MainActor final class FinanceStore: ObservableObject {
    @Published private(set) var spaces = [PilotSpace(id:"personal",name:"Personal",type:"Personal")]
    @Published private(set) var records: [PilotRecord] = []
    @Published var error: String?
    private var locked=false
    private let file: URL
    init(directory: URL? = nil) {
        let folder=directory ?? FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("PaisaPilot",isDirectory:true)
        file=folder.appendingPathComponent("ledger.json")
        do {try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true);var url=folder;var values=URLResourceValues();values.isExcludedFromBackup=true;try url.setResourceValues(values)
            if FileManager.default.fileExists(atPath:file.path){let decoded=try Self.decode(Data(contentsOf:file));spaces=decoded.0;records=decoded.1}
        }catch{self.error="Could not load records: \(error.localizedDescription). Restore a backup to recover.";locked=true}
    }
    static func decode(_ data: Data) throws -> ([PilotSpace],[PilotRecord]) {
        try require(data.count <= 10_000_000,"Snapshot exceeds 10 MB")
        guard let root=try JSONSerialization.jsonObject(with:data) as? [String:Any], root["format"] as? String=="PaisaPilot",root["version"] as? Int==1,
              let scopes=root["spaces"] as? [[String:Any]],let rows=root["records"] as? [[String:Any]] else {throw PilotError(message:"Invalid backup schema")}
        try require(!scopes.isEmpty && scopes.count<=100 && rows.count<=100000,"Invalid record counts")
        var spaceIds=Set<String>(),recordIds=Set<String>();var result=[PilotSpace]()
        for s in scopes {guard let id=s["id"] as? String,let name=s["name"] as? String,let type=s["type"] as? String else {throw PilotError(message:"Invalid space")}
            try require(!id.isEmpty&&id.count<=200 && !name.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty&&name.count<=80&&spaceIds.insert(id).inserted&&["Personal","Household","Business"].contains(type),"Invalid space")
            if id=="personal" {try require(type=="Personal","Invalid personal space")};result.append(PilotSpace(id:id,name:name,type:type))
        }
        try require(spaceIds.contains("personal"),"Personal space missing")
        let records=rows.map{PilotRecord(value:$0)}
        for r in records {try require(recordIds.insert(r.id).inserted,"Repeated record ID");try validate(r,spaceIds)}
        return(result,records)
    }
    private static func integer(_ r: PilotRecord,_ key: String,_ signed: Bool=false)throws {
        guard let n=r.value[key] as? NSNumber,CFGetTypeID(n) != CFBooleanGetTypeID(), !["d","f"].contains(String(cString:n.objCType)) else {throw PilotError(message:"Invalid integer \(key)")}
        try require(n.int64Value >= (signed ? -1_000_000_000_000_000 : 0) && n.int64Value<=1_000_000_000_000_000,"Amount outside supported range")
    }
    static func validate(_ r: PilotRecord,_ spaces:Set<String>) throws {
        try require(!r.id.isEmpty&&r.id.count<=200&&spaces.contains(r.str("space"))&&sections.contains(r.str("section")),"Invalid record identity")
        if r.str("section")=="Settings" {
            try require(r.id=="settings-"+r.str("space"),"Invalid settings identity")
            for key in moneyKeys where r.value[key] != nil {try integer(r,key)}
            for key in ["months","riskbp","years","returnbp","inflationbp","stepupbp"] where r.value[key] != nil {try integer(r,key,key=="returnbp")}
            try require((0...120).contains(r.num("months",6))&&(0...10000).contains(r.num("riskbp"))&&(1...60).contains(r.num("years",10))&&(-9900...5000).contains(r.num("returnbp",800))&&(0...2500).contains(r.num("inflationbp",600))&&(0...2500).contains(r.num("stepupbp")),"Invalid plan range")
            if let value=r.value["paused"] as? NSNumber {try require(CFGetTypeID(value)==CFBooleanGetTypeID(),"Invalid pause state")}
            if r.value["payday"] != nil {_ = try dateValue(r.str("payday"))};return
        }
        try require(!r.str("title").trimmingCharacters(in:.whitespacesAndNewlines).isEmpty&&r.str("title").count<=300&&r.str("note").count<=2000,"Enter a description (up to 300 characters)")
        _ = try dateValue(r.str("date"));try integer(r,"amount")
        if r.str("section")=="Transaction" {
            try require(recordKinds.contains(r.str("kind"))&&r.str("currency")=="INR","Unsupported transaction")
            guard let reviewed=r.value["reviewed"] as? NSNumber,CFGetTypeID(reviewed)==CFBooleanGetTypeID() else {throw PilotError(message:"Review state missing")}
        }
        if ["Goal","Holding"].contains(r.str("section")){try integer(r,"saved")}
    }
    private func encoded(_ spaces:[PilotSpace],_ records:[PilotRecord])throws->Data{try JSONSerialization.data(withJSONObject:["format":"PaisaPilot","version":1,"spaces":spaces.map(\.json),"records":records.map(\.value)],options:[.sortedKeys,.withoutEscapingSlashes])}
    func snapshot()throws->Data{try encoded(spaces,records)}
    private func commit(_ nextSpaces:[PilotSpace],_ nextRecords:[PilotRecord],recover:Bool=false)throws {
        try require(!locked||recover,"Stored data needs recovery; restore a backup first")
        let bytes=try encoded(nextSpaces,nextRecords);_ = try Self.decode(bytes)
        try bytes.write(to:file,options:[.atomic,.completeFileProtection]);spaces=nextSpaces;records=nextRecords;locked=false
    }
    func restore(_ bytes:Data)throws {let decoded=try Self.decode(bytes);try commit(decoded.0,decoded.1,recover:true)}
    func rows(_ space:String,_ section:String)->[PilotRecord]{records.filter{$0.str("space")==space&&$0.str("section")==section}.sorted{$0.str("date")>$1.str("date")}}
    func put(_ record:PilotRecord)throws {var next=records;next.removeAll{$0.id==record.id};next.append(record);try commit(spaces,next)}
    func remove(_ id:String)throws {try commit(spaces,records.filter{$0.id != id})}
    func createSpace(_ name:String,_ type:String)throws->String{let id=UUID().uuidString;try commit(spaces+[PilotSpace(id:id,name:name,type:type)],records);return id}
    func settings(_ space:String)->PilotRecord{records.first{$0.id=="settings-"+space} ?? PilotRecord(value:["id":"settings-"+space,"space":space,"section":"Settings"])}
    func totals(_ space:String,_ month:String)throws->[String:Any]{try engine("totals",["rows":rows(space,"Transaction").filter{$0.str("date").hasPrefix(month)}.map(\.value)])}
    func settle(_ id:String,_ payment:Int64,_ date:String)throws {
        guard var current=records.first(where:{$0.id==id}) else {throw PilotError(message:"Commitment no longer exists")}
        try require(payment>0&&payment<=current.num("amount") && !current.flag("done"),"Payment exceeds current outstanding amount")
        var tx=PilotRecord.new(current.str("space"),"Transaction");tx.value["title"]=current.str("title");tx.value["amount"]=payment;tx.value["date"]=date;tx.value["owner"]=current.str("owner");tx.value["note"]="Linked to commitment \(id)"
        let type=current.str("kind");tx.value["kind"]=type=="Money lent" ? "Lent principal returned" : ["Loan taken","EMI principal"].contains(type) ? "Loan principal repayment" : type=="Card bill" ? "Card repayment" : "Expense"
        current.value["amount"]=current.num("amount")-payment;current.value["done"]=current.num("amount")==0
        var next=records.filter{$0.id != id};next.append(current);next.append(tx);try commit(spaces,next)
    }
}
