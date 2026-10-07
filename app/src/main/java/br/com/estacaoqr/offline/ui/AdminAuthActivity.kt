package br.com.estacaoqr.offline.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.estacaoqr.offline.databinding.ActivityAdminAuthBinding
import br.com.estacaoqr.offline.security.AdminSecurity

class AdminAuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminAuthBinding
    private val security by lazy { AdminSecurity(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val setup = !security.hasPin()
        if (setup) {
            binding.authTitle.text = "CRIAR PIN ADMINISTRATIVO"
            binding.authHelp.text = "Primeiro acesso: crie um PIN numérico com pelo menos 4 dígitos."
            binding.confirmPinInput.visibility = View.VISIBLE
            binding.authButton.text = "SALVAR PIN"
        }

        binding.authButton.setOnClickListener {
            val pin = binding.pinInput.text.toString()
            if (setup) createPin(pin) else authenticate(pin)
        }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun createPin(pin: String) {
        when {
            pin.length < 4 -> toast("O PIN precisa ter pelo menos 4 dígitos.")
            pin != binding.confirmPinInput.text.toString() -> toast("A confirmação do PIN não confere.")
            else -> {
                security.savePin(pin)
                openAdmin()
            }
        }
    }

    private fun authenticate(pin: String) {
        if (security.verifyPin(pin)) openAdmin() else {
            binding.pinInput.text?.clear()
            toast("PIN incorreto.")
        }
    }

    private fun openAdmin() {
        startActivity(Intent(this, AdminActivity::class.java))
        finish()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
