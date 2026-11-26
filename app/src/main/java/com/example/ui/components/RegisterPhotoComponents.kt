package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.BillingCalculator
import com.example.domain.DailyRegisterCalculation
import com.example.media.RegisterPhotoManager
import com.example.ui.theme.DisplayFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun LocalPhotoImage(
    filePath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    version: Int = 0,
    maxDimension: Int = 1280,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, filePath, version, maxDimension) {
        value = if (filePath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                RegisterPhotoManager.loadBitmapWithExif(filePath, maxDimension = maxDimension)
            }
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (filePath.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun CropablePhotoEditor(
    photoPath: String,
    photoVersion: Int,
    rotationDegrees: Int,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    isProcessing: Boolean,
    hasPendingCropChanges: Boolean,
    onCropChanged: (Float, Float, Float, Float) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onApplyCrop: () -> Unit,
    onResetOriginal: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photoPath, photoVersion) {
        value = withContext(Dispatchers.IO) {
            RegisterPhotoManager.loadBitmapWithExif(photoPath, maxDimension = 1280)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val loaded = bitmap
        val ratio = if (loaded != null && loaded.height > 0) {
            (loaded.width.toFloat() / loaded.height.toFloat()).coerceIn(0.6f, 1.8f)
        } else {
            0.75f
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F1612))
        ) {
            val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "रजिस्टर फोटो प्रिव्ह्यू",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Interactive 4-corner Crop Overlay
            var activeCorner by remember { mutableStateOf(-1) }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cropLeft, cropTop, cropRight, cropBottom) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val nx = offset.x / boxW
                                val ny = offset.y / boxH
                                val corners = listOf(
                                    cropLeft to cropTop,     // 0: TL
                                    cropRight to cropTop,    // 1: TR
                                    cropLeft to cropBottom,  // 2: BL
                                    cropRight to cropBottom  // 3: BR
                                )
                                var bestIdx = -1
                                var bestDist = 0.18f
                                corners.forEachIndexed { idx, (cx, cy) ->
                                    val d = abs(nx - cx) + abs(ny - cy)
                                    if (d < bestDist) {
                                        bestDist = d
                                        bestIdx = idx
                                    }
                                }
                                activeCorner = bestIdx
                            },
                            onDragEnd = { activeCorner = -1 },
                            onDragCancel = { activeCorner = -1 },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / boxW
                                val dy = dragAmount.y / boxH
                                when (activeCorner) {
                                    0 -> onCropChanged(
                                        (cropLeft + dx).coerceIn(0f, cropRight - 0.15f),
                                        (cropTop + dy).coerceIn(0f, cropBottom - 0.15f),
                                        cropRight,
                                        cropBottom
                                    )
                                    1 -> onCropChanged(
                                        cropLeft,
                                        (cropTop + dy).coerceIn(0f, cropBottom - 0.15f),
                                        (cropRight + dx).coerceIn(cropLeft + 0.15f, 1f),
                                        cropBottom
                                    )
                                    2 -> onCropChanged(
                                        (cropLeft + dx).coerceIn(0f, cropRight - 0.15f),
                                        cropTop,
                                        cropRight,
                                        (cropBottom + dy).coerceIn(cropTop + 0.15f, 1f)
                                    )
                                    3 -> onCropChanged(
                                        cropLeft,
                                        cropTop,
                                        (cropRight + dx).coerceIn(cropLeft + 0.15f, 1f),
                                        (cropBottom + dy).coerceIn(cropTop + 0.15f, 1f)
                                    )
                                }
                            }
                        )
                    }
            ) {
                val l = cropLeft * size.width
                val t = cropTop * size.height
                val r = cropRight * size.width
                val b = cropBottom * size.height

                // Dim outside crop area
                val dim = Color.Black.copy(alpha = 0.45f)
                if (t > 0f) drawRect(dim, Offset.Zero, Size(size.width, t))
                if (b < size.height) drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
                if (l > 0f) drawRect(dim, Offset(0f, t), Size(l, b - t))
                if (r < size.width) drawRect(dim, Offset(r, t), Size(size.width - r, b - t))

                // Border rectangle
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(l, t),
                    size = Size(r - l, b - t),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Corner handles
                val handleR = 9.dp.toPx()
                listOf(
                    Offset(l, t),
                    Offset(r, t),
                    Offset(l, b),
                    Offset(r, b)
                ).forEach { pt ->
                    drawCircle(color = Color(0xFFF59E0B), radius = handleR, center = pt)
                    drawCircle(color = Color.White, radius = handleR * 0.45f, center = pt)
                }
            }

            // Fullscreen preview button
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clickable(onClick = onOpenFullscreen)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "मोठा फोटो पहा",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "मोठा पहा",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        Text(
            text = "टीप: कोपऱ्यांवरील पिवळे बिंदू ओढून फक्त आवश्यक रजिस्टर भाग क्रॉप करू शकता (मूळ फोटो सुरक्षित राहतो).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Photo Edit Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRotateLeft,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_rotate_left_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("डावीकडे", maxLines = 1)
            }
            OutlinedButton(
                onClick = onRotateRight,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_rotate_right_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("उजवीकडे", maxLines = 1)
            }
            OutlinedButton(
                onClick = onResetOriginal,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_reset_button")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("मूळ फोटो", maxLines = 1)
            }
        }

        if (hasPendingCropChanges) {
            Button(
                onClick = onApplyCrop,
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_crop_button")
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("निवडलेला क्रॉप लागू करा")
            }
        }
    }
}

@Composable
fun FullscreenPhotoDialog(
    title: String,
    primaryPath: String,
    alternatePath: String? = null,
    primaryLabel: String = "मूळ फोटो",
    alternateLabel: String = "क्रॉप केलेला फोटो",
    onDismiss: () -> Unit
) {
    var showAlternate by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val activePath = if (showAlternate && !alternatePath.isNullOrBlank()) alternatePath else primaryPath

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!alternatePath.isNullOrBlank() && alternatePath != primaryPath) {
                            TextButton(onClick = {
                                showAlternate = !showAlternate
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }) {
                                Text(
                                    text = if (showAlternate) primaryLabel else alternateLabel,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "बंद करा", tint = Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(activePath) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = nextScale
                                if (nextScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LocalPhotoImage(
                        filePath = activePath,
                        contentDescription = title,
                        maxDimension = 2048,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }

                Text(
                    text = "झूम करण्यासाठी दोन बोटांनी पिंच करा",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun RegisterAutomaticReportCard(
    calculation: DailyRegisterCalculation,
    dateDisplay: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "स्वयंचलित दैनिक अहवाल",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = dateDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            ReportCalcRow(
                label = "१. फुल आखा मसूर",
                formula = "${calculation.fullMasoorQty} × ₹${calculation.fullMasoorRate}",
                total = BillingCalculator.formatInr(calculation.fullMasoorIncome),
                testTag = "register_report_full_line"
            )
            ReportCalcRow(
                label = "२. हाफ पक्का मसूर",
                formula = "${calculation.halfMasoorQty} × ₹${calculation.halfMasoorRate}",
                total = BillingCalculator.formatInr(calculation.halfMasoorIncome),
                testTag = "register_report_half_line"
            )
            ReportCalcRow(
                label = "३. रोटी",
                formula = "${calculation.rotiQty} × ₹${calculation.rotiRate}",
                total = BillingCalculator.formatInr(calculation.rotiIncome),
                testTag = "register_report_roti_line"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "४. एकूण विक्री नग (Total Qty)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${calculation.totalQuantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("register_report_total_qty")
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "५. एकूण उत्पन्न (Total Income)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = BillingCalculator.formatInr(calculation.totalIncome),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("register_report_total_income")
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCalcRow(
    label: String,
    formula: String,
    total: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formula,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = DisplayFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = total,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
