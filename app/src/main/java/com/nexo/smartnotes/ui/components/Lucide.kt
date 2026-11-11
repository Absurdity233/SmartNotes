package com.nexo.smartnotes.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.nexo.smartnotes.R

/**
 * Lucide 图标资源（https://lucide.dev，ISC License）。
 * 24×24 网格、2px 圆头描边，比 Material 图标更轻、更统一。
 */
object Lucide {
    val Plus = R.drawable.ic_lucide_plus
    val Search = R.drawable.ic_lucide_search
    val ArrowLeft = R.drawable.ic_lucide_arrow_left
    val Square = R.drawable.ic_lucide_square
    val SquareCheck = R.drawable.ic_lucide_square_check
    val Trash = R.drawable.ic_lucide_trash
    val Pin = R.drawable.ic_lucide_pin
    val Settings = R.drawable.ic_lucide_settings
    val User = R.drawable.ic_lucide_user
    val Users = R.drawable.ic_lucide_users
    val Close = R.drawable.ic_lucide_close
    val Eye = R.drawable.ic_lucide_eye
    val EyeOff = R.drawable.ic_lucide_eye_off
    val Archive = R.drawable.ic_lucide_archive
    val ArchiveRestore = R.drawable.ic_lucide_archive_restore
    val Refresh = R.drawable.ic_lucide_refresh
    val Tag = R.drawable.ic_lucide_tag
    val Restore = R.drawable.ic_lucide_restore
    val Palette = R.drawable.ic_lucide_palette
    val Lock = R.drawable.ic_lucide_lock
    val Sun = R.drawable.ic_lucide_sun
    val Moon = R.drawable.ic_lucide_moon
    val Info = R.drawable.ic_lucide_info
    val Cloud = R.drawable.ic_lucide_cloud
    val CloudCheck = R.drawable.ic_lucide_cloud_check
    val CloudUpload = R.drawable.ic_lucide_cloud_upload
    val CloudOff = R.drawable.ic_lucide_cloud_off
    val ImagePlus = R.drawable.ic_lucide_image_plus
    val Image = R.drawable.ic_lucide_image
    val Send = R.drawable.ic_lucide_send
    val More = R.drawable.ic_lucide_more
    val Pencil = R.drawable.ic_lucide_pencil
    val Check = R.drawable.ic_lucide_check
    val Calendar = R.drawable.ic_lucide_calendar
    val AlarmClock = R.drawable.ic_lucide_alarm
    val Logout = R.drawable.ic_lucide_logout
    val LayoutGrid = R.drawable.ic_lucide_layout_grid
    val Rows = R.drawable.ic_lucide_rows
    val ListChecks = R.drawable.ic_lucide_list_checks
    val Notebook = R.drawable.ic_lucide_notebook
    val MapPin = R.drawable.ic_lucide_map_pin
    val Clock = R.drawable.ic_lucide_clock
    val BellRing = R.drawable.ic_lucide_bell_ring
}

/** 用 Lucide 资源绘制图标（参数顺序与 Material3 的 Icon 一致）。 */
@Composable
fun LucideIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
