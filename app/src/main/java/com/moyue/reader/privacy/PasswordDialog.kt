package com.moyue.reader.privacy

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.content.Context
import android.content.ContextWrapper
import com.moyue.reader.R
import com.moyue.reader.di.Graph
import com.moyue.reader.ui.common.MoyueDialog
import kotlinx.coroutines.launch

enum class PasswordDialogKind { SETUP, UNLOCK, CHANGE }

/** BiometricPrompt 需要宿主 FragmentActivity */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val MIN_PIN = 4
private const val MAX_PIN = 8
private const val MIN_PATTERN_POINTS = 4

private sealed interface Stage {
    data object Loading : Stage
    data object ChooseType : Stage
    data class InputFirst(val type: String) : Stage
    data class InputConfirm(val type: String, val first: String) : Stage
    data class VerifyOld(val type: String) : Stage
}

/**
 * 隐私空间密码对话框：
 * - SETUP  首次进入，设置新密码（数字 4-8 位 / 图案至少 4 点）
 * - UNLOCK 验证密码进入
 * - CHANGE 先验旧密码，再设置新密码
 */
@Composable
fun PasswordDialog(
    kind: PasswordDialogKind,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val repo = Graph.privacyRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var stage by remember { mutableStateOf<Stage>(Stage.Loading) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }

    // 非可组合回调（提交/图案绘制）中使用的本地化文本，需先在此解析
    val strMismatch = stringResource(R.string.pwd_mismatch)
    val strWrong = stringResource(R.string.pwd_wrong)
    val strPatternSimple = stringResource(R.string.pwd_pattern_simple, MIN_PATTERN_POINTS)

    // 读取已存密码类型，决定起始阶段
    LaunchedEffect(kind) {
        val type = repo.passwordType()
        stage = when (kind) {
            PasswordDialogKind.UNLOCK ->
                if (type != null) Stage.VerifyOld(type) else Stage.ChooseType
            PasswordDialogKind.CHANGE ->
                if (type != null) Stage.VerifyOld(type) else Stage.ChooseType
            PasswordDialogKind.SETUP -> Stage.ChooseType
        }
    }

    // 解锁隐私空间：已开启指纹且设备支持 → 自动弹出系统生物识别（取消则回退手动输入）
    LaunchedEffect(kind, stage) {
        if (kind != PasswordDialogKind.UNLOCK || stage !is Stage.VerifyOld) return@LaunchedEffect
        if (!repo.bioEnabled()) return@LaunchedEffect
        val activity = context.findFragmentActivity() ?: return@LaunchedEffect
        val manager = BiometricManager.from(context)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) return@LaunchedEffect
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onDone()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_title))
                .setSubtitle(context.getString(R.string.biometric_subtitle))
                .setNegativeButtonText(context.getString(R.string.btn_cancel))
                .build()
        )
    }

    fun submitSecret(secret: String, type: String) {
        error = null
        when (val st = stage) {
            is Stage.InputFirst -> {
                pin = ""
                stage = Stage.InputConfirm(st.type, secret)
            }
            is Stage.InputConfirm -> {
                if (secret == st.first) {
                    scope.launch {
                        repo.setPassword(st.type, secret)
                        onDone()
                    }
                } else {
                    pin = ""
                    error = strMismatch
                    stage = Stage.InputFirst(st.type)
                }
            }
            is Stage.VerifyOld -> {
                scope.launch {
                    if (repo.verify(st.type, secret)) {
                        pin = ""
                        if (kind == PasswordDialogKind.UNLOCK) {
                            onDone()
                        } else {
                            stage = Stage.ChooseType
                        }
                    } else {
                        pin = ""
                        error = strWrong
                    }
                }
            }
            else -> Unit
        }
    }

    val inputType = when (val s = stage) {
        is Stage.InputFirst -> s.type
        is Stage.InputConfirm -> s.type
        is Stage.VerifyOld -> s.type
        else -> null
    }

    val title = when (stage) {
        Stage.Loading -> stringResource(R.string.title_private_space)
        Stage.ChooseType -> stringResource(R.string.pwd_setup_title)
        is Stage.InputFirst -> stringResource(R.string.pwd_new_title)
        is Stage.InputConfirm -> stringResource(R.string.pwd_confirm_title)
        is Stage.VerifyOld ->
            if (kind == PasswordDialogKind.CHANGE) stringResource(R.string.pwd_verify_change_title)
            else stringResource(R.string.pwd_verify_title)
    }

    MoyueDialog(
        onDismissRequest = onDismiss,
        title = title
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (val s = stage) {
                    Stage.Loading -> Spacer(Modifier.height(8.dp))

                    Stage.ChooseType -> {
                        Text(
                            stringResource(R.string.pwd_choose_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Row {
                            FilterChip(
                                selected = false,
                                onClick = { stage = Stage.InputFirst(PrivacyRepository.TYPE_PIN) },
                                label = { Text(stringResource(R.string.pwd_type_pin)) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            FilterChip(
                                selected = false,
                                onClick = { stage = Stage.InputFirst(PrivacyRepository.TYPE_PATTERN) },
                                label = { Text(stringResource(R.string.pwd_type_pattern)) }
                            )
                        }
                    }

                    is Stage.InputFirst, is Stage.InputConfirm, is Stage.VerifyOld -> {
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (inputType == PrivacyRepository.TYPE_PIN) {
                            Text(
                                stringResource(R.string.pwd_pin_hint, MIN_PIN, MAX_PIN),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            PinPad(
                                entered = pin,
                                onDigit = { if (pin.length < MAX_PIN) pin += it },
                                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                onConfirm = { submitSecret(pin, PrivacyRepository.TYPE_PIN) }
                            )
                        } else {
                            Text(
                                stringResource(R.string.pwd_pattern_hint, MIN_PATTERN_POINTS),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            PatternLock { pattern ->
                                val points = pattern.split("-").size
                                if (points < MIN_PATTERN_POINTS) {
                                    if (stage is Stage.InputFirst || stage is Stage.InputConfirm) {
                                        error = strPatternSimple
                                    } else {
                                        error = strWrong
                                    }
                                } else {
                                    submitSecret(pattern, PrivacyRepository.TYPE_PATTERN)
                                }
                            }
                        }
                    }
                }
                if (stage is Stage.VerifyOld) {
                    TextButton(
                        onClick = { showForgot = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            stringResource(R.string.pwd_forgot),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
        }
    }

    // 忘记密码 → 仅清除隐私空间数据（普通书架保留），随后可重新设置
    if (showForgot) {
        MoyueDialog(
            onDismissRequest = { showForgot = false },
            title = stringResource(R.string.pwd_forgot_title),
            subtitle = stringResource(R.string.pwd_forgot_body),
            confirmText = stringResource(R.string.pwd_forgot_confirm),
            onConfirm = {
                scope.launch {
                    runCatching {
                        Graph.privacyRepository.clearPassword()
                        Graph.bookRepository.deleteAllInSpace(space = 1, deleteFile = true)
                    }
                    showForgot = false
                    onDismiss()
                }
            },
            confirmDestructive = true
        )
    }
}
