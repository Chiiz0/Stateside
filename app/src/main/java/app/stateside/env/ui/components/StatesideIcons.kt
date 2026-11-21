package app.stateside.env.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 零额外体积开销的定制矢量图标集（24x24 标准视口），为四大模块与设置中心提供高辨识度语义图标。
 */
object StatesideIcons {

    val Dashboard: ImageVector by lazy {
        ImageVector.Builder(
            name = "Dashboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 左上大模块
            path(
                fill = SolidColor(Color.Black),
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(11f)
                verticalLineTo(12.5f)
                horizontalLineTo(4f)
                close()
            }
            // 右上模块
            path(
                fill = SolidColor(Color.Black),
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(9.5f)
                horizontalLineTo(13f)
                close()
            }
            // 左下模块
            path(
                fill = SolidColor(Color.Black),
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 14.5f)
                horizontalLineTo(11f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
            }
            // 右下大模块
            path(
                fill = SolidColor(Color.Black),
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 11.5f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(13f)
                close()
            }
        }.build()
    }

    val SimCard: ImageVector by lazy {
        ImageVector.Builder(
            name = "SimCard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 3f)
                horizontalLineTo(14.2f)
                lineTo(19f, 7.8f)
                verticalLineTo(19f)
                quadTo(19f, 21f, 17f, 21f)
                horizontalLineTo(7f)
                quadTo(5f, 21f, 5f, 19f)
                verticalLineTo(5f)
                quadTo(5f, 3f, 6f, 3f)
                close()
            }
            // SIM 芯片触点框
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8.5f, 11.5f)
                horizontalLineTo(15.5f)
                verticalLineTo(17.5f)
                horizontalLineTo(8.5f)
                close()
                moveTo(12f, 11.5f)
                verticalLineTo(17.5f)
                moveTo(8.5f, 14.5f)
                horizontalLineTo(15.5f)
            }
        }.build()
    }

    val GlobeLanguage: ImageVector by lazy {
        ImageVector.Builder(
            name = "GlobeLanguage",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 21f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
                close()
                moveTo(3.6f, 9f)
                horizontalLineTo(20.4f)
                moveTo(3.6f, 15f)
                horizontalLineTo(20.4f)
                moveTo(12f, 3f)
                curveTo(15.5f, 6.5f, 15.5f, 17.5f, 12f, 21f)
                moveTo(12f, 3f)
                curveTo(8.5f, 6.5f, 8.5f, 17.5f, 12f, 21f)
            }
        }.build()
    }

    val SlidersTune: ImageVector by lazy {
        ImageVector.Builder(
            name = "SlidersTune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 6f)
                horizontalLineTo(10f)
                moveTo(14f, 6f)
                horizontalLineTo(20f)
                moveTo(12f, 4f)
                verticalLineTo(8f)

                moveTo(4f, 12f)
                horizontalLineTo(14f)
                moveTo(18f, 12f)
                horizontalLineTo(20f)
                moveTo(16f, 10f)
                verticalLineTo(14f)

                moveTo(4f, 18f)
                horizontalLineTo(7f)
                moveTo(11f, 18f)
                horizontalLineTo(20f)
                moveTo(9f, 16f)
                verticalLineTo(20f)
            }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(13.2f, 2.5f)
                lineTo(5.5f, 13.2f)
                horizontalLineTo(11.4f)
                lineTo(10.6f, 21.5f)
                lineTo(18.5f, 10.8f)
                horizontalLineTo(12.6f)
                close()
            }
        }.build()
    }

    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(19f, 6f)
                verticalLineTo(11.5f)
                curveTo(19f, 16.2f, 15.9f, 19.8f, 12f, 21.2f)
                curveTo(8.1f, 19.8f, 5f, 16.2f, 5f, 11.5f)
                verticalLineTo(6f)
                close()
                moveTo(9f, 12.2f)
                lineTo(11.1f, 14.3f)
                lineTo(15.2f, 10.2f)
            }
        }.build()
    }

    val TerminalLink: ImageVector by lazy {
        ImageVector.Builder(
            name = "TerminalLink",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                horizontalLineTo(20f)
                verticalLineTo(19f)
                horizontalLineTo(4f)
                close()
                moveTo(7.8f, 9.5f)
                lineTo(10.8f, 12f)
                lineTo(7.8f, 14.5f)
                moveTo(12.5f, 14.5f)
                horizontalLineTo(16.2f)
            }
        }.build()
    }

    val RadarScan: ImageVector by lazy {
        ImageVector.Builder(
            name = "RadarScan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 21f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
                close()
                moveTo(12f, 7.5f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 16.5f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 7.5f)
                close()
                moveTo(12f, 12f)
                lineTo(18.2f, 5.8f)
            }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 21f)
                curveTo(13.4f, 21f, 14.5f, 19.9f, 14.5f, 18.5f)
                curveTo(14.5f, 17.8f, 14.2f, 17.2f, 13.8f, 16.8f)
                curveTo(13.4f, 16.3f, 13.8f, 15.5f, 14.5f, 15.5f)
                horizontalLineTo(16.5f)
                curveTo(19f, 15.5f, 21f, 13.5f, 21f, 11f)
                curveTo(21f, 6.6f, 17f, 3f, 12f, 3f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 10f)
                arcTo(1.2f, 1.2f, 0f, true, true, 8f, 12.4f)
                arcTo(1.2f, 1.2f, 0f, true, true, 8f, 10f)
                close()
                moveTo(11.5f, 6.8f)
                arcTo(1.2f, 1.2f, 0f, true, true, 11.5f, 9.2f)
                arcTo(1.2f, 1.2f, 0f, true, true, 11.5f, 6.8f)
                close()
                moveTo(15.5f, 8.2f)
                arcTo(1.2f, 1.2f, 0f, true, true, 15.5f, 10.6f)
                arcTo(1.2f, 1.2f, 0f, true, true, 15.5f, 8.2f)
                close()
            }
        }.build()
    }

    val Copy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 9f)
                horizontalLineTo(19f)
                verticalLineTo(20f)
                horizontalLineTo(9f)
                close()
                moveTo(15f, 9f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(16f)
                horizontalLineTo(9f)
            }
        }.build()
    }

    val ExternalOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExternalOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                moveTo(20f, 4f)
                lineTo(11f, 13f)
                moveTo(18f, 14f)
                verticalLineTo(19f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(10f)
            }
        }.build()
    }

    val RestoreReset: ImageVector by lazy {
        ImageVector.Builder(
            name = "RestoreReset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f)
                verticalLineTo(9f)
                horizontalLineTo(9f)
                moveTo(4.8f, 8.8f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.2f, 16.5f)
            }
        }.build()
    }

    val EditPen: ImageVector by lazy {
        ImageVector.Builder(
            name = "EditPen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 4.5f)
                lineTo(19.5f, 8.5f)
                lineTo(8.5f, 19.5f)
                horizontalLineTo(4.5f)
                verticalLineTo(15.5f)
                close()
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9.5f, 6.5f)
                lineTo(15f, 12f)
                lineTo(9.5f, 17.5f)
            }
        }.build()
    }

    val ClockTimezone: ImageVector by lazy {
        ImageVector.Builder(
            name = "ClockTimezone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 21f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
                close()
                moveTo(12f, 7.2f)
                verticalLineTo(12.2f)
                lineTo(15.6f, 14.3f)
            }
        }.build()
    }
}
