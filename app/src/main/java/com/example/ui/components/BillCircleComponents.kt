package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.Bill
import com.example.data.BillStatus
import com.example.data.MemberRole
import com.example.data.PaymentStatus
import com.example.ui.theme.FinancialFigureLargeStyle
import com.example.ui.theme.FinancialFigureStyle
import com.example.ui.theme.LocalBillCircleColors
import com.example.util.CurrencyAndSplitUtils
import com.example.util.QrCodeGenerator

@Composable
fun StatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun BillStatusBadge(statusRaw: String, modifier: Modifier = Modifier) {
    val ext = LocalBillCircleColors.current
    val status = BillStatus.fromString(statusRaw)
    val (bg, fg) = when (status) {
        BillStatus.CLEARED -> ext.successContainer to ext.success
        BillStatus.DISPUTED -> ext.dangerContainer to ext.danger
        BillStatus.PAYMENT_IN_PROGRESS -> ext.infoContainer to ext.info
        BillStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        BillStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> ext.warningContainer to ext.warning
    }
    StatusPill(label = status.label, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun PaymentStatusBadge(statusRaw: String, modifier: Modifier = Modifier) {
    val ext = LocalBillCircleColors.current
    val status = PaymentStatus.fromString(statusRaw)
    val (bg, fg) = when (status) {
        PaymentStatus.PAYMENT_VERIFIED -> ext.successContainer to ext.success
        PaymentStatus.PAYMENT_SUBMITTED -> ext.infoContainer to ext.info
        PaymentStatus.PARTIALLY_PAID -> ext.warningContainer to ext.warning
        PaymentStatus.PAYMENT_REJECTED -> ext.dangerContainer to ext.danger
        PaymentStatus.NOT_PAID -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(label = status.label, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun RoleBadge(roleRaw: String, modifier: Modifier = Modifier) {
    val ext = LocalBillCircleColors.current
    val role = MemberRole.fromString(roleRaw)
    val (bg, fg, label) = when (role) {
        MemberRole.ADMIN -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, "Admin")
        MemberRole.MEMBER -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, "Member")
        MemberRole.VIEWER -> Triple(ext.warningContainer, ext.warning, "Viewer")
    }
    StatusPill(label = label, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun HeroBalanceCard(
    totalDistributedPaise: Long,
    totalCollectedPaise: Long,
    outstandingPaise: Long,
    distributedLabel: String,
    collectedLabel: String,
    outstandingLabel: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalBillCircleColors.current
    val progress = if (totalDistributedPaise > 0L) {
        (totalCollectedPaise.toFloat() / totalDistributedPaise.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(ext.heroStart, ext.heroEnd)))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = distributedLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.heroOnSurface.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = CurrencyAndSplitUtils.formatInr(totalDistributedPaise),
                            style = FinancialFigureLargeStyle,
                            color = ext.heroOnSurface
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}% collected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                        Text(
                            text = "$outstandingLabel: ${CurrencyAndSplitUtils.formatInr(outstandingPaise)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.heroOnSurface.copy(alpha = 0.85f)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ext.heroOnSurface.copy(alpha = 0.16f)
                    )
                }

                HorizontalDivider(color = ext.heroOnSurface.copy(alpha = 0.14f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = collectedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.heroOnSurface.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = CurrencyAndSplitUtils.formatInr(totalCollectedPaise),
                            style = FinancialFigureStyle,
                            color = Color(0xFF34D399)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = outstandingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.heroOnSurface.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = CurrencyAndSplitUtils.formatInr(outstandingPaise),
                            style = FinancialFigureStyle,
                            color = Color(0xFFFBBF24)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionTile(
    title: String,
    icon: ImageVector,
    highlighted: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalBillCircleColors.current
    val bg = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val iconBg = if (highlighted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
    val iconTint = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .defaultMinSize(minHeight = 92.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = if (highlighted) null else BorderStroke(1.dp, ext.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BillSummaryCard(
    bill: Bill,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalBillCircleColors.current
    val progress = bill.completionPercent / 100f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bill_card_${bill.billCode}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = bill.billCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = bill.groupName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                BillStatusBadge(bill.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = CurrencyAndSplitUtils.formatInr(bill.totalPaise),
                        style = FinancialFigureStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${bill.category} • Due ${CurrencyAndSplitUtils.formatDate(bill.dueDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${bill.paidCount}/${bill.participantCount} paid",
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.success
                    )
                    Text(
                        text = "Left ${CurrencyAndSplitUtils.formatInr(bill.outstandingPaise)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (bill.status == BillStatus.CLEARED.name) ext.success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalBillCircleColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun UpiQrDialog(
    upiId: String,
    recipientName: String,
    amountPaise: Long,
    billCode: String,
    billTitle: String,
    onDismiss: () -> Unit,
    onLaunchUpi: () -> Unit
) {
    val upiUri = remember(upiId, recipientName, amountPaise, billCode) {
        CurrencyAndSplitUtils.buildUpiPaymentUri(upiId, recipientName, amountPaise, billCode, billTitle).toString()
    }
    val qrBitmap = remember(upiUri) {
        QrCodeGenerator.generateBitmap(upiUri, 540)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Scan to Pay via UPI", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "$recipientName • $upiId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyAndSplitUtils.formatInr(amountPaise),
                    style = FinancialFigureLargeStyle,
                    color = MaterialTheme.colorScheme.primary
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "UPI QR code for $upiId",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Text("Unable to generate QR code.", color = MaterialTheme.colorScheme.error)
                }

                Text(
                    text = "After completing UPI payment, upload your payment screenshot or UTR number so the administrator can verify it. BillCircle never asks for UPI PINs or OTPs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) { Text("Close") }
                    Button(
                        onClick = onLaunchUpi,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) { Text("Open UPI App") }
                }
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(
    title: String,
    bitmap: Bitmap,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save / Share")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) { Text("Close") }
                }
            }
        }
    }
}
