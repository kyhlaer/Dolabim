package com.dresskit.wardrobe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dresskit.wardrobe.data.AppFeatureKey
import com.dresskit.wardrobe.data.RemoteConfigManager
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.data.SecurityGuards
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.ui.theme.DressKitStyle

/**
 * Remote-controlled banner / sponsored promo slot.
 * Renders NOTHING (0dp) when [RemoteConfigState.adsEnabled] is false or [slot] is not listed.
 */
@Composable
fun RemoteAdSlotBanner(
    slot: String,
    config: RemoteConfigState,
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    if (!config.isBannerVisible(slot)) return

    val context = LocalContext.current
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val isEn = lang != AppLanguage.TR

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
        color = DressKitStyle.secondarySurface(),
        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (config.announcementTitle.isNotBlank()) {
                    Text(
                        text = config.announcementTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (config.announcementMessage.isNotBlank()) {
                    Text(
                        text = config.announcementMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (SecurityGuards.isSafePublicHttpsUrl(config.ctaUrl)) {
                Button(
                    onClick = { SecurityGuards.openExternalUrlSafely(context, config.ctaUrl) },
                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = config.ctaLabel.ifBlank { if (isEn) "Open" else "Aç" },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Remote-controlled Pro access & activation dialog.
 * Only shown when remote config sets proEnabled=true and includes this feature in lockedFeatures.
 */
@Composable
fun RemoteProGateDialog(
    feature: AppFeatureKey,
    config: RemoteConfigState,
    lang: AppLanguage,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEn = lang != AppLanguage.TR
    var codeInput by remember { mutableStateOf("") }
    var invalidCodeError by remember { mutableStateOf(false) }

    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DressKitStyle.cardSurface(),
        icon = {
            Icon(
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = DressKitStyle.accentColor(),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = config.proTitle.ifBlank {
                    if (isEn) "Dolabım Pro Feature" else "Dolabım Pro Özelliği"
                },
                style = MaterialTheme.typography.titleLarge,
                color = primaryInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = feature.displayLabel(isEn),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DressKitStyle.accentColor()
                )
                Text(
                    text = config.proSubtitle.ifBlank {
                        if (isEn) {
                            "This feature is currently part of Dolabım Pro. Enter your activation code or open the pass link."
                        } else {
                            "Bu özellik şu anda Dolabım Pro kapsamında. Aktivasyon kodunu girebilir veya erişim bağlantısını açabilirsin."
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryInk
                )
                if (config.proUnlockCodes.isNotEmpty()) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            invalidCodeError = false
                        },
                        label = {
                            Text(if (isEn) "Activation Code" else "Aktivasyon Kodu")
                        },
                        singleLine = true,
                        isError = invalidCodeError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (invalidCodeError) {
                        val remSec = RemoteConfigManager.getRemainingLockoutSeconds(context)
                        Text(
                            text = if (remSec > 0) {
                                if (isEn) "Too many attempts. Wait ${remSec}s." else "Çok fazla hatalı deneme. ${remSec} sn bekleyin."
                            } else {
                                if (isEn) "Invalid code" else "Geçersiz aktivasyon kodu"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (config.proUnlockCodes.isNotEmpty()) {
                Button(
                    onClick = {
                        if (RemoteConfigManager.verifyAndUnlockPro(context, codeInput, config)) {
                            onUnlocked()
                        } else {
                            invalidCodeError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEn) "Unlock" else "Etkinleştir")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (SecurityGuards.isSafePublicHttpsUrl(config.proPurchaseUrl)) {
                    OutlinedButton(
                        onClick = { SecurityGuards.openExternalUrlSafely(context, config.proPurchaseUrl) }
                    ) {
                        Text(if (isEn) "Get Pro" else "Pro Al")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (isEn) "Close" else "Kapat", color = secondaryInk)
                }
            }
        }
    )
}
