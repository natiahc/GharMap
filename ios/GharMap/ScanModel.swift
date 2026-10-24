import ARKit
import SceneKit
import AVFoundation
import Combine

final class ScanModel: NSObject, ObservableObject, ARSessionDelegate {
    let view = ARSCNView(frame: .zero)
    @Published var points: [Double] = []
    @Published var status = "Move slowly to find surfaces"
    @Published var recording = false
    @Published var recordingBusy = false
    @Published var automatic = false
    @Published var error: String?
    private var walls: [NSNumber] = []
    private var writer: AVAssetWriter?
    private var input: AVAssetWriterInput?
    private var adaptor: AVAssetWriterInputPixelBufferAdaptor?
    private var metadata: FileHandle?
    private var firstTimestamp = 0.0
    private var lastTimestamp = 0.0
    private(set) var capture: String?
    private let directory: URL
    init(directory: URL) { self.directory = directory; super.init() }
    func start() {
        guard ARWorldTrackingConfiguration.isSupported else { error = "AR tracking unavailable. Use measured-room entry."; return }
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                guard granted else { self.error = "Camera access is required to scan."; return }
                let config = ARWorldTrackingConfiguration(); config.planeDetection = [.horizontal, .vertical]
                self.view.session.delegate = self; self.view.session.delegateQueue = .main
                self.view.session.run(config)
            }
        }
    }
    func stop() { finishRecording(); view.session.pause() }
    func session(_ session: ARSession, didFailWithError error: Error) { self.error = error.localizedDescription }
    func sessionWasInterrupted(_ session: ARSession) { finishRecording(); status = "Session interrupted. Recheck all captured corners." }
    func sessionInterruptionEnded(_ session: ARSession) { status = "Move slowly to restore tracking; verify existing corners" }
    func session(_ session: ARSession, didUpdate frame: ARFrame) {
        guard case .normal = frame.camera.trackingState else { status = "Tracking limited · move slowly"; return }
        walls = []
        let camera = frame.camera.transform.columns.3
        for case let plane as ARPlaneAnchor in frame.anchors where plane.alignment == .vertical && plane.extent.x > 0.4 {
            let normal = plane.transform.columns.1
            let centre = plane.transform * SIMD4<Float>(plane.center.x, plane.center.y, plane.center.z, 1)
            let length = hypot(normal.x, normal.z); guard length > 0.9 else { continue }
            var nx = normal.x / length, ny = normal.z / length
            var d = nx * centre.x + ny * centre.z
            if nx * camera.x + ny * camera.z > d { nx = -nx; ny = -ny; d = -d }
            let distance = d - nx * camera.x - ny * camera.z
            if distance > 0.15 && distance < 8 { walls += [NSNumber(value: nx), NSNumber(value: ny), NSNumber(value: d)] }
        }
        status = "Tracking ready · \(points.count / 2) corners · \(walls.count / 3) wall planes"
        if recording { append(frame) }
    }
    func mark() {
        guard let frame = view.session.currentFrame, case .normal = frame.camera.trackingState else { error = "Wait for stable tracking"; return }
        let centre = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        guard let query = view.raycastQuery(from: centre, allowing: .existingPlaneGeometry, alignment: .horizontal),
              let result = view.session.raycast(query).first else { error = "No horizontal surface found. Aim at a visible floor corner."; return }
        points += [Double(result.worldTransform.columns.3.x), Double(result.worldTransform.columns.3.z)]; automatic = false
    }
    func undo() { if points.count >= 2 { points.removeLast(2) }; automatic = false }
    func autoOutline() {
        let result = GMGeometry.outline(walls).map { $0.doubleValue }
        guard result.count >= 6 else { error = "No closed room detected. Scan all walls or mark corners."; return }
        points = result; automatic = true
    }
    func beginRecording() {
        guard let frame = view.session.currentFrame, case .normal = frame.camera.trackingState else { error = "Wait for tracking"; return }
        guard !recording && !recordingBusy && capture == nil else { return }
        do {
            let name = "scan-\(UUID()).mp4", url = directory.appendingPathComponent(name)
            let buffer = frame.capturedImage
            let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
            let settings: [String: Any] = [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: CVPixelBufferGetWidth(buffer), AVVideoHeightKey: CVPixelBufferGetHeight(buffer)]
            let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings); input.expectsMediaDataInRealTime = true
            input.transform = CGAffineTransform(rotationAngle: .pi / 2)
            guard writer.canAdd(input) else { throw CocoaError(.fileWriteUnknown) }; writer.add(input)
            let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: nil)
            guard writer.startWriting() else { throw writer.error ?? CocoaError(.fileWriteUnknown) }; writer.startSession(atSourceTime: .zero)
            let meta = directory.appendingPathComponent(name + ".jsonl")
            try Data("{\"schemaVersion\":1,\"provider\":\"ARKit\",\"units\":\"metres\",\"coordinates\":\"right-handed-y-up\"}\n".utf8).write(to: meta)
            metadata = try FileHandle(forWritingTo: meta); try metadata?.seekToEnd()
            self.writer = writer; self.input = input; self.adaptor = adaptor; capture = name
            firstTimestamp = frame.timestamp; lastTimestamp = -1; recording = true; append(frame)
        } catch { self.error = error.localizedDescription }
    }
    private func append(_ frame: ARFrame) {
        guard frame.timestamp - lastTimestamp >= 0.1, let input, input.isReadyForMoreMediaData, let adaptor else { return }
        let t = CMTime(seconds: frame.timestamp - firstTimestamp, preferredTimescale: 1_000_000)
        guard adaptor.append(frame.capturedImage, withPresentationTime: t) else { error = writer?.error?.localizedDescription ?? "Recording failed"; finishRecording(); return }
        lastTimestamp = frame.timestamp
        let m = frame.camera.transform, k = frame.camera.intrinsics
        let matrix = [m.columns.0, m.columns.1, m.columns.2, m.columns.3].flatMap { [$0.x, $0.y, $0.z, $0.w] }
        let row: [String: Any] = ["timestampSeconds": frame.timestamp, "cameraToWorldColumnMajor": matrix,
            "intrinsics": [k.columns.0.x, k.columns.1.y, k.columns.2.x, k.columns.2.y],
            "imageSize": [CVPixelBufferGetWidth(frame.capturedImage), CVPixelBufferGetHeight(frame.capturedImage)]]
        do { var data = try JSONSerialization.data(withJSONObject: row); data.append(0x0a); try metadata?.write(contentsOf: data) }
        catch { self.error = error.localizedDescription; finishRecording() }
    }
    func finishRecording() {
        guard recording else { return }; recording = false; recordingBusy = true
        try? metadata?.close(); metadata = nil; input?.markAsFinished()
        let finishedWriter = writer
        writer?.finishWriting { DispatchQueue.main.async {
            if finishedWriter?.status == .failed { self.error = finishedWriter?.error?.localizedDescription ?? "Video finalization failed" }
            self.recordingBusy = false
        } }
    }
    func discard() {
        finishRecording()
        // Avoid deleting a file while AVAssetWriter is still finalizing it.
        // Project deletion removes all recordings, including abandoned captures.
    }
}
