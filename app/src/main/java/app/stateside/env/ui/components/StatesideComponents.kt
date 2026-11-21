package app.stateside.env.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.data.UserSettings
import app.stateside.env.ui.SimCardUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val appIconMemoryCache = LruCache<String, ImageBitmap>(80)

/**
 * 与新版桌面图标同源的高精度 Compose 矢量徽标：
 * 深海蓝宝石底座 + 阳极氧化钛金属切边环 + 立体水晶四芒罗盘 + 实时状态信号星点。
 */
@Composable
fun StatesideBrandEmblem(
    size: Dp = 40.dp,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "brand_pulse")
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val corner = w * 0.27f

        // 1. 圆角黑曜蓝宝石底座
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0A1938), Color(0xFF060E21)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            cornerRadius = CornerRadius(corner, corner)
        )

        // 2. 中心极光呼吸光晕
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isActive) Color(0xFF10B981).copy(alpha = haloAlpha) else Color(0xFF2563EB).copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = c,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = c
        )

        // 3. 阳极氧化金属外环
        val ringR = w * 0.32f
        val ringStroke = w * 0.068f
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF0F9FF),
                    Color(0xFF38BDF8),
                    Color(0xFF2563EB),
                    Color(0xFF1E3A8A)
                ),
                start = Offset(w * 0.2f, h * 0.2f),
                end = Offset(w * 0.8f, h * 0.8f)
            ),
            radius = ringR,
            center = c,
            style = Stroke(width = ringStroke)
        )

        // 4. 四芒水晶罗盘主星（明暗双切面）
        val outerTip = w * 0.255f
        val waist = w * 0.068f

        val lightStar = Path().apply {
            moveTo(c.x, c.y - outerTip)
            lineTo(c.x + waist, c.y - waist)
            lineTo(c.x + outerTip, c.y)
            lineTo(c.x, c.y)
            lineTo(c.x - outerTip, c.y)
            lineTo(c.x - waist, c.y - waist)
            close()
        }
        drawPath(
            path = lightStar,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF7DD3FC)),
                start = Offset(c.x - outerTip, c.y - outerTip),
                end = Offset(c.x + outerTip, c.y)
            )
        )

        val shadowStar = Path().apply {
            moveTo(c.x - outerTip, c.y)
            lineTo(c.x, c.y)
            lineTo(c.x + outerTip, c.y)
            lineTo(c.x + waist, c.y + waist)
            lineTo(c.x, c.y + outerTip)
            lineTo(c.x - waist, c.y + waist)
            close()
        }
        drawPath(
            path = shadowStar,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)),
                start = Offset(c.x, c.y),
                end = Offset(c.x, c.y + outerTip)
            )
        )

        // 中心明珠锚点
        drawCircle(
            color = Color.White,
            radius = w * 0.035f,
            center = c
        )

        // 5. 右上角状态星点（生效时翡翠绿，待机时冰蓝）
        val dotCenter = Offset(c.x + ringR * 0.707f, c.y - ringR * 0.707f)
        drawCircle(
            color = Color(0xFF060E21),
            radius = w * 0.068f,
            center = dotCenter
        )
        drawCircle(
            color = if (isActive) Color(0xFF34D399) else Color(0xFF7DD3FC),
            radius = w * 0.048f,
            center = dotCenter
        )
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    dotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun IconBadgeBox(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(containerColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    badgeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadgeBox(
            icon = icon,
            tint = iconTint,
            containerColor = iconContainerColor
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) titleColor else titleColor.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!badgeText.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            StatusPillBadge(
                text = badgeText,
                containerColor = badgeContainerColor,
                contentColor = badgeContentColor
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = StatesideIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadgeBox(
            icon = icon,
            tint = iconTint,
            containerColor = iconContainerColor
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 号卡工作坊顶部「系统状态栏实时仿真预览窗」：
 * 直观呈现双卡状态栏与控制中心运营商显标的当前生效值与预期目标值。
 */
@Composable
fun LiveStatusBarMockup(
    simCards: List<SimCardUiModel>,
    settings: UserSettings,
    modifier: Modifier = Modifier
) {
    val clockText = runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }.getOrDefault("12:00")

    val carrierPreview = if (simCards.isEmpty()) {
        val target0 = settings.resolveTargetCarrierLabelForSlot(0) ?: "原运营商"
        "US · $target0"
    } else {
        simCards.joinToString("  |  ") { sim ->
            sim.currentReportedCarrier
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0A1324),
        border = BorderStroke(1.dp, Color(0xFF1E3256)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 仿真手机顶部状态栏条
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF111E38),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = clockText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                            softWrap = false
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = carrierPreview,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF67E8F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "5G",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = "Wi-Fi",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF34D399).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFF34D399))
                        ) {
                            Text(
                                text = "US",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6EE7B7),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "状态栏与控制中心实时读数",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "3GPP EF_PNN · NITZ 穿透引擎",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7DD3FC)
                )
            }
        }
    }
}

@Composable
fun InstalledAppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cached = appIconMemoryCache.get(packageName)
    val bitmap by produceState(initialValue = cached, key1 = packageName) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val px = 112
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(canvas)
                    bmp.asImageBitmap().also { appIconMemoryCache.put(packageName, it) }
                }.getOrNull()
            }
            value = loaded
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
fun AppMonogramPlaceholder(
    letter: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(
            text = letter.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
