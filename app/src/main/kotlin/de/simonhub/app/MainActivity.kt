package de.simonhub.app
import android.content.Intent
import android.os.Bundle
import androidx.activity.*
import androidx.activity.compose.setContent
class MainActivity: ComponentActivity() {
    private val model: HubViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        val shared=if(intent.action==Intent.ACTION_SEND)intent.getStringExtra(Intent.EXTRA_TEXT)?.take(240)else null
        setContent {SimonHub(model,shared)}
    }
    override fun onResume() {super.onResume();model.refreshLauncherApps();if(model.ready.value) {model.refresh();model.refreshWeather()}}
}
