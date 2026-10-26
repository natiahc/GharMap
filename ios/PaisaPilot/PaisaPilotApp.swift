import SwiftUI
import UniformTypeIdentifiers

extension Color { static let pilot=Color(red:25/255,green:122/255,blue:103/255) }
@main struct PaisaPilotApp: App {
    @StateObject private var store=FinanceStore()
    var body: some Scene {WindowGroup{PilotRoot().environmentObject(store).tint(.pilot)}}
}
struct PilotRoot: View {
    @EnvironmentObject var store:FinanceStore
    @State private var space="personal"
    var body: some View {
        TabView {
            Dashboard(space:$space).tabItem{Label("Overview",systemImage:"house")}
            LedgerScreen(space:$space).tabItem{Label("Ledger",systemImage:"list.bullet.rectangle")}
            PlanScreen(space:$space).tabItem{Label("Plan",systemImage:"chart.line.uptrend.xyaxis")}
            LibraryScreen(space:$space).tabItem{Label("Goals & more",systemImage:"square.stack.3d.up")}
            DataScreen(space:$space).tabItem{Label("Data",systemImage:"lock.shield")}
        }.alert("PaisaPilot",isPresented:Binding(get:{store.error != nil},set:{if !$0 {store.error=nil}})){Button("OK"){store.error=nil}} message:{Text(store.error ?? "")}
    }
}
struct SpacePicker:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    var body:some View{Picker("Current space",selection:$space){ForEach(store.spaces){s in Text("\(s.name) · \(s.type)").tag(s.id)}}.onChange(of:store.spaces.map(\.id)){ids in if !ids.contains(space){space="personal"}}}
}
struct MoneyRow:View {let label:String;let value:Int64;var body:some View{LabeledContent(label){Text(rupees(value)).monospacedDigit()}}}
struct Dashboard:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    @State private var editor:PilotRecord?
    @State private var month=String(day().prefix(7))
    var body:some View{NavigationStack{Form{
        Section{SpacePicker(space:$space);Text("Your money. Your plan. Your breathing room.").font(.title2).foregroundStyle(.pilot)}
        Section("Recorded totals"){
            TextField("Month YYYY-MM",text:$month)
            if let t=try? store.totals(space,month){MoneyRow(label:"Reviewed income",value:(t["income"] as? NSNumber)?.int64Value ?? 0)
                MoneyRow(label:"Net spending",value:((t["expenses"] as? NSNumber)?.int64Value ?? 0)-((t["refunds"] as? NSNumber)?.int64Value ?? 0))
                LabeledContent("Awaiting review",value:String((t["pending"] as? NSNumber)?.intValue ?? 0))}
            Text("Recorded earnings/spending are not reconciled bank balances. Transfers, borrowing and card repayments are excluded.").font(.footnote).foregroundStyle(.secondary)
        }
        Section{Button("Add transaction"){editor = .new(space,"Transaction")};Text("Private local storage. No cloud sync is enabled on iPhone in this build.").font(.footnote)}
    }.navigationTitle("PaisaPilot").sheet(item:$editor){RecordEditor(record:$0)}}}
}
struct LedgerScreen:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    @State private var editor:PilotRecord?
    @State private var pendingOnly=false
    var body:some View{NavigationStack{List{
        Section{SpacePicker(space:$space);Toggle("Needs review only",isOn:$pendingOnly);Button("Add transaction"){editor = .new(space,"Transaction")}}
        ForEach(store.rows(space,"Transaction").filter{!pendingOnly || !$0.flag("reviewed")}){r in
            Button{editor=r}label:{VStack(alignment:.leading){Text(r.str("title")).font(.headline);Text("\(rupees(r.num("amount"))) · \(r.str("kind"))").foregroundStyle(.pilot)
                Text("\(r.str("date")) · \(r.flag("reviewed") ? "Reviewed" : "Needs review")").font(.caption).foregroundStyle(.secondary)}}
            .swipeActions{Button("Delete",role:.destructive){do{try store.remove(r.id)}catch{store.error=error.localizedDescription}}}
        }
    }.navigationTitle("Transactions").sheet(item:$editor){RecordEditor(record:$0)}}}
}
struct RecordEditor:View {
    @EnvironmentObject var store:FinanceStore
    @Environment(\.dismiss) var dismiss
    let record:PilotRecord
    @State private var fields:[String:String]
    @State private var kind:String
    @State private var reviewed:Bool
    init(record:PilotRecord){self.record=record;_kind=State(initialValue:record.str("kind"));_reviewed=State(initialValue:record.flag("reviewed"));var f=[String:String]();for key in ["title","date","category","account","owner","note"]{f[key]=record.str(key)};f["amount"]=decimal(record.num("amount"));f["saved"]=decimal(record.num("saved"));_fields=State(initialValue:f)}
    private var types:[String]{switch record.str("section"){case "Transaction":return recordKinds;case "Commitment":return ["Bill","Card bill","EMI principal","Loan taken","Money lent","Task"];case "Holding":return ["Equity","Debt","Deposit","Gold","Other"];default:return ["Retirement","Emergency","Purchase","Other"]}}
    private func binding(_ key:String)->Binding<String>{Binding(get:{fields[key] ?? ""},set:{fields[key]=$0})}
    var body:some View{NavigationStack{Form{
        Section{TextField("Description",text:binding("title"));Picker("Type",selection:$kind){ForEach(types,id:\.self){Text($0).tag($0)}}
            TextField(record.str("section")=="Holding" ? "Cost basis INR" : "Amount / target INR",text:binding("amount")).keyboardType(.decimalPad)
            if ["Goal","Holding"].contains(record.str("section")){TextField(record.str("section")=="Holding" ? "Current value INR" : "Saved INR",text:binding("saved")).keyboardType(.decimalPad)}
            TextField("Date YYYY-MM-DD",text:binding("date"));TextField("Owner / counterparty",text:binding("owner"));TextField("Note",text:binding("note"),axis:.vertical)
        }
        if record.str("section")=="Transaction"{Section{TextField("Category",text:binding("category"));TextField("Account label",text:binding("account"));Toggle("I checked amount, date and type",isOn:$reviewed);Text("Card payments, transfers and principal are not new expenses. Record interest/fees separately.").font(.footnote)}}
    }.navigationTitle(record.str("section")).toolbar{ToolbarItem(placement:.cancellationAction){Button("Cancel"){dismiss()}};ToolbarItem(placement:.confirmationAction){Button("Save"){
        do{var r=record;for(key,value) in fields where !["amount","saved"].contains(key){r.value[key]=value};r.value["amount"]=try paise(fields["amount"] ?? "");r.value["kind"]=kind
            if ["Goal","Holding"].contains(r.str("section")){r.value["saved"]=try paise(fields["saved"] ?? "")};if r.str("section")=="Transaction"{r.value["reviewed"]=reviewed;r.value["currency"]="INR"};try store.put(r);dismiss()
        }catch{store.error=error.localizedDescription}
    }}}.onAppear{if !types.contains(kind){kind=types[0]}}}}
}
struct LibraryScreen:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    @State private var section="Goal"
    @State private var editor:PilotRecord?
    @State private var payment:PilotRecord?
    var body:some View{NavigationStack{List{
        Section{SpacePicker(space:$space);Picker("Records",selection:$section){Text("Goals").tag("Goal");Text("Bills / loans").tag("Commitment");Text("Holdings").tag("Holding")};Button("Add \(section.lowercased())"){editor = .new(space,section)}}
        ForEach(store.rows(space,section)){r in Section{
            Button(r.str("title")){editor=r}.font(.headline)
            if section=="Goal"{MoneyRow(label:"Target",value:r.num("amount"));MoneyRow(label:"Saved",value:r.num("saved"));Text("Due \(r.str("date")); remaining \(rupees(max(0,r.num("amount")-r.num("saved")))) assuming no growth.").font(.footnote)}
            else if section=="Holding"{MoneyRow(label:"Cost",value:r.num("amount"));MoneyRow(label:"Current value",value:r.num("saved"));MoneyRow(label:"Unrealised change",value:r.num("saved")-r.num("amount"));Text("Manual valuation as of \(r.str("date")); no live prices.").font(.footnote)}
            else{MoneyRow(label:r.str("kind"),value:r.num("amount"));Text(r.flag("done") ? "Completed" : "Due \(r.str("date"))").font(.footnote)
                if !r.flag("done"){if r.str("kind")=="Task"{Button("Mark done"){do{var next=r;next.value["done"]=true;try store.put(next)}catch{store.error=error.localizedDescription}}}else{Button("Record payment / return"){payment=r}}}}
            Button("Delete",role:.destructive){do{try store.remove(r.id)}catch{store.error=error.localizedDescription}}
        }}
        Section{Text("One-off commitments. Add each EMI separately. Loan/card settlements update principal without double counting expenses; enter interest separately. Deleting a settlement transaction does not reverse the commitment automatically.").font(.footnote).foregroundStyle(.secondary)}
    }.navigationTitle("Goals & records").sheet(item:$editor){RecordEditor(record:$0)}.sheet(item:$payment){PaymentScreen(record:$0)}}}
}
struct PaymentScreen:View{
    @EnvironmentObject var store:FinanceStore
    @Environment(\.dismiss) var dismiss
    let record:PilotRecord
    @State private var amount=""
    @State private var date=day()
    var body:some View{NavigationStack{Form{Text(record.str("title"));TextField("Payment INR",text:$amount).keyboardType(.decimalPad);TextField("Date YYYY-MM-DD",text:$date)}.navigationTitle("Actual payment").toolbar{Button("Save"){do{try store.settle(record.id,paise(amount),date);dismiss()}catch{store.error=error.localizedDescription}}}.onAppear{amount=decimal(record.num("amount"))}}}
}
struct PlanScreen:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    @State private var editing=false
    var body:some View{NavigationStack{Form{
        Section{SpacePicker(space:$space);Button("Edit financial plan"){editing=true};Text("Manual assumptions. No orders are executed. Exclude emergency savings/near-term goals from long-term assets and avoid counting the same money twice.").font(.footnote)}
        let s=store.settings(space)
        if let b=try? engine("budget",s.value){Section("Monthly allocation"){
            MoneyRow(label:"Long-term contribution",value:(b["longterm"] as? NSNumber)?.int64Value ?? 0);MoneyRow(label:"Risk contribution",value:(b["risk"] as? NSNumber)?.int64Value ?? 0);MoneyRow(label:"Shortfall",value:(b["shortfall"] as? NSNumber)?.int64Value ?? 0);MoneyRow(label:"Emergency gap",value:(b["gap"] as? NSNumber)?.int64Value ?? 0)
            Text("Update already-funded capital and losses manually. Pause/threshold limits future planned funding, not broker activity.").font(.footnote)}
            Section("Income-loss scenarios"){
                if s.num("essentials")>0{Text(String(format:"No-income runway: %.1f months",Double(s.num("reserves"))/Double(s.num("essentials"))))
                    let burn=max(0,s.num("essentials")-s.num("retained"));Text(burn==0 ? "Retained income covers essentials" : String(format:"One-income runway: %.1f months",Double(s.num("reserves"))/Double(burn)))}else{Text("Enter essential outgoings to calculate runway")}
            }
            var input=s.value
            let _ = {input["monthly"]=b["longterm"];if input["years"]==nil{input["years"]=10};if input["returnbp"]==nil{input["returnbp"]=800};if input["inflationbp"]==nil{input["inflationbp"]=600}}()
            if let p=try? engine("projection",input){Section("Retirement growth scenario"){MoneyRow(label:"Future nominal assets",value:(p["nominal"] as? NSNumber)?.int64Value ?? 0);MoneyRow(label:"Today's purchasing power",value:(p["real"] as? NSNumber)?.int64Value ?? 0);Text("\(s.num("years",10)) years; return \(decimal(s.num("returnbp",800)))%, inflation \(decimal(s.num("inflationbp",600)))%, annual contribution increase \(decimal(s.num("stepupbp")))%. Smooth returns; excludes taxes, fees, withdrawals and market variability.").font(.footnote)}}
        }
    }.navigationTitle("Your plan").sheet(isPresented:$editing){PlanEditor(record:store.settings(space))}}}
}
struct PlanEditor:View {
    @EnvironmentObject var store:FinanceStore
    @Environment(\.dismiss) var dismiss
    let record:PilotRecord
    @State private var fields:[String:String]
    @State private var paused:Bool
    init(record:PilotRecord){self.record=record;var f=[String:String]();for key in moneyKeys{f[key]=decimal(record.num(key))};for(key,defaultValue) in [("riskbp",Int64(0)),("returnbp",800),("inflationbp",600),("stepupbp",0)]{f[key]=decimal(record.num(key,defaultValue))};f["months"]=String(record.num("months",6));f["years"]=String(record.num("years",10));_fields=State(initialValue:f);_paused=State(initialValue:record.flag("paused"))}
    private func binding(_ key:String)->Binding<String>{Binding(get:{fields[key] ?? ""},set:{fields[key]=$0})}
    var body:some View{NavigationStack{Form{
        Section("INR amounts; monthly unless stated"){ForEach(planFields,id:\.0){key,label in TextField(label,text:binding(key)).keyboardType(.decimalPad)}}
        Section("Assumptions"){TextField("Emergency target months 0–120",text:binding("months"));TextField("Projection years 1–60",text:binding("years"));TextField("Risk share % of surplus",text:binding("riskbp"));TextField("Annual return % (-99 to 50)",text:binding("returnbp"));TextField("Inflation % (0–25)",text:binding("inflationbp"));TextField("Annual contribution increase % (0–25)",text:binding("stepupbp"));Toggle("Pause new risk funding",isOn:$paused)}
    }.navigationTitle("Financial plan").toolbar{ToolbarItem(placement:.cancellationAction){Button("Cancel"){dismiss()}};ToolbarItem(placement:.confirmationAction){Button("Save"){
        do{var next=record;for key in moneyKeys where fields[key] != nil {next.value[key]=try paise(fields[key] ?? "")};for key in ["riskbp","returnbp","inflationbp","stepupbp"]{let text=fields[key] ?? "0",negative=text.hasPrefix("-");next.value[key]=(try paise(negative ? String(text.dropFirst()) : text))*(negative ? -1 : 1)}
            guard let months=Int64(fields["months"] ?? ""),let years=Int64(fields["years"] ?? "") else {throw PilotError(message:"Enter whole months and years")};next.value["months"]=months;next.value["years"]=years;next.value["paused"]=paused;try store.put(next);dismiss()
        }catch{store.error=error.localizedDescription}
    }}}}}
}
struct BinaryFile:FileDocument {
    static var readableContentTypes:[UTType]{[.data]}
    var data:Data
    init(_ data:Data=Data()){self.data=data}
    init(configuration:ReadConfiguration)throws{data=configuration.file.regularFileContents ?? Data()}
    func fileWrapper(configuration:WriteConfiguration)throws->FileWrapper{FileWrapper(regularFileWithContents:data)}
}
struct DataScreen:View {
    @EnvironmentObject var store:FinanceStore
    @Binding var space:String
    @State private var password=""
    @State private var confirmation=""
    @State private var consent=false
    @State private var busy=false
    @State private var exporting=false
    @State private var importing=false
    @State private var file=BinaryFile()
    @State private var newName=""
    @State private var newType="Household"
    @State private var notice:String?
    var body:some View{NavigationStack{Form{
        Section{SpacePicker(space:$space);Text("Local protected storage. Backups contain ALL spaces; keep them and their passphrase private. No Google credentials are included.").font(.footnote)}
        Section("Encrypted full backup / restore"){
            SecureField("Passphrase, 12–128 English characters",text:$password);SecureField("Confirm for new backup",text:$confirmation)
            Toggle("I approve all-space backup or replacement of ALL local data on restore",isOn:$consent)
            Button(busy ? "Working…" : "Create encrypted backup"){backup()}.disabled(busy)
            Button("Restore encrypted backup"){if consent {importing=true}else{store.error="Confirm replacement scope first"}}.disabled(busy)
            Text("Passphrase cannot be recovered. Restore validates the whole snapshot before replacing local data. Use a backup before changing devices or uninstalling.").font(.footnote)
        }
        Section("Separate household / business space"){
            TextField("Space name",text:$newName);Picker("Type",selection:$newType){Text("Household").tag("Household");Text("Business").tag("Business")}
            Button("Create local space"){do{space=try store.createSpace(newName,newType);newName=""}catch{store.error=error.localizedDescription}}
        }
        Section("Connections"){
            Text("iPhone finance screens and portable encrypted backups are available. Automatic Google Sheets/partner sync is not connected on iPhone. Android SMS-inbox access is not available on iOS. Use reviewed manual records and cross-device encrypted backup/restore.").font(.footnote)
        }
    }.navigationTitle("Your data").fileExporter(isPresented:$exporting,document:file,contentType:.data,defaultFilename:"PaisaPilot-\(day()).paisabackup"){result in if case .failure(let e)=result{store.error=e.localizedDescription}}
        .fileImporter(isPresented:$importing,allowedContentTypes:[.data]){result in
            do{let url=try result.get();let access=url.startAccessingSecurityScopedResource();defer{if access{url.stopAccessingSecurityScopedResource()}};let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0;try require(size<=10_000_000,"Backup exceeds 10 MB");let bytes=try Data(contentsOf:url);let pass=password;password="";confirmation="";busy=true
                Task{do{let plain=try await Task.detached{try PortableBackup.decrypt(bytes,password:pass)}.value;try store.restore(plain);space="personal";notice="All records restored. Connections require separate setup."}catch{store.error=error.localizedDescription};busy=false}
            }catch{store.error=error.localizedDescription}
        }.alert("PaisaPilot",isPresented:Binding(get:{notice != nil},set:{if !$0{notice=nil}})){Button("OK"){notice=nil}}message:{Text(notice ?? "")}
    }}
    private func backup(){do{try require(consent,"Approve all-space backup first");try require(password==confirmation,"Passphrases do not match");let snapshot=try store.snapshot(),pass=password;password="";confirmation="";busy=true
        Task{do{let encrypted=try await Task.detached{try PortableBackup.encrypt(snapshot,password:pass)}.value;file=BinaryFile(encrypted);exporting=true}catch{store.error=error.localizedDescription};busy=false}
    }catch{store.error=error.localizedDescription}}
}
