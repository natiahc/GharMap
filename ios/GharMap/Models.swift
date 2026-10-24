import Foundation
import Combine

struct Room: Codable, Identifiable {
    var id = UUID()
    var name: String
    var points: [Double]
    var source: String
    var capture: String?
    var verified = false
    var area: Double { GMGeometry.area(points.map { NSNumber(value: $0) }) }
}
struct Floor: Codable, Identifiable { var id = UUID(); var name: String; var rooms: [Room] = [] }
struct Property: Codable, Identifiable {
    var id = UUID(); var schemaVersion = 1; var name: String
    var floors = [Floor(name: "Ground floor")]
}
final class Library: ObservableObject {
    @Published var properties: [Property] = []
    @Published var error: String?
    let root: URL
    init() {
        root = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("projects", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            var url = root; var values = URLResourceValues(); values.isExcludedFromBackup = true; try url.setResourceValues(values)
            let dirs = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)
            for dir in dirs {
                let file = dir.appendingPathComponent("project.json")
                let p = try JSONDecoder().decode(Property.self, from: Data(contentsOf: file))
                guard p.schemaVersion == 1 else { throw CocoaError(.fileReadCorruptFile) }
                properties.append(p)
            }
        } catch { self.error = error.localizedDescription }
    }
    func directory(_ id: UUID) -> URL { root.appendingPathComponent(id.uuidString, isDirectory: true) }
    func save(_ property: Property) {
        do {
            let dir = directory(property.id); try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            try encoder.encode(property).write(to: dir.appendingPathComponent("project.json"), options: .atomic)
        } catch { self.error = error.localizedDescription }
    }
    func create(_ name: String) {
        let p = Property(name: name); save(p); if error == nil { properties.append(p) }
    }
    func remove(_ offsets: IndexSet) {
        for i in offsets.sorted(by: >) {
            do { try FileManager.default.removeItem(at: directory(properties[i].id)); properties.remove(at: i) }
            catch { self.error = error.localizedDescription }
        }
    }
    func export(_ room: Room) -> URL? {
        do {
            let text = GMGeometry.svg(room.points.map { NSNumber(value: $0) }, label: room.name)
            guard !text.isEmpty else { return nil }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("GharMap-\(room.id).svg")
            try text.write(to: url, atomically: true, encoding: .utf8); return url
        } catch { self.error = error.localizedDescription; return nil }
    }
}
