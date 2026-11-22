package com.dresskit.wardrobe.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var customAmountText by remember { mutableStateOf("24") }
    var useDaysUnit by remember { mutableStateOf(false) }
    var pendingPermissionFile by remember { mutableStateOf<File?>(null) }

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
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DressKitStyle.secondarySurface(),
                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 170.dp)
                            ) {
                                Text(
                                    text = updateInfo.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = primaryInk,
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(10.dp)
                                )
                            }
                        } else {
                            Text(
                                text = localizedText(
                                    lang,
                                    "Dolabım v${updateInfo.versionName} sürümü indirilip kurulmaya hazır.",
                                    "Dolabım v${updateInfo.versionName} is ready to download and install.",
                                    "Dolabım v${updateInfo.versionName} steht zum Download bereit.",
                                    "Dolabım v${updateInfo.versionName} est prêt à être téléchargé.",
                                    "Dolabım v${updateInfo.versionName} está listo para descargar.",
                                    "Dolabım v${updateInfo.versionName} 已准备好下载并安装。"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryInk
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
