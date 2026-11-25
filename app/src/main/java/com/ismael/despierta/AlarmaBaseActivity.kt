package com.ismael.despierta

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

/** Pantallas de la alarma sonando: con volumen forzado, las teclas de volumen no hacen nada. */
open class AlarmaBaseActivity : AppCompatActivity() {
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) &&
            Prefs.forzarVolumen(this)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
