// 生成したファイル。手で直さない（design/genre-art/generate.py で SVG から作る）
package app.cmsl.classical.core.artwork

/** ジャンルのイラスト（viewBox 0〜100）。キーはジャンル ID。描画は [IllustrationShape] を参照。 */
@Suppress("LargeClass", "MagicNumber", "ktlint")
internal object GenreIllustrations {
    val shapes: Map<String, List<IllustrationShape>> = mapOf(
        "symphony" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 24f, 30f, 1f, 70f, 30f, 1f, 66f, 56f, 1f, 28f, 56f, 3f)),
            IllustrationShape(Ink.Detail, 1.2f, floatArrayOf(0f, 30f, 36f, 1f, 64f, 36f, 0f, 29.5f, 39.5f, 1f, 63.5f, 39.5f, 0f, 29f, 43f, 1f, 63f, 43f, 0f, 28.5f, 46.5f, 1f, 62.5f, 46.5f, 0f, 28f, 50f, 1f, 62f, 50f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 45.5f, 56f, 1f, 48.5f, 56f, 1f, 48.5f, 78f, 1f, 45.5f, 78f, 3f)),
            IllustrationShape(Ink.Accent, 2.4f, floatArrayOf(0f, 47f, 77f, 1f, 34f, 88f, 0f, 47f, 77f, 1f, 60f, 88f, 0f, 47f, 77f, 1f, 47f, 89f)),
            IllustrationShape(Ink.Accent, 1.6f, floatArrayOf(0f, 60f, 24f, 1f, 88f, 10f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 89.56f, 9.18f, 2f, 90.22f, 10.46f, 89.15f, 12.31f, 87.18f, 13.32f, 2f, 85.21f, 14.32f, 83.09f, 14.1f, 82.44f, 12.82f, 2f, 81.78f, 11.54f, 82.85f, 9.69f, 84.82f, 8.68f, 2f, 86.79f, 7.68f, 88.91f, 7.9f, 89.56f, 9.18f, 3f)),
        ),
        "orchestral" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 58f, 46f, 2f, 70f, 45f, 79f, 40f, 86f, 32f, 1f, 86f, 64f, 2f, 79f, 56f, 70f, 51f, 58f, 50f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 89f, 48f, 2f, 89f, 56.84f, 87.66f, 64f, 86f, 64f, 2f, 84.34f, 64f, 83f, 56.84f, 83f, 48f, 2f, 83f, 39.16f, 84.34f, 32f, 86f, 32f, 2f, 87.66f, 32f, 89f, 39.16f, 89f, 48f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 18f, 46f, 1f, 60f, 46f, 1f, 60f, 50f, 1f, 18f, 50f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 10f, 44f, 1f, 18f, 46f, 1f, 18f, 50f, 1f, 10f, 52f, 3f)),
            IllustrationShape(Ink.Accent, 3.6f, floatArrayOf(0f, 30f, 50f, 1f, 30f, 60f, 2f, 30f, 62.67f, 31.33f, 64f, 34f, 64f, 1f, 58f, 64f, 2f, 60.67f, 64f, 62f, 62.67f, 62f, 60f, 1f, 62f, 52f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 36f, 36f, 1f, 40f, 36f, 1f, 40f, 60f, 1f, 36f, 60f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 43f, 36f, 1f, 47f, 36f, 1f, 47f, 60f, 1f, 43f, 60f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 50f, 36f, 1f, 54f, 36f, 1f, 54f, 60f, 1f, 50f, 60f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 35f, 32f, 1f, 41f, 32f, 1f, 41f, 34.5f, 1f, 35f, 34.5f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 42f, 32f, 1f, 48f, 32f, 1f, 48f, 34.5f, 1f, 42f, 34.5f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 49f, 32f, 1f, 55f, 32f, 1f, 55f, 34.5f, 1f, 49f, 34.5f, 3f)),
        ),
        "concerto" to listOf(
            IllustrationShape(Ink.Accent, 1.8f, floatArrayOf(0f, 20f, 30f, 1f, 76f, 86f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 17f, 26f, 1f, 22f, 26f, 1f, 22f, 32f, 1f, 17f, 32f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 58.45f, 34.11f, 2f, 65.51f, 37.86f, 68.52f, 42.86f, 65.7f, 48.16f, 2f, 63.36f, 52.57f, 58.89f, 52.46f, 57.01f, 55.99f, 2f, 55.13f, 59.52f, 60.37f, 64.58f, 56.62f, 71.64f, 2f, 52.39f, 79.59f, 43.86f, 80.71f, 35.92f, 76.49f, 2f, 27.97f, 72.26f, 24.14f, 64.56f, 28.36f, 56.62f, 2f, 32.12f, 49.55f, 39.24f, 51.07f, 41.11f, 47.54f, 2f, 42.99f, 44.01f, 40.4f, 40.37f, 42.75f, 35.95f, 2f, 45.56f, 30.65f, 51.39f, 30.35f, 58.45f, 34.11f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 64.69f, 17.04f, 1f, 69.11f, 19.39f, 1f, 59.72f, 37.05f, 1f, 55.3f, 34.7f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 70.55f, 19.02f, 2f, 69.62f, 20.78f, 67.44f, 21.44f, 65.68f, 20.51f, 2f, 63.92f, 19.58f, 63.26f, 17.4f, 64.19f, 15.64f, 2f, 65.13f, 13.89f, 67.31f, 13.22f, 69.06f, 14.15f, 2f, 70.82f, 15.09f, 71.48f, 17.27f, 70.55f, 19.02f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 60.26f, 26.01f, 1f, 64.15f, 28.08f, 1f, 49.13f, 56.33f, 1f, 45.24f, 54.26f, 3f)),
            IllustrationShape(Ink.Detail, 1.6f, floatArrayOf(0f, 41.44f, 52.25f, 2f, 38.68f, 55.31f, 37.11f, 58.62f, 36.72f, 62.19f)),
            IllustrationShape(Ink.Detail, 1.6f, floatArrayOf(0f, 52.92f, 58.35f, 2f, 51.93f, 62.35f, 50.06f, 65.51f, 47.32f, 67.83f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 40.42f, 57.37f, 1f, 49.25f, 62.06f, 1f, 48.5f, 63.47f, 1f, 39.67f, 58.78f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 38.9f, 64.48f, 1f, 44.2f, 67.3f, 1f, 39.09f, 74.78f, 1f, 35.56f, 72.9f, 3f)),
        ),
        "piano" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 24f, 47f, 1f, 86f, 47f, 2f, 70f, 42.33f, 51.33f, 33.33f, 30f, 20f, 3f)),
            IllustrationShape(Ink.Accent, 2f, floatArrayOf(0f, 50f, 47f, 1f, 44f, 31f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 16f, 48f, 1f, 86f, 48f, 1f, 86f, 59f, 1f, 16f, 59f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 16f, 48f, 1f, 28f, 48f, 1f, 28f, 51f, 1f, 16f, 51f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 21f, 59f, 1f, 25f, 59f, 1f, 25f, 79f, 1f, 21f, 79f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 77f, 59f, 1f, 81f, 59f, 1f, 81f, 79f, 1f, 77f, 79f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 49f, 59f, 1f, 52f, 59f, 1f, 52f, 73f, 1f, 49f, 73f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 45f, 72f, 1f, 56f, 72f, 1f, 56f, 74.5f, 1f, 45f, 74.5f, 3f)),
        ),
        "chamber" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 45.93f, 40.86f, 2f, 54.15f, 37.2f, 59.03f, 38.32f, 61.88f, 44.71f, 2f, 63.91f, 49.28f, 61.98f, 52.33f, 63.2f, 55.07f, 2f, 64.42f, 57.81f, 72.95f, 57.3f, 76.61f, 65.52f, 2f, 80.27f, 73.74f, 75f, 81.56f, 65.86f, 85.63f, 2f, 56.73f, 89.7f, 47.39f, 88.38f, 43.72f, 80.16f, 2f, 40.06f, 71.94f, 46.15f, 65.94f, 44.93f, 63.2f, 2f, 43.71f, 60.46f, 40.16f, 59.86f, 38.12f, 55.29f, 2f, 35.28f, 48.89f, 37.71f, 44.53f, 45.93f, 40.86f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 32.62f, 18.33f, 1f, 38.1f, 15.89f, 1f, 49.49f, 41.47f, 1f, 44.01f, 43.91f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 27.64f, 9.6f, 1f, 34.94f, 6.35f, 1f, 40.33f, 15.99f, 1f, 31.2f, 20.06f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 61.18f, 60.35f, 2f, 62.52f, 63.38f, 61.16f, 66.92f, 58.13f, 68.27f, 2f, 55.11f, 69.62f, 51.56f, 68.26f, 50.21f, 65.23f, 2f, 48.87f, 62.2f, 50.23f, 58.66f, 53.25f, 57.31f, 2f, 56.28f, 55.96f, 59.83f, 57.32f, 61.18f, 60.35f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 55.5f, 77.11f, 1f, 66.46f, 72.23f, 1f, 67.44f, 74.42f, 1f, 56.48f, 79.3f, 3f)),
            IllustrationShape(Ink.Detail, 0f, floatArrayOf(0f, 34.08f, 17.68f, 1f, 36.64f, 16.54f, 1f, 54.53f, 56.74f, 1f, 51.97f, 57.88f, 3f)),
        ),
        "vocal" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 12f, 14f, 1f, 88f, 14f, 1f, 88f, 22f, 2f, 84.67f, 26f, 81.33f, 26f, 78f, 22f, 2f, 74.67f, 26f, 71.33f, 26f, 68f, 22f, 2f, 64.67f, 26f, 61.33f, 26f, 58f, 22f, 2f, 54.67f, 26f, 51.33f, 26f, 48f, 22f, 2f, 44.67f, 26f, 41.33f, 26f, 38f, 22f, 2f, 34.67f, 26f, 31.33f, 26f, 28f, 22f, 2f, 24.67f, 26f, 21.33f, 26f, 18f, 22f, 2f, 16f, 24.67f, 14f, 24.67f, 12f, 22f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 12f, 22f, 1f, 28f, 22f, 2f, 27f, 40f, 24f, 58f, 32f, 70f, 2f, 26f, 74f, 20f, 80f, 12f, 86f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 88f, 22f, 1f, 72f, 22f, 2f, 73f, 40f, 76f, 58f, 68f, 70f, 2f, 74f, 74f, 80f, 80f, 88f, 86f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 12f, 86f, 1f, 88f, 86f, 1f, 88f, 89f, 1f, 12f, 89f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 55f, 50f, 2f, 55f, 52.76f, 52.76f, 55f, 50f, 55f, 2f, 47.24f, 55f, 45f, 52.76f, 45f, 50f, 2f, 45f, 47.24f, 47.24f, 45f, 50f, 45f, 2f, 52.76f, 45f, 55f, 47.24f, 55f, 50f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 50f, 56f, 2f, 45f, 57f, 43f, 64f, 42f, 70f, 2f, 40f, 76f, 38f, 81f, 37f, 86f, 1f, 63f, 86f, 2f, 62f, 81f, 60f, 76f, 58f, 70f, 2f, 57f, 64f, 55f, 57f, 50f, 56f, 3f)),
            IllustrationShape(Ink.Accent, 2.4f, floatArrayOf(0f, 46f, 61f, 1f, 38f, 48f)),
            IllustrationShape(Ink.Accent, 2.4f, floatArrayOf(0f, 54f, 61f, 1f, 62f, 48f)),
        ),
        "other" to listOf(
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 43.46f, 68.92f, 2f, 44.69f, 72.3f, 41.89f, 76.41f, 37.22f, 78.11f, 2f, 32.55f, 79.81f, 27.77f, 78.45f, 26.54f, 75.08f, 2f, 25.31f, 71.7f, 28.11f, 67.59f, 32.78f, 65.89f, 2f, 37.45f, 64.19f, 42.23f, 65.55f, 43.46f, 68.92f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 77.46f, 60.92f, 2f, 78.69f, 64.3f, 75.89f, 68.41f, 71.22f, 70.11f, 2f, 66.55f, 71.81f, 61.77f, 70.45f, 60.54f, 67.08f, 2f, 59.31f, 63.7f, 62.11f, 59.59f, 66.78f, 57.89f, 2f, 71.45f, 56.19f, 76.23f, 57.55f, 77.46f, 60.92f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 40.5f, 28f, 1f, 43.9f, 28f, 1f, 43.9f, 72f, 1f, 40.5f, 72f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 74.5f, 20f, 1f, 77.9f, 20f, 1f, 77.9f, 64f, 1f, 74.5f, 64f, 3f)),
            IllustrationShape(Ink.Accent, 0f, floatArrayOf(0f, 40.5f, 26f, 1f, 77.9f, 17f, 1f, 77.9f, 26f, 1f, 40.5f, 35f, 3f)),
        ),
    )
}
