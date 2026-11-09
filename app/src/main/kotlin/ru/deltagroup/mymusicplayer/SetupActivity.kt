package ru.deltagroup.mymusicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color

class SetupActivity : AppCompatActivity() {

    private lateinit var switchStorage: MaterialSwitch
    private lateinit var switchNotification: MaterialSwitch
    private lateinit var switchEnergy: MaterialSwitch

    private val permStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.POST_NOTIFICATIONS else null

    // Флаги для предотвращения ложных срабатываний при программном изменении свитча
    private var isUpdatingStorage = false
    private var isUpdatingNotification = false

    companion object {
        const val REQ_STORAGE = 101
        const val REQ_NOTIFICATION = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Если всё уже разрешено, мгновенно переходим в MainActivity
        if (areAllPermissionsGranted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_setup)
        
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        switchStorage = findViewById(R.id.switch_allow_storage)
        switchNotification = findViewById(R.id.switch_allow_notification)
        switchEnergy = findViewById(R.id.switch_allow_energy) // Убедитесь, что этот ID есть в XML
        val btnContinue = findViewById<MaterialButton>(R.id.btnContinue)

        updateAllStates()

        // --- ХРАНИЛИЩЕ: Защита от двойного клика ---
        switchStorage.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingStorage) return@setOnCheckedChangeListener

            if (isChecked) {
                val hasPerm = ContextCompat.checkSelfPermission(this, permStorage) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    isUpdatingStorage = true
                    switchStorage.isChecked = false // Визуально откатываем назад до ответа системы
                    isUpdatingStorage = false
                    ActivityCompat.requestPermissions(this, arrayOf(permStorage), REQ_STORAGE)
                }
            }
        }

        // --- УВЕДОМЛЕНИЯ: Защита от двойного клика ---
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingNotification) return@setOnCheckedChangeListener

            if (isChecked && permNotification != null) {
                val hasPerm = ContextCompat.checkSelfPermission(this, permNotification) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    isUpdatingNotification = true
                    switchNotification.isChecked = false
                    isUpdatingNotification = false
                    ActivityCompat.requestPermissions(this, arrayOf(permNotification), REQ_NOTIFICATION)
                }
            }
        }

        // --- ЭНЕРГОСБЕРЕЖЕНИЕ: Универсальный способ для Android 8–15+ ---
        // Мы не используем setOnCheckedChangeListener, так как мы не меняем свитч сами, 
        // а открываем настройки. Свитч обновится автоматически в onResume.
        switchEnergy.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            // Подсказка пользователю, что именно нужно сделать
            Toast.makeText(this, "Зайдите в раздел 'Батарея' и выберите 'Без ограничений'", Toast.LENGTH_LONG).show()
        }

        btnContinue.setOnClickListener {
            val hasStorage = ContextCompat.checkSelfPermission(this, permStorage) == PackageManager.PERMISSION_GRANTED
            val hasNotification = permNotification == null || ContextCompat.checkSelfPermission(this, permNotification) == PackageManager.PERMISSION_GRANTED
        
            if (hasStorage && hasNotification) {
                // Обязательные разрешения получены, запускаем плеер
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                // Блокируем переход, если не хватает обязательных прав
                if (!hasStorage) {
                    Toast.makeText(this, "Для работы приложения необходим доступ к музыке", Toast.LENGTH_SHORT).show()
                } else if (!hasNotification) {
                    Toast.makeText(this, "Для управления плеером необходимы уведомления", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Обновляем состояние при возврате из настроек телефона
    override fun onResume() {
        super.onResume()
        updateAllStates()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            REQ_STORAGE -> {
                if (isGranted) {
                    switchStorage.isChecked = true
                    switchStorage.isEnabled = false // Блокируем, т.к. разрешение получено
                } else {
                    switchStorage.isChecked = false
                    Toast.makeText(this, "Без доступа к музыке приложение не сможет работать", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_NOTIFICATION -> {
                if (isGranted) {
                    switchNotification.isChecked = true
                    switchNotification.isEnabled = false
                } else {
                    switchNotification.isChecked = false
                }
            }
        }
    }

    private fun updateAllStates() {
        // 1. Хранилище
        val isStorageGranted = ContextCompat.checkSelfPermission(this, permStorage) == PackageManager.PERMISSION_GRANTED
        switchStorage.isChecked = isStorageGranted
        switchStorage.isEnabled = !isStorageGranted

        // 2. Уведомления
        val isNotifGranted = permNotification == null || ContextCompat.checkSelfPermission(this, permNotification) == PackageManager.PERMISSION_GRANTED
        switchNotification.isChecked = isNotifGranted
        switchNotification.isEnabled = !isNotifGranted

        // 3. Энергосбережение
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isEnergyIgnored = pm.isIgnoringBatteryOptimizations(packageName)
        switchEnergy.isChecked = isEnergyIgnored
        switchEnergy.isEnabled = !isEnergyIgnored
        // Энергосбережение блокируем (isEnabled = true), чтобы пользователь мог зайти в настройки снова, если нужно
    }

    private fun areAllPermissionsGranted(): Boolean {
        val hasStorage = ContextCompat.checkSelfPermission(this, permStorage) == PackageManager.PERMISSION_GRANTED
        val hasNotification = permNotification == null || ContextCompat.checkSelfPermission(this, permNotification) == PackageManager.PERMISSION_GRANTED
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasEnergy = pm.isIgnoringBatteryOptimizations(packageName)
        
        return hasStorage && hasNotification && hasEnergy
    }
}