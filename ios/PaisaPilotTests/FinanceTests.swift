import XCTest
@testable import PaisaPilot

@MainActor final class FinanceTests:XCTestCase{
    func testNativeAccountingAndBudget()throws{
        XCTAssertEqual(try paise("0.29"),29)
        let totals=try engine("totals",["rows":[["id":"expense","kind":"Expense","amount":12345,"reviewed":true],["id":"payment","kind":"Card repayment","amount":12345,"reviewed":true],["id":"loan","kind":"Borrowed principal","amount":50000,"reviewed":true]]])
        XCTAssertEqual((totals["expenses"] as? NSNumber)?.int64Value,12345);XCTAssertEqual((totals["income"] as? NSNumber)?.int64Value,0)
        let result=try engine("budget",["takehome":25000000,"spending":8000000,"reserveadd":2000000,"riskbp":667,"riskcap":5000000])
        XCTAssertEqual((result["risk"] as? NSNumber)?.int64Value,1000500)
    }
    func testSharedBackupFixtureAndTamperProtection()throws{
        let url=try XCTUnwrap(Bundle(for:Self.self).url(forResource:"backup-v1",withExtension:"b64"));let bytes=try XCTUnwrap(Data(base64Encoded:String(contentsOf:url).trimmingCharacters(in:.whitespacesAndNewlines)))
        let plain=try PortableBackup.decrypt(bytes,password:"fixture passphrase only"),decoded=try FinanceStore.decode(plain)
        XCTAssertEqual(decoded.1.first?.num("amount"),29)
        let encrypted=try PortableBackup.encrypt(plain,password:"fixture passphrase only")
        XCTAssertEqual(try PortableBackup.decrypt(encrypted,password:"fixture passphrase only"),plain)
        XCTAssertThrowsError(try PortableBackup.decrypt(bytes,password:"incorrect passphrase"))
        var damaged=bytes;damaged[damaged.count-1]^=1;XCTAssertThrowsError(try PortableBackup.decrypt(damaged,password:"fixture passphrase only"))
    }
    func testPersistenceAndAtomicRestore()throws{
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString);defer{try? FileManager.default.removeItem(at:folder)}
        let store=FinanceStore(directory:folder);var record=PilotRecord.new("personal","Transaction");record.value["title"]="Groceries";record.value["amount"]=Int64(29);try store.put(record)
        XCTAssertEqual(FinanceStore(directory:folder).records.first?.num("amount"),29)
        let original=try store.snapshot();var malformed=try XCTUnwrap(JSONSerialization.jsonObject(with:original) as? [String:Any]);var rows=try XCTUnwrap(malformed["records"] as? [[String:Any]]);rows[0]["amount"] = -1;malformed["records"]=rows
        XCTAssertThrowsError(try store.restore(JSONSerialization.data(withJSONObject:malformed)));XCTAssertEqual(store.records.first?.num("amount"),29)
        try store.restore(original);XCTAssertEqual(store.records.count,1)
    }
}
