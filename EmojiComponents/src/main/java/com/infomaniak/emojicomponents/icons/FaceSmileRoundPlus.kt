package com.infomaniak.emojicomponents.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap.Companion.Round as strokeCapRound
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as strokeJoinRound

internal val Icons.FaceSmileRoundPlus: ImageVector
    get() {
        if (_faceSmileRoundPlus != null) {
            return _faceSmileRoundPlus!!
        }
        _faceSmileRoundPlus = Builder(
            name = "FaceSmileRoundPlus",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
                group {
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(10.25f, 19.75f)
                        curveTo(5.05f, 19.75f, 0.75f, 15.45f, 0.75f, 10.25f)
                        curveTo(0.75f, 5.05f, 5.05f, 0.75f, 10.25f, 0.75f)
                        curveTo(15.45f, 0.75f, 19.75f, 5.05f, 19.75f, 10.25f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(11.047f, 15.75f)
                        curveTo(8.747f, 16.15f, 6.647f, 14.95f, 5.747f, 13.05f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(18.247f, 23.25f)
                        curveTo(21.008f, 23.25f, 23.247f, 21.012f, 23.247f, 18.25f)
                        curveTo(23.247f, 15.489f, 21.008f, 13.25f, 18.247f, 13.25f)
                        curveTo(15.486f, 13.25f, 13.247f, 15.489f, 13.247f, 18.25f)
                        curveTo(13.247f, 21.012f, 15.486f, 23.25f, 18.247f, 23.25f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(18.253f, 16.25f)
                        verticalLineTo(20.25f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(16.247f, 18.25f)
                        horizontalLineTo(20.247f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(6.558f, 9.058f)
                        curveTo(6.351f, 9.058f, 6.183f, 8.89f, 6.183f, 8.683f)
                        curveTo(6.183f, 8.476f, 6.351f, 8.308f, 6.558f, 8.308f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(6.558f, 9.058f)
                        curveTo(6.765f, 9.058f, 6.933f, 8.89f, 6.933f, 8.683f)
                        curveTo(6.933f, 8.476f, 6.765f, 8.308f, 6.558f, 8.308f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(14.058f, 9.058f)
                        curveTo(13.851f, 9.058f, 13.683f, 8.89f, 13.683f, 8.683f)
                        curveTo(13.683f, 8.476f, 13.851f, 8.308f, 14.058f, 8.308f)
                    }
                    path(
                        fill = SolidColor(Color(0x00000000)),
                        stroke = SolidColor(Color(0xFF888888)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = strokeCapRound,
                        strokeLineJoin = strokeJoinRound,
                        strokeLineMiter = 4.0f,
                        pathFillType = NonZero,
                    ) {
                        moveTo(14.058f, 9.058f)
                        curveTo(14.265f, 9.058f, 14.433f, 8.89f, 14.433f, 8.683f)
                        curveTo(14.433f, 8.476f, 14.265f, 8.308f, 14.058f, 8.308f)
                    }
                }
            }.build()
        return _faceSmileRoundPlus!!
    }

private var _faceSmileRoundPlus: ImageVector? = null

@Preview
@Composable
private fun Preview() {
    Box {
        Image(
            imageVector = Icons.FaceSmileRoundPlus,
            contentDescription = null,
            modifier = Modifier.size(250.dp)
        )
    }
}
