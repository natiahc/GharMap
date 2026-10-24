import SwiftUI
import ARKit

extension Color {
    static let gharInk = Color(red: 21/255, green: 58/255, blue: 54/255)
    static let gharGreen = Color(red: 25/255, green: 122/255, blue: 103/255)
    static let gharBackground = Color(red: 245/255, green: 247/255, blue: 243/255)
}
@main struct GharMapApp: App {
    @StateObject private var library = Library()
    @State private var newProperty = false
    @State private var name = ""
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                List {
                    Section {
                        Text("Every space,\na clearer picture.").font(.system(size: 34, design: .serif)).foregroundStyle(Color.gharInk)
                        Text("Walk. Scan. Plan.\nFree basic plans. Projects stay on this device.").foregroundStyle(.secondary)
                        Button("+ New property") { name = ""; newProperty = true }.buttonStyle(.borderedProminent)
                    }.listRowBackground(Color.gharBackground)
                    Section("Your properties") {
                        ForEach($library.properties) { $property in
                            NavigationLink { PropertyScreen(property: $property, library: library) } label: {
                                VStack(alignment: .leading) { Text(property.name).font(.headline); Text("\(property.floors.count) floors · saved locally").font(.caption).foregroundStyle(.secondary) }
                            }
                        }.onDelete(perform: library.remove)
                    }
                    Section {
                        Text("Development build. Geometry is unverified. Automatic whole-home reconstruction is not implemented.").font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .scrollContentBackground(.hidden).background(Color.gharBackground).navigationTitle("GharMap")
                .alert("New property", isPresented: $newProperty) {
                    TextField("Property name", text: $name)
                    Button("Create") { let value = name.trimmingCharacters(in: .whitespacesAndNewlines); if !value.isEmpty { library.create(String(value.prefix(100))) } }
                    Button("Cancel", role: .cancel) {}
                }
                .alert("Could not complete action", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
                    Button("OK") { library.error = nil }
                } message: { Text(library.error ?? "") }
            }.tint(.gharGreen)
        }
    }
}
struct PropertyScreen: View {
    @Binding var property: Property
    @ObservedObject var library: Library
    @State private var floor = 0
    @State private var scanning = false
    @State private var measured = false
    @State private var name = "Room"
    @State private var width = ""
    @State private var length = ""
    @State private var share: URL?
    var body: some View {
        List {
            Section {
                Picker("Floor", selection: $floor) { ForEach(property.floors.indices, id: \.self) { i in Text(property.floors[i].name).tag(i) } }
                Button("+ Add floor") { property.floors.append(Floor(name: "Floor \(property.floors.count)")); floor = property.floors.count - 1; library.save(property) }
                Button("Scan a room") { scanning = true }.buttonStyle(.borderedProminent)
                Button("Enter measured rectangle") { measured = true }
                Text("No paid floor limit. Rooms captured separately are not automatically aligned.").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(property.floors[floor].rooms) { room in
                Section(room.name) {
                    PlanDiagram(points: room.points).frame(height: 280)
                    Text(String(format: "%.2f m² · %@", room.area, room.source)).font(.subheadline)
                    Text("Unverified. Confirm dimensions on site.").font(.caption).foregroundStyle(.secondary)
                    Button("Prepare SVG export") { share = library.export(room) }
                }
            }.onDelete { offsets in property.floors[floor].rooms.remove(atOffsets: offsets); library.save(property) }
            if let share { Section { ShareLink("Share room SVG", item: share) } }
        }
        .navigationTitle(property.name).scrollContentBackground(.hidden).background(Color.gharBackground)
        .onDisappear { library.save(property) }
        .fullScreenCover(isPresented: $scanning) {
            ScanScreen(directory: library.directory(property.id)) { room in property.floors[floor].rooms.append(room); library.save(property) }
        }
        .alert("Measured rectangle", isPresented: $measured) {
            TextField("Room name", text: $name); TextField("Width (metres)", text: $width).keyboardType(.decimalPad)
            TextField("Length (metres)", text: $length).keyboardType(.decimalPad)
            Button("Add") {
                guard let w = Double(width), let l = Double(length), w > 0, l > 0, w.isFinite, l.isFinite else { library.error = "Enter positive dimensions in metres"; return }
                let points: [Double] = [0,0,w,0,w,l,0,l]
                let problem = GMGeometry.validate(points.map { NSNumber(value: $0) })
                guard problem.isEmpty else { library.error = problem; return }
                let label = name.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !label.isEmpty else { library.error = "Enter a room name"; return }
                property.floors[floor].rooms.append(Room(name: String(label.prefix(100)), points: points, source: "user-measured")); library.save(property)
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
struct PlanDiagram: View {
    let points: [Double]
    var body: some View {
        Canvas { context, size in
            guard points.count >= 6 && points.count % 2 == 0 else { return }
            let xs = stride(from: 0, to: points.count, by: 2).map { points[$0] }
            let ys = stride(from: 1, to: points.count, by: 2).map { points[$0] }
            guard let minX = xs.min(), let maxX = xs.max(), let minY = ys.min(), let maxY = ys.max() else { return }
            let scale = min((size.width - 70) / max(0.01, maxX-minX), (size.height - 70) / max(0.01, maxY-minY))
            let vertices = zip(xs, ys).map { CGPoint(x: 35 + ($0.0-minX)*scale, y: 35 + ($0.1-minY)*scale) }
            var path = Path(); path.addLines(vertices); path.closeSubpath()
            context.fill(path, with: .color(.gharGreen.opacity(0.12))); context.stroke(path, with: .color(.gharInk), lineWidth: 3)
            for i in vertices.indices {
                let j = (i+1)%vertices.count
                let label = String(format: "%.2f m", hypot(xs[i]-xs[j],ys[i]-ys[j]))
                context.draw(Text(label).font(.caption2).foregroundColor(.gharInk), at: CGPoint(x: (vertices[i].x+vertices[j].x)/2, y: (vertices[i].y+vertices[j].y)/2-10))
            }
        }
    }
}
struct CameraSurface: UIViewRepresentable {
    let model: ScanModel
    func makeUIView(context: Context) -> ARSCNView { model.view }
    func updateUIView(_ uiView: ARSCNView, context: Context) {}
}
struct ScanScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model: ScanModel
    @State private var review = false
    @State private var name = "Room"
    private let save: (Room) -> Void
    init(directory: URL, save: @escaping (Room) -> Void) { _model = StateObject(wrappedValue: ScanModel(directory: directory)); self.save = save }
    var body: some View {
        ZStack {
            CameraSurface(model: model).ignoresSafeArea()
            Text("＋").font(.largeTitle).foregroundColor(.white)
            VStack {
                VStack(alignment: .leading, spacing: 8) {
                    Text("ROOM CAPTURE").font(.caption).bold(); Text(model.status)
                    Text("Aim at visible floor corners. Scan one room at a time; walk safely.").font(.caption)
                    if model.recording { Text("● Recording locally").foregroundStyle(.red) }
                }.padding().frame(maxWidth: .infinity, alignment: .leading).background(Color.gharInk.opacity(0.85)).foregroundStyle(.white)
                Spacer()
                VStack(spacing: 10) {
                    Button("Record walkthrough") { model.beginRecording() }.disabled(model.capture != nil || model.recordingBusy)
                    Button("Mark corner at centre") { model.mark() }
                    HStack { Button("Undo") { model.undo() }; Spacer(); Button("Try auto outline") { model.autoOutline() } }
                    Button("Review room →") {
                        let problem = GMGeometry.validate(model.points.map { NSNumber(value: $0) })
                        if problem.isEmpty { model.finishRecording(); review = true } else { model.error = problem }
                    }
                    Button("Cancel scan", role: .cancel) { model.discard(); dismiss() }
                }.buttonStyle(.borderedProminent).padding().background(Color.gharInk.opacity(0.9))
            }
        }.onAppear { model.start() }.onDisappear { model.stop() }
        .onChange(of: scenePhase) { phase in if phase != .active { model.finishRecording() } }
        .sheet(isPresented: $review) {
            NavigationStack {
                Form {
                    TextField("Room name", text: $name)
                    PlanDiagram(points: model.points).frame(height: 300)
                    Text(model.automatic ? "Experimental convex-room estimate. Verify every wall." : "Assisted floor-corner outline. Verify every dimension.")
                    Text("No doors/windows are inferred. This is not a construction drawing.").font(.caption)
                    Button(model.recordingBusy ? "Finalizing video…" : "Save unverified room") {
                        let label = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !label.isEmpty else { return }
                        save(Room(name: String(label.prefix(100)), points: model.points, source: model.automatic ? "experimental-wall-planes" : "assisted-AR-corners", capture: model.capture))
                        review = false; dismiss()
                    }.disabled(model.recordingBusy)
                }.navigationTitle("Review room")
                .toolbar { Button("Keep scanning") { review = false } }
            }
        }
        .alert("Capture notice", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
            Button("OK") { model.error = nil }
        } message: { Text(model.error ?? "") }
    }
}
