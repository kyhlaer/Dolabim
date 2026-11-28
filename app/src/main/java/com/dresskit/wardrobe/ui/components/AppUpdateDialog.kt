package com.dresskit.wardrobe.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dresskit.wardrobe.data.AppUpdateInfo
import com.dresskit.wardrobe.data.AppUpdateManager
import com.dresskit.wardrobe.data.UpdateDownloadState
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var downloadState by remember(updateInfo.versionName) {
        mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
    }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var showSnoozePicker by remember { mutableStateOf(false) }
    var showUpdateNotesModal by remember { mutableStateOf(false) }
    var customAmountText by remember { mutableStateOf("24") }
    var useDaysUnit by remember { mutableStateOf(false) }
    var pendingPermissionFile by remember { mutableStateOf<File?>(null) }

    val fallbackBullets = remember(lang) {
        if (lang == com.dresskit.wardrobe.model.AppLanguage.TR) {
            AppUpdateManager.DEFAULT_WHATS_NEW_TR
        } else {
            AppUpdateManager.DEFAULT_WHATS_NEW_EN
        }
    }
    val parsedHighlights = remember(updateInfo.releaseNotes, fallbackBullets) {
        AppUpdateManager.parseReleaseNotesHighlights(updateInfo.releaseNotes, fallbackBullets)
    }

    if (showUpdateNotesModal) {
        WhatsNewDialog(
            versionTag = "v${updateInfo.versionName}",
            releaseBullets = parsedHighlights,
            onDismiss = { showUpdateNotesModal = false }
        )
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingPermissionFile
        if (file != null && AppUpdateManager.canInstallPackages(context)) {
            val launched = AppUpdateManager.launchApkInstaller(context, file)
            if (launched) {
                onDismiss()
            } else {
                downloadState = UpdateDownloadState.ReadyToInstall(file)
            }
        } else if (file != null) {
            downloadState = UpdateDownloadState.NeedsInstallPermission(file)
        }
    }

    fun startDownload() {
        activeJob?.cancel()
        val initialTotal = if (updateInfo.deltaPatchUrl != null && updateInfo.deltaPatchSizeBytes > 0L) {
            updateInfo.deltaPatchSizeBytes
        } else {
            updateInfo.apkSizeBytes
        }.coerceAtLeast(1L)
        downloadState = UpdateDownloadState.Downloading(0L, initialTotal, 0)
        activeJob = scope.launch {
            val res = try {
                AppUpdateManager.downloadAndVerifyApk(
                    context = context,
                    info = updateInfo,
                    onProgress = { dl, tot, pct ->
                        downloadState = UpdateDownloadState.Downloading(dl, tot, pct)
                    },
                    onVerifying = {
                        downloadState = UpdateDownloadState.Verifying
                    }
                )
            } catch (_: CancellationException) {
                downloadState = UpdateDownloadState.Idle
                return@launch
            }

            res.onSuccess { apkFile ->
                if (AppUpdateManager.canInstallPackages(context)) {
                    val launched = AppUpdateManager.launchApkInstaller(context, apkFile)
                    if (launched) {
                        onDismiss()
                    } else {
                        downloadState = UpdateDownloadState.ReadyToInstall(apkFile)
                    }
                } else {
                    downloadState = UpdateDownloadState.NeedsInstallPermission(apkFile)
                }
            }.onFailure { err ->
                downloadState = UpdateDownloadState.Error(
                    err.localizedMessage ?: localizedText(
                        lang,
                        "İndirme veya güvenlik doğrulaması başarısız.",
                        "Download or verification failed.",
                        "Download oder Verifizierung fehlgeschlagen.",
                        "Échec du téléchargement ou de la vérification.",
                        "Error al descargar o verificar.",
                        "下载或安全校验失败。"
                    )
                )
            }
        }
    }

    fun applySnooze(durationMs: Long, displayLabel: String) {
        AppUpdateManager.snoozeUpdate(context, updateInfo.versionName, durationMs)
        Toast.makeText(
            context,
            localizedText(
                lang,
                "Güncelleme $displayLabel hatırlatılacak.",
                "Reminder set for $displayLabel.",
                "Erinnerung in $displayLabel.",
                "Rappel défini dans $displayLabel.",
                "Recordatorio en $displayLabel.",
                "将在 $displayLabel 后再次提醒。"
            ),
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
    }

    val isDeltaPatch = updateInfo.deltaPatchUrl != null && updateInfo.deltaPatchSizeBytes > 0L
    val sizeLabel = if (isDeltaPatch) {
        val deltaMb = String.format(Locale.US, "%.1f MB", updateInfo.deltaPatchSizeBytes / (1024.0 * 1024.0))
        val smartBadge = localizedText(lang, "Akıllı Yama", "Smart Patch", "Smart-Patch", "Patch malin", "Parche inteligente", "增量更新")
        "$deltaMb · $smartBadge"
    } else {
        String.format(Locale.US, "%.1f MB", updateInfo.apkSizeBytes / (1024.0 * 1024.0))
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading && downloadState !is UpdateDownloadState.Verifying) {
                onDismiss()
            }
        },
        containerColor = DressKitStyle.cardSurface(),
        titleContentColor = primaryInk,
        textContentColor = secondaryInk,
        icon = {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null,
                tint = DressKitStyle.accentColor(),
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = localizedText(
                        lang,
                        "Yeni Güncelleme Hazır!",
                        "New Update Available!",
                        "Neues Update verfügbar!",
                        "Nouvelle mise à jour disponible !",
                        "¡Nueva actualización disponible!",
                        "发现新版本！"
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = primaryInk
                )
                Text(
                    text = "${updateInfo.releaseTitle} · $sizeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = DressKitStyle.accentColor()
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val state = downloadState) {
                    is UpdateDownloadState.Idle -> {
                        Text(
                            text = localizedText(
                                lang,
                                "Dolabım v${updateInfo.versionName} sürümü indirilip kurulmaya hazır. Yeni özellikleri görmek için Güncelleme Notları’na dokun.",
                                "Dolabım v${updateInfo.versionName} is ready to download and install. Tap Update Notes to see what's new.",
                                "Dolabım v${updateInfo.versionName} steht zum Download bereit. Tippe auf Update-Hinweise für alle Neuigkeiten.",
                                "Dolabım v${updateInfo.versionName} est prêt. Touchez Notes de mise à jour pour découvrir les nouveautés.",
                                "Dolabım v${updateInfo.versionName} está listo. Toca Notas de actualización para ver las novedades.",
                                "Dolabım v${updateInfo.versionName} 已就绪，点击更新说明查看本次亮点。"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryInk
                        )

                        OutlinedButton(
                            onClick = { showUpdateNotesModal = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = DressKitStyle.accentColor(),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = localizedText(
                                    lang,
                                    "Güncelleme Notları",
                                    "Update Notes",
                                    "Update-Hinweise",
                                    "Notes de mise à jour",
                                    "Notas de actualización",
                                    "更新说明"
                                ),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = DressKitStyle.accentColor()
                            )
                        }

                        if (showSnoozePicker) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DressKitStyle.secondarySurface(),
                                border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Ne zaman tekrar hatırlatılsın?",
                                            "Remind me again after:",
                                            "Erneut erinnern nach:",
                                            "Me rappeler dans :",
                                            "Recordar después de:",
                                            "多久后再次提醒？"
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = primaryInk
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            (6L * 3_600_000L) to localizedText(lang, "6 Saat", "6 Hours", "6 Std", "6 h", "6 h", "6 小时"),
                                            (24L * 3_600_000L) to localizedText(lang, "1 Gün", "1 Day", "1 Tag", "1 j", "1 día", "1 天"),
                                            (72L * 3_600_000L) to localizedText(lang, "3 Gün", "3 Days", "3 Tage", "3 j", "3 días", "3 天"),
                                            (168L * 3_600_000L) to localizedText(lang, "7 Gün", "7 Days", "7 Tage", "7 j", "7 días", "7 天")
                                        ).forEach { (ms, lbl) ->
                                            FilterChipPill(
                                                label = lbl,
                                                selected = false,
                                                onClick = { applySnooze(ms, lbl) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customAmountText,
                                            onValueChange = { input ->
                                                customAmountText = input.filter { it.isDigit() }.take(3)
                                            },
                                            label = {
                                                Text(localizedText(lang, "Süre", "Duration", "Dauer", "Durée", "Tiempo", "时长"))
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChipPill(
                                            label = localizedText(lang, "Saat", "Hr", "Std", "H", "Hr", "时"),
                                            selected = !useDaysUnit,
                                            onClick = { useDaysUnit = false }
                                        )
                                        FilterChipPill(
                                            label = localizedText(lang, "Gün", "Day", "Tag", "Jour", "Día", "天"),
                                            selected = useDaysUnit,
                                            onClick = { useDaysUnit = true }
                                        )
                                    }

                                    val customNum = customAmountText.toLongOrNull()?.coerceIn(1L, 90L)
                                    Button(
                                        onClick = {
                                            val amt = customNum ?: 12L
                                            val unitMs = if (useDaysUnit) 86_400_000L else 3_600_000L
                                            val unitLbl = if (useDaysUnit) {
                                                localizedText(lang, "$amt gün", "$amt day(s)", "$amt Tag(e)", "$amt j", "$amt día(s)", "$amt 天")
                                            } else {
                                                localizedText(lang, "$amt saat", "$amt hour(s)", "$amt Std", "$amt h", "$amt h", "$amt 小时")
                                            }
                                            applySnooze(amt * unitMs, unitLbl)
                                        },
                                        enabled = customNum != null,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DressKitStyle.buttonBg(),
                                            contentColor = DressKitStyle.buttonFg()
                                        )
                                    ) {
                                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            localizedText(
                                                lang,
                                                "Hatırlatıcıyı Kaydet",
                                                "Save Reminder",
                                                "Erinnerung speichern",
                                                "Enregistrer le rappel",
                                                "Guardar recordatorio",
                                                "保存提醒"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        val dlMb = String.format(Locale.US, "%.1f", state.downloadedBytes / (1024.0 * 1024.0))
                        val totMb = String.format(Locale.US, "%.1f", state.totalBytes / (1024.0 * 1024.0))
                        Text(
                            text = localizedText(
                                lang,
                                "Güncelleme indiriliyor... %${state.percent} ($dlMb / $totMb MB)",
                                "Downloading update... ${state.percent}% ($dlMb / $totMb MB)",
                                "Update wird geladen... ${state.percent}% ($dlMb / $totMb MB)",
                                "Téléchargement... ${state.percent}% ($dlMb / $totMb MB)",
                                "Descargando... ${state.percent}% ($dlMb / $totMb MB)",
                                "正在下载更新... ${state.percent}% ($dlMb / $totMb MB)"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryInk
                        )
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = DressKitStyle.accentColor(),
                            trackColor = DressKitStyle.secondarySurface()
                        )
                    }

                    is UpdateDownloadState.Verifying -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = DressKitStyle.accentColor(),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = localizedText(
                                    lang,
                                    "İmza sertifikası ve dosya bütünlüğü doğrulanıyor...",
                                    "Verifying signature certificate and integrity...",
                                    "Signatur und Integrität werden geprüft...",
                                    "Vérification de la signature et de l'intégrité...",
                                    "Verificando firma e integridad...",
                                    "正在验证签名证书与文件完整性..."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryInk
                            )
                        }
                    }

                    is UpdateDownloadState.NeedsInstallPermission -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = DressKitStyle.accentColor())
                            Text(
                                text = localizedText(
                                    lang,
                                    "Kurulumu başlatmak için 'Bu kaynaktan uygulamalara izin ver' ayarını açın. İzin verince kurulum otomatik başlayacaktır.",
                                    "Allow 'Install unknown apps' permission for Dolabım. Installation will start automatically once granted.",
                                    "Erlaube 'Unbekannte Apps installieren'. Die Installation startet danach automatisch.",
                                    "Autorisez l'installation depuis cette source pour lancer la mise à jour.",
                                    "Permite instalar aplicaciones desconocidas para iniciar la actualización.",
                                    "请授予“安装未知应用”权限，授权后将自动开始安装。"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryInk
                            )
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Text(
                            text = localizedText(
                                lang,
                                "Güncelleme doğrulandı ve kuruluma hazır.",
                                "Update verified and ready to install.",
                                "Update verifiziert und bereit zur Installation.",
                                "Mise à jour vérifiée et prête.",
                                "Actualización verificada y lista.",
                                "更新已验证，可以安装。"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryInk
                        )
                    }

                    is UpdateDownloadState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val state = downloadState) {
                is UpdateDownloadState.Idle -> {
                    Button(
                        onClick = { startDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = localizedText(
                                lang,
                                "Şimdi İndir",
                                "Download Now",
                                "Jetzt laden",
                                "Télécharger",
                                "Descargar ahora",
                                "立即下载安装"
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    TextButton(
                        onClick = {
                            activeJob?.cancel()
                            downloadState = UpdateDownloadState.Idle
                        }
                    ) {
                        Text(
                            localizedText(lang, "İptal", "Cancel", "Abbrechen", "Annuler", "Cancelar", "取消"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is UpdateDownloadState.Verifying -> Unit

                is UpdateDownloadState.NeedsInstallPermission -> {
                    Button(
                        onClick = {
                            pendingPermissionFile = state.apkFile
                            installPermissionLauncher.launch(AppUpdateManager.createInstallPermissionIntent(context))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Text(
                            localizedText(lang, "İzin Ver & Kur", "Allow & Install", "Erlauben & Installieren", "Autoriser", "Permitir e instalar", "去授权并安装")
                        )
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            if (AppUpdateManager.launchApkInstaller(context, state.apkFile)) {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Text(localizedText(lang, "Kur", "Install", "Installieren", "Installer", "Instalar", "立即安装"))
                    }
                }

                is UpdateDownloadState.Error -> {
                    Button(
                        onClick = { startDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Text(localizedText(lang, "Tekrar Dene", "Retry", "Wiederholen", "Réessayer", "Reintentar", "重试"))
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState is UpdateDownloadState.Idle) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showSnoozePicker = !showSnoozePicker }) {
                        Text(
                            text = localizedText(
                                lang,
                                "Hatırlat",
                                "Remind Later",
                                "Später erinnern",
                                "Rappeler",
                                "Recordar luego",
                                "稍后提醒"
                            ),
                            color = DressKitStyle.accentColor(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = localizedText(lang, "Sonra", "Later", "Später", "Plus tard", "Luego", "下次再说"),
                            color = secondaryInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else if (downloadState !is UpdateDownloadState.Downloading && downloadState !is UpdateDownloadState.Verifying) {
                TextButton(onClick = onDismiss) {
                    Text(
                        localizedText(lang, "Kapat", "Close", "Schließen", "Fermer", "Cerrar", "关闭"),
                        color = secondaryInk
                    )
                }
            }
        }
    )
}

private data class WhatsNewFeatureCard(
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * 1:1 visual match with the Web "Yeni Sürümde Neler Var?" modal:
 * Header with close icon, accent-tinted banner with version pill, scrollable icon-card feature
 * highlights, and full-width bright CTA ("Harika, Keşfet").
 */
@Composable
fun WhatsNewDialog(
    versionTag: String,
    releaseBullets: List<String>? = null,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val isTr = lang == com.dresskit.wardrobe.model.AppLanguage.TR
    val isDark = DressKitStyle.isDark()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val accent = DressKitStyle.accentColor()

    val defaultCards = remember(isTr) {
        if (isTr) {
            listOf(
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.Explore,
                    title = "112 Kombinlik Yenilenen İlham Kataloğu",
                    description = "6 modern tarz, tüm mevsim seçenekleri ve ten uyumu rozetiyle Erkek & Kadın kombin ilhamı tamamen yenilendi."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Moduna Göre Kombin",
                    description = "Kombinler sekmesinin en üstünde açılıp kapanabilen mod seçiciyle ruh haline uygun kombini tek dokunuşta üret ve parçalarını değiştir."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.AcUnit,
                    title = "Kış & Yılbaşı Teması",
                    description = "Alt menüde fizikli kar birikimi, sekme geçişinde dökülen kar efekti, şapkalı başlıklar ve tatlı mini yılbaşı süsleri (Ayarlar’dan aç/kapat)."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.FaceRetouchingNatural,
                    title = "Güçlendirilen Yüz & Ten Analizi",
                    description = "Yüksek çözünürlüklü fotoğraflarda daha hızlı ve doğru ten alt tonu algılama ile sana en çok yakışan renk paletini keşfet."
                )
            )
        } else {
            listOf(
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.Explore,
                    title = "Refreshed 112-Look Inspiration Catalog",
                    description = "Curated Men & Women outfits across 6 modern styles, all seasons, and live skin-tone match badges."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Outfit by Mood",
                    description = "Collapsible mood selector at the top of Outfits with one-tap outfit generation and full piece swapping."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.AcUnit,
                    title = "Winter & New Year Theme",
                    description = "Physics snow accumulation on the bottom dock, tab-switch snow shake, festive title hats, and mini holiday ornaments (toggleable in Settings)."
                ),
                WhatsNewFeatureCard(
                    icon = Icons.Outlined.FaceRetouchingNatural,
                    title = "Enhanced Face & Skin Tone Analysis",
                    description = "Faster, more accurate undertone detection on high-resolution photos to discover your best color palette."
                )
            )
        }
    }

    val cards = remember(releaseBullets, defaultCards) {
        if (releaseBullets.isNullOrEmpty()) {
            defaultCards
        } else {
            val iconCycle = listOf(
                Icons.Outlined.Explore,
                Icons.Outlined.AutoAwesome,
                Icons.Outlined.AcUnit,
                Icons.Outlined.FaceRetouchingNatural
            )
            releaseBullets.mapIndexed { idx, raw ->
                val colonIdx = raw.indexOf(':')
                if (colonIdx in 3..58 && colonIdx + 1 < raw.length) {
                    WhatsNewFeatureCard(
                        icon = iconCycle[idx % iconCycle.size],
                        title = raw.substring(0, colonIdx).trim(),
                        description = raw.substring(colonIdx + 1).trim()
                    )
                } else {
                    WhatsNewFeatureCard(
                        icon = iconCycle[idx % iconCycle.size],
                        title = localizedText(
                            lang,
                            "Yenilik #${idx + 1}",
                            "Highlight #${idx + 1}"
                        ),
                        description = raw.trim()
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(22.dp),
            color = DressKitStyle.cardSurface(),
            border = BorderStroke(1.dp, DressKitStyle.borderColor()),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedText(
                            lang,
                            "Yeni Sürümde Neler Var?",
                            "What's New?",
                            "Was ist neu?",
                            "Nouveautés",
                            "¿Qué hay de nuevo?",
                            "新版本亮点"
                        ),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = primaryInk
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = localizedText(lang, "Kapat", "Close"),
                            tint = secondaryInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = DressKitStyle.borderColor(), thickness = 1.dp)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Accent Banner Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = if (isDark) 0.14f else 0.10f),
                        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.42f else 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = localizedText(
                                        lang,
                                        "Dolabım Yenilendi",
                                        "Dolabım Refreshed",
                                        "Dolabım aktualisiert",
                                        "Dolabım amélioré",
                                        "Dolabım renovado",
                                        "Dolabım 焕新升级"
                                    ),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = primaryInk
                                )
                                Text(
                                    text = localizedText(
                                        lang,
                                        "En yeni ve kullanışlı özelliklere hızlı bakış",
                                        "A quick look at the newest and most useful features"
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = secondaryInk
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = accent.copy(alpha = if (isDark) 0.16f else 0.12f),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = versionTag,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Feature Highlight Cards
                    cards.forEach { card ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = DressKitStyle.secondarySurface(),
                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = accent.copy(alpha = if (isDark) 0.15f else 0.11f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = card.icon,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = card.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp
                                        ),
                                        color = primaryInk
                                    )
                                    Text(
                                        text = card.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.sp
                                        ),
                                        color = secondaryInk
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DressKitStyle.borderColor(), thickness = 1.dp)

                // Footer Primary CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DressKitStyle.buttonBg(),
                            contentColor = DressKitStyle.buttonFg()
                        )
                    ) {
                        Text(
                            text = localizedText(
                                lang,
                                "Harika, Keşfet",
                                "Awesome, Explore",
                                "Super, entdecken",
                                "Super, découvrir",
                                "Genial, explorar",
                                "太棒了，去体验"
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
