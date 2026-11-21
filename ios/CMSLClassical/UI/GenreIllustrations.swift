// 生成したファイル。手で直さない（design/genre-art/generate.py で SVG から作る）
// swiftlint:disable all

/// ジャンルのイラスト（viewBox 0〜100）。キーはジャンル ID。描画は `IllustrationShape` を参照。
enum GenreIllustrations {
    static let shapes: [String: [IllustrationShape]] = [
        "symphony": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 24, 30, 1, 70, 30, 1, 66, 56, 1, 28, 56, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 1.2, ops: [0, 30, 36, 1, 64, 36, 0, 29.5, 39.5, 1, 63.5, 39.5, 0, 29, 43, 1, 63, 43, 0, 28.5, 46.5, 1, 62.5, 46.5, 0, 28, 50, 1, 62, 50]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 45.5, 56, 1, 48.5, 56, 1, 48.5, 78, 1, 45.5, 78, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 2.4, ops: [0, 47, 77, 1, 34, 88, 0, 47, 77, 1, 60, 88, 0, 47, 77, 1, 47, 89]),
            IllustrationShape(ink: .accent, strokeWidth: 1.6, ops: [0, 60, 24, 1, 88, 10]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 89.56, 9.18, 2, 90.22, 10.46, 89.15, 12.31, 87.18, 13.32, 2, 85.21, 14.32, 83.09, 14.1, 82.44, 12.82, 2, 81.78, 11.54, 82.85, 9.69, 84.82, 8.68, 2, 86.79, 7.68, 88.91, 7.9, 89.56, 9.18, 3]),
        ],
        "orchestral": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 58, 46, 2, 70, 45, 79, 40, 86, 32, 1, 86, 64, 2, 79, 56, 70, 51, 58, 50, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 89, 48, 2, 89, 56.84, 87.66, 64, 86, 64, 2, 84.34, 64, 83, 56.84, 83, 48, 2, 83, 39.16, 84.34, 32, 86, 32, 2, 87.66, 32, 89, 39.16, 89, 48, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 18, 46, 1, 60, 46, 1, 60, 50, 1, 18, 50, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 10, 44, 1, 18, 46, 1, 18, 50, 1, 10, 52, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 3.6, ops: [0, 30, 50, 1, 30, 60, 2, 30, 62.67, 31.33, 64, 34, 64, 1, 58, 64, 2, 60.67, 64, 62, 62.67, 62, 60, 1, 62, 52]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 36, 36, 1, 40, 36, 1, 40, 60, 1, 36, 60, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 43, 36, 1, 47, 36, 1, 47, 60, 1, 43, 60, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 50, 36, 1, 54, 36, 1, 54, 60, 1, 50, 60, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 35, 32, 1, 41, 32, 1, 41, 34.5, 1, 35, 34.5, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 42, 32, 1, 48, 32, 1, 48, 34.5, 1, 42, 34.5, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 49, 32, 1, 55, 32, 1, 55, 34.5, 1, 49, 34.5, 3]),
        ],
        "concerto": [
            IllustrationShape(ink: .accent, strokeWidth: 1.8, ops: [0, 20, 30, 1, 76, 86]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 17, 26, 1, 22, 26, 1, 22, 32, 1, 17, 32, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 58.45, 34.11, 2, 65.51, 37.86, 68.52, 42.86, 65.7, 48.16, 2, 63.36, 52.57, 58.89, 52.46, 57.01, 55.99, 2, 55.13, 59.52, 60.37, 64.58, 56.62, 71.64, 2, 52.39, 79.59, 43.86, 80.71, 35.92, 76.49, 2, 27.97, 72.26, 24.14, 64.56, 28.36, 56.62, 2, 32.12, 49.55, 39.24, 51.07, 41.11, 47.54, 2, 42.99, 44.01, 40.4, 40.37, 42.75, 35.95, 2, 45.56, 30.65, 51.39, 30.35, 58.45, 34.11, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 64.69, 17.04, 1, 69.11, 19.39, 1, 59.72, 37.05, 1, 55.3, 34.7, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 70.55, 19.02, 2, 69.62, 20.78, 67.44, 21.44, 65.68, 20.51, 2, 63.92, 19.58, 63.26, 17.4, 64.19, 15.64, 2, 65.13, 13.89, 67.31, 13.22, 69.06, 14.15, 2, 70.82, 15.09, 71.48, 17.27, 70.55, 19.02, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 60.26, 26.01, 1, 64.15, 28.08, 1, 49.13, 56.33, 1, 45.24, 54.26, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 1.6, ops: [0, 41.44, 52.25, 2, 38.68, 55.31, 37.11, 58.62, 36.72, 62.19]),
            IllustrationShape(ink: .detail, strokeWidth: 1.6, ops: [0, 52.92, 58.35, 2, 51.93, 62.35, 50.06, 65.51, 47.32, 67.83]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 40.42, 57.37, 1, 49.25, 62.06, 1, 48.5, 63.47, 1, 39.67, 58.78, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 38.9, 64.48, 1, 44.2, 67.3, 1, 39.09, 74.78, 1, 35.56, 72.9, 3]),
        ],
        "piano": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 24, 47, 1, 86, 47, 2, 70, 42.33, 51.33, 33.33, 30, 20, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 2, ops: [0, 50, 47, 1, 44, 31]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 16, 48, 1, 86, 48, 1, 86, 59, 1, 16, 59, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 16, 48, 1, 28, 48, 1, 28, 51, 1, 16, 51, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 21, 59, 1, 25, 59, 1, 25, 79, 1, 21, 79, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 77, 59, 1, 81, 59, 1, 81, 79, 1, 77, 79, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 49, 59, 1, 52, 59, 1, 52, 73, 1, 49, 73, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 45, 72, 1, 56, 72, 1, 56, 74.5, 1, 45, 74.5, 3]),
        ],
        "chamber": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 45.93, 40.86, 2, 54.15, 37.2, 59.03, 38.32, 61.88, 44.71, 2, 63.91, 49.28, 61.98, 52.33, 63.2, 55.07, 2, 64.42, 57.81, 72.95, 57.3, 76.61, 65.52, 2, 80.27, 73.74, 75, 81.56, 65.86, 85.63, 2, 56.73, 89.7, 47.39, 88.38, 43.72, 80.16, 2, 40.06, 71.94, 46.15, 65.94, 44.93, 63.2, 2, 43.71, 60.46, 40.16, 59.86, 38.12, 55.29, 2, 35.28, 48.89, 37.71, 44.53, 45.93, 40.86, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 32.62, 18.33, 1, 38.1, 15.89, 1, 49.49, 41.47, 1, 44.01, 43.91, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 27.64, 9.6, 1, 34.94, 6.35, 1, 40.33, 15.99, 1, 31.2, 20.06, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 61.18, 60.35, 2, 62.52, 63.38, 61.16, 66.92, 58.13, 68.27, 2, 55.11, 69.62, 51.56, 68.26, 50.21, 65.23, 2, 48.87, 62.2, 50.23, 58.66, 53.25, 57.31, 2, 56.28, 55.96, 59.83, 57.32, 61.18, 60.35, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 55.5, 77.11, 1, 66.46, 72.23, 1, 67.44, 74.42, 1, 56.48, 79.3, 3]),
            IllustrationShape(ink: .detail, strokeWidth: 0, ops: [0, 34.08, 17.68, 1, 36.64, 16.54, 1, 54.53, 56.74, 1, 51.97, 57.88, 3]),
        ],
        "vocal": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 12, 14, 1, 88, 14, 1, 88, 22, 2, 84.67, 26, 81.33, 26, 78, 22, 2, 74.67, 26, 71.33, 26, 68, 22, 2, 64.67, 26, 61.33, 26, 58, 22, 2, 54.67, 26, 51.33, 26, 48, 22, 2, 44.67, 26, 41.33, 26, 38, 22, 2, 34.67, 26, 31.33, 26, 28, 22, 2, 24.67, 26, 21.33, 26, 18, 22, 2, 16, 24.67, 14, 24.67, 12, 22, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 12, 22, 1, 28, 22, 2, 27, 40, 24, 58, 32, 70, 2, 26, 74, 20, 80, 12, 86, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 88, 22, 1, 72, 22, 2, 73, 40, 76, 58, 68, 70, 2, 74, 74, 80, 80, 88, 86, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 12, 86, 1, 88, 86, 1, 88, 89, 1, 12, 89, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 55, 50, 2, 55, 52.76, 52.76, 55, 50, 55, 2, 47.24, 55, 45, 52.76, 45, 50, 2, 45, 47.24, 47.24, 45, 50, 45, 2, 52.76, 45, 55, 47.24, 55, 50, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 50, 56, 2, 45, 57, 43, 64, 42, 70, 2, 40, 76, 38, 81, 37, 86, 1, 63, 86, 2, 62, 81, 60, 76, 58, 70, 2, 57, 64, 55, 57, 50, 56, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 2.4, ops: [0, 46, 61, 1, 38, 48]),
            IllustrationShape(ink: .accent, strokeWidth: 2.4, ops: [0, 54, 61, 1, 62, 48]),
        ],
        "other": [
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 43.46, 68.92, 2, 44.69, 72.3, 41.89, 76.41, 37.22, 78.11, 2, 32.55, 79.81, 27.77, 78.45, 26.54, 75.08, 2, 25.31, 71.7, 28.11, 67.59, 32.78, 65.89, 2, 37.45, 64.19, 42.23, 65.55, 43.46, 68.92, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 77.46, 60.92, 2, 78.69, 64.3, 75.89, 68.41, 71.22, 70.11, 2, 66.55, 71.81, 61.77, 70.45, 60.54, 67.08, 2, 59.31, 63.7, 62.11, 59.59, 66.78, 57.89, 2, 71.45, 56.19, 76.23, 57.55, 77.46, 60.92, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 40.5, 28, 1, 43.9, 28, 1, 43.9, 72, 1, 40.5, 72, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 74.5, 20, 1, 77.9, 20, 1, 77.9, 64, 1, 74.5, 64, 3]),
            IllustrationShape(ink: .accent, strokeWidth: 0, ops: [0, 40.5, 26, 1, 77.9, 17, 1, 77.9, 26, 1, 40.5, 35, 3]),
        ],
    ]
}
