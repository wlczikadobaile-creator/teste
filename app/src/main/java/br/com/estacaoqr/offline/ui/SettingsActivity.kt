package br.com.estacaoqr.offline.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.estacaoqr.offline.config.ConfigTransferManager
import br.com.estacaoqr.offline.databinding.ActivitySettingsBinding
import br.com.estacaoqr.offline.kiosk.KioskController
import br.com.estacaoqr.offline.security.AdminSecurity
import br.com.estacaoqr.offline.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { AppSettings(this) }
    private val configTransfer by lazy { ConfigTransferManager(this) }
    private var bindingSwitches = false
    private var pendingExportPassword: String? = null
    private val createConfigFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null || password == null) return@registerForActivityResult
        exportConfig(uri, password)
    }
    private val openConfigFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::showImportDialog) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val owner = KioskController.isDeviceOwner(this)
        binding.deviceOwnerStatus.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ->
                "Android 4.4 detectado: tela cheia disponível; Lock Task não existe nesta versão."
            owner -> "Device Owner ativo: bloqueio total. Sem aviso de desafixar; a saída é feita somente por esta tela."
            else -> "Device Owner não configurado: o Android mostra o aviso \"deslize para cima para desafixar\" e permite sair por gesto. Configure o Device Owner para bloqueio total."
        }

        bindingSwitches = true
        binding.kioskSwitch.isChecked = settings.kioskEnabled
        binding.soundSwitch.isChecked = settings.soundEnabled
        bindingSwitches = false

        binding.kioskSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!bindingSwitches) {
                settings.kioskEnabled = enabled
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val started = KioskController.start(this)
                    toast(if (started) "Modo quiosque iniciado." else "Não foi possível iniciar o Lock Task.")
                } else if (!enabled) {
                    KioskController.stop(this)
                }
            }
        }
        binding.soundSwitch.setOnCheckedChangeListener { _, enabled -> settings.soundEnabled = enabled }
        binding.changePinButton.setOnClickListener { showChangePinDialog() }
        binding.exportConfigButton.setOnClickListener { showExportDialog() }
        binding.importConfigButton.setOnClickListener {
            openConfigFile.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
        binding.exitKioskButton.setOnClickListener { exitKiosk() }
        binding.backButton.setOnClickListener { finish() }
    }

    private fun showExportDialog() {
        val password = passwordField("Senha do arquivo")
        val confirm = passwordField("Confirmar senha")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(password, LinearLayout.LayoutParams(-1, dp(60)))
            addView(confirm, LinearLayout.LayoutParams(-1, dp(60)))
        }
        AlertDialog.Builder(this)
            .setTitle("Exportar configuração")
            .setMessage("A senha protege peças, revisoras e a chave dos cartões. Guarde-a em local seguro.")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Escolher local") { _, _ ->
                val value = password.text.toString()
                when {
                    value.length < 6 -> toast("A senha precisa ter pelo menos 6 caracteres.")
                    value != confirm.text.toString() -> toast("A confirmação da senha não confere.")
                    else -> {
                        pendingExportPassword = value
                        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
                        createConfigFile.launch("estacao-qr-config-$date.eqrconfig")
                    }
                }
            }.show()
    }

    private fun exportConfig(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { configTransfer.exportTo(uri, password) }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    toast("Configuração exportada: ${it.pieces} peça(s) e ${it.reviewers} revisora(s).")
                }.onFailure { toast("Falha ao exportar: ${it.message}") }
            }
        }
    }

    private fun showImportDialog(uri: Uri) {
        val password = passwordField("Senha do arquivo")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(password, LinearLayout.LayoutParams(-1, dp(60)))
        }
        AlertDialog.Builder(this)
            .setTitle("Importar configuração")
            .setMessage("MESCLAR preserva cadastros locais. SUBSTITUIR apaga as peças e revisoras atuais. A chave dos cartões será trocada pela chave do arquivo; use os cartões de revisora dessa configuração. Logs e PIN administrativo não são importados.")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Substituir") { _, _ -> importConfig(uri, password.text.toString(), true) }
            .setPositiveButton("Mesclar") { _, _ -> importConfig(uri, password.text.toString(), false) }
            .show()
    }

    private fun importConfig(uri: Uri, password: String, replace: Boolean) {
        if (password.length < 6) {
            toast("Informe a senha do arquivo, com pelo menos 6 caracteres.")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { configTransfer.importFrom(uri, password, replace) }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    refreshSettingsFromStore()
                    toast("Configuração importada: ${it.pieces} peça(s) e ${it.reviewers} revisora(s). Sessão encerrada.")
                }.onFailure { toast("Falha ao importar: ${it.message}") }
            }
        }
    }

    private fun refreshSettingsFromStore() {
        bindingSwitches = true
        binding.kioskSwitch.isChecked = settings.kioskEnabled
        binding.soundSwitch.isChecked = settings.soundEnabled
        bindingSwitches = false
    }

    private fun showChangePinDialog() {
        val newPin = pinField("Novo PIN")
        val confirm = pinField("Confirmar novo PIN")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(newPin, LinearLayout.LayoutParams(-1, dp(60)))
            addView(confirm, LinearLayout.LayoutParams(-1, dp(60)))
        }
        AlertDialog.Builder(this)
            .setTitle("Alterar PIN")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val pin = newPin.text.toString()
                when {
                    pin.length < 4 -> toast("O PIN precisa ter pelo menos 4 dígitos.")
                    pin != confirm.text.toString() -> toast("A confirmação não confere.")
                    else -> {
                        AdminSecurity(this).savePin(pin)
                        toast("PIN alterado.")
                    }
                }
            }.show()
    }

    private fun exitKiosk() {
        settings.kioskEnabled = false
        KioskController.stop(this)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAffinity()
    }

    private fun pinField(label: String) = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        maxLines = 1
    }
    private fun passwordField(label: String) = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        maxLines = 1
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
