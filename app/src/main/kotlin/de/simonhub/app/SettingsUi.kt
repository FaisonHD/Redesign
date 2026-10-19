@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package de.simonhub.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simonhub.core.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable private fun SettingSwitch(title: String,body: String,checked: Boolean,change: (Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Column(Modifier.weight(1f)) {Text(title,fontSize=14.sp);Text(body,Modifier.padding(top=4.dp),fontSize=11.sp,lineHeight=17.sp,color=muted())};Switch(checked,change)}
}
@Composable internal fun SettingsScreen(vm: HubViewModel,s: HubState,customize: ()->Unit) {
    val c=LocalContext.current;val calendars by vm.calendars.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var bridgeDialog by remember {mutableStateOf(false)};var pendingImport by remember {mutableStateOf<Uri?>(null)}
    var name by remember(s.settings.name) {mutableStateOf(s.settings.name)};var start by remember(s.settings.startHour) {mutableStateOf(s.settings.startHour.toString())};var end by remember(s.settings.endHour) {mutableStateOf(s.settings.endHour.toString())}
    var homeAppCount by remember(s.settings.homeAppCount) {mutableIntStateOf(s.settings.homeAppCount)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->vm.launch {
        if(allowed) {val choices=c.hub.calendar.calendars();vm.repository.settings {it.copy(calendarEnabled=true,calendarIds=if(it.calendarIds.isEmpty())choices.map {cal->cal.id}.toSet()else it.calendarIds)};vm.refresh()}
        else vm.notices.emit("Kalenderzugriff wurde nicht freigegeben.")
    }}
    val vault=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->if(uri!=null)vm.launch {c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.connectVault(uri)}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null)vm.launch {
        val content=vm.exportBackup();withContext(Dispatchers.IO) {c.contentResolver.openOutputStream(uri,"wt")?.use {it.write(content.toByteArray(Charsets.UTF_8))} ?: error("Backup konnte nicht gespeichert werden.")};vm.notices.emit("Backup ohne Zugangsdaten exportiert.")
    }}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->pendingImport=uri}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,28.dp,20.dp,36.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Column {Heading("Ganz deiner","Dein Hub.","So persönlich wie dein Alltag.")}}
        item {HubCard {
            CardHeader("Persönlich & anpassbar",Icons.Outlined.Tune);Spacer(Modifier.height(15.dp))
            OutlinedTextField(name,{name=it.take(40)},Modifier.fillMaxWidth(),label={Text("Dein Name")},singleLine=true,trailingIcon={IconButton(onClick={if(name.isNotBlank())vm.settings {it.copy(name=name.trim())}}) {Icon(Icons.Outlined.Check,"Name speichern")}})
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("dark" to "Dunkel","light" to "Hell","system" to "System").forEach {(key,label)->FilterChip(selected=s.settings.theme==key,onClick={vm.settings {it.copy(theme=key)}},label={Text(label)})}}
            Text("Deine Akzentfarbe",Modifier.padding(top=6.dp),fontSize=12.sp,color=muted())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(Triple("mono","Koralle",Color(0xFFD97757)),Triple("sage","Salbei",Color(0xFFBCDDA9)),Triple("lavender","Lavendel",Color(0xFFD0BFFD)),Triple("sky","Himmel",Color(0xFFA8D7EB))).forEach {(key,label,color)->FilterChip(selected=s.settings.accent==key,onClick={vm.settings {it.copy(accent=key)}},label={Text(label,fontSize=11.sp)},leadingIcon={Box(Modifier.size(10.dp).clip(CircleShape).background(color))})}}
            Text("Maximale App-Anzahl auf Home · Apps direkt auf Home auswählen",Modifier.padding(top=7.dp),fontSize=11.sp,color=muted())
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Slider(value=homeAppCount.toFloat(),onValueChange={homeAppCount=it.roundToInt().coerceIn(4,64)},valueRange=4f..64f,steps=14,onValueChangeFinished={vm.settings {it.copy(homeAppCount=homeAppCount)}},modifier=Modifier.weight(1f))
                Text("$homeAppCount",Modifier.width(34.dp),fontSize=14.sp,fontWeight=FontWeight.Medium)
            }
            OutlinedButton(onClick=customize,Modifier.fillMaxWidth()) {Icon(Icons.Outlined.DashboardCustomize,null,Modifier.size(17.dp));Text("Dashboard gestalten",Modifier.padding(start=8.dp))}
        }}
        item {HubCard {
            CardHeader("Deine Tageszeiten",Icons.Outlined.Schedule);Text("Hier sucht dein Hub passende Fokusfenster.",Modifier.padding(top=12.dp),fontSize=12.sp,color=muted())
            Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {OutlinedTextField(start,{start=it.filter(Char::isDigit).take(2)},Modifier.weight(1f),label={Text("Start · Stunde")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(end,{end=it.filter(Char::isDigit).take(2)},Modifier.weight(1f),label={Text("Ende · Stunde")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)}
            TextButton(onClick={val a=start.toIntOrNull();val b=end.toIntOrNull();if(a!=null && b!=null && a in 0..23 && b in 1..24 && a<b)vm.settings {it.copy(startHour=a,endHour=b)}else vm.launch {vm.notices.emit("Start 0–23, Ende 1–24; Start muss vor Ende liegen.")}}) {Text("Tageszeiten speichern")}
        }}
        item {HubCard {
            CardHeader("Google Calendar",Icons.Outlined.CalendarMonth);Spacer(Modifier.height(12.dp))
            SettingSwitch("Kalender lesen","Auf deinem Handy synchronisierte Kalender.",s.settings.calendarEnabled) {if(it)permission.launch(Manifest.permission.READ_CALENDAR)else vm.settings {it.copy(calendarEnabled=false)}}
            if(s.settings.calendarEnabled) {
                if(calendars.isEmpty())Text("Keine Kalender gefunden. Google-Konto und Kalendersynchronisierung in Android prüfen.",fontSize=12.sp,color=muted())
                calendars.forEach {cal->Row(Modifier.fillMaxWidth().clickable {vm.settings {old->old.copy(calendarIds=if(cal.id in old.calendarIds)old.calendarIds-cal.id else old.calendarIds+cal.id)}},verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(cal.id in s.settings.calendarIds,{checked->vm.settings {old->old.copy(calendarIds=if(checked)old.calendarIds+cal.id else old.calendarIds-cal.id)}})
                    Column {Text(cal.name,fontSize=13.sp);Text(cal.account,fontSize=10.sp,color=muted())}
                }}
            }
            Text("Google Primary und Rapla/DHBW müssen im Android-Kalender verfügbar sein. Termine werden nur gelesen.",Modifier.padding(top=10.dp),fontSize=11.sp,lineHeight=17.sp,color=muted())
        }}
        item {HubCard {
            CardHeader("Obsidian · Second Brain",Icons.Outlined.AutoStories)
            Text(if(s.settings.obsidianTree.isBlank())"Noch kein Ordner verbunden."else "Ordner freigegeben${s.obsidianSyncedAt?.let {" · "+it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))} ?: " · Import offen"}",Modifier.padding(top=12.dp),fontSize=12.sp,color=muted())
            Text("Wähle einen lokal verfügbaren Vault oder Unterordner. Markdown wird als Offline-Lesekopie importiert. Dateien werden nicht verändert.",Modifier.padding(vertical=10.dp),fontSize=12.sp,lineHeight=18.sp,color=muted())
            SettingSwitch("Aufgaben übernehmen","Checkboxen kopieren; 📅 JJJJ-MM-TT wird als belegtes Datum gelesen. Abhaken bleibt lokal.",s.settings.importNoteTasks) {enabled->vm.settings {it.copy(importNoteTasks=enabled)}}
            Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)) {OutlinedButton(onClick={vault.launch(null)},Modifier.weight(1f)) {Icon(Icons.Outlined.FolderOpen,null,Modifier.size(16.dp));Text("Ordner",Modifier.padding(start=6.dp),fontSize=12.sp)};Button(onClick=vm::syncVault,enabled=s.settings.obsidianTree.isNotBlank() && !busy,modifier=Modifier.weight(1f)) {Text(if(busy)"Lädt…"else"Importieren",fontSize=12.sp)}}
            if(s.settings.obsidianTree.isNotBlank())TextButton(onClick={vm.settings {it.copy(obsidianTree="")}}) {Text("Ordner trennen")}
        }}
        item {HubCard {
            CardHeader("ChatGPT · MCP",Icons.Outlined.AutoAwesome)
            Text(if(!s.settings.bridgeEnabled)"Noch nicht verbunden"else if(s.bridgeSyncedAt==null)"Eingerichtet · noch nicht getestet"else "Synchronisiert · "+s.bridgeSyncedAt!!.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")),Modifier.padding(top=12.dp),fontSize=13.sp,color=if(s.settings.bridgeEnabled && s.bridgeSyncedAt!=null)MaterialTheme.colorScheme.primary else muted())
            Text("Über deine eigene Bridge liest ChatGPT deinen Tageskontext und schlägt Aufgaben, Zieländerungen und Pläne vor. Vorschläge prüfst du vor dem Übernehmen.",Modifier.padding(top=10.dp),fontSize=12.sp,lineHeight=18.sp,color=muted())
            Text("Bei aktivierter Verbindung werden Aufgaben, ausgewählte Termine, Ziele und Vorschautexte von bis zu 20 Notizen übertragen. Abgleich manuell und ungefähr alle 15 Minuten im Hintergrund; unter Energiesparen später.",Modifier.padding(vertical=10.dp),fontSize=11.sp,lineHeight=17.sp,color=muted())
            OutlinedButton(onClick={bridgeDialog=true},Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Link,null,Modifier.size(17.dp));Text("MCP-Bridge einrichten",Modifier.padding(start=8.dp))}
            if(s.settings.bridgeEnabled)Button(onClick={vm.refresh(true)},Modifier.fillMaxWidth(),enabled=!busy) {Text("Jetzt synchronisieren")}
            TextButton(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,vm.shareContext()),"Tageskontext mit ChatGPT teilen"))}) {Icon(Icons.Outlined.IosShare,null,Modifier.size(16.dp));Text("Tageskontext teilen",Modifier.padding(start=8.dp))}
            Text("Ohne Bridge bleibt die lokale Planung verfügbar. ChatGPT-Verbindungen aus diesem Chat werden nicht automatisch übernommen.",fontSize=11.sp,lineHeight=17.sp,color=muted())
        }}
        if(s.pendingActions.isNotEmpty())item {HubCard {
            CardHeader("Vorschläge prüfen",Icons.Outlined.RateReview)
            s.pendingActions.forEach {a->
                val data=runCatching {JSONObject(a.json).getJSONObject("data")}.getOrNull()
                val label=when(a.type) {"create_task"->"Neue Aufgabe";"complete_task"->"Aufgabe erledigen";"set_goal"->"Ziel aktualisieren";"set_plan"->"Tagesplan übernehmen";else->"Unbekannter Vorschlag"}
                Text(label,Modifier.padding(top=18.dp),fontSize=14.sp,fontWeight=FontWeight.Medium)
                val detail=when(a.type) {"complete_task"->s.tasks.find {it.id==data?.optString("id")}?.title ?: "Aufgabe nicht mehr vorhanden";"set_plan"->"${data?.optString("date")} · ${data?.optJSONArray("blocks")?.length() ?: 0} Zeitblöcke\n${data?.optString("explanation") ?: ""}";"set_goal"->"${data?.optString("title")} · ${data?.optDouble("current")} / ${data?.optDouble("target")}";else->data?.optString("title") ?: ""}
                Text(detail,Modifier.padding(top=5.dp),fontSize=12.sp,lineHeight=18.sp,color=muted())
                if(a.type=="set_plan")data?.optJSONArray("blocks")?.let {blocks->(0 until blocks.length()).take(20).forEach {i->val b=blocks.getJSONObject(i);Text("${b.optString("start")} · ${b.optString("title")}",Modifier.padding(top=4.dp),fontSize=11.sp,color=muted())}}
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {TextButton(onClick={vm.decideAction(a.id,false)}) {Text("Verwerfen")};Button(onClick={vm.decideAction(a.id,true)}) {Text("Übernehmen")}}
            }
        }}
        item {HubCard {
            CardHeader("Module",Icons.Outlined.Extension)
            PluginRegistry().all().forEach {p->Row(Modifier.padding(top=16.dp)) {Column(Modifier.weight(1f)) {Text(p.name,fontSize=13.sp);Text(p.description,Modifier.padding(top=3.dp),fontSize=11.sp,color=muted())};Text("Integriert",fontSize=10.sp,color=MaterialTheme.colorScheme.primary)}}
        }}
        item {HubCard {
            CardHeader("Deine Daten",Icons.Outlined.Shield);Text("Lokal gespeichert. Zugangsdaten sind verschlüsselt und werden nicht exportiert.",Modifier.padding(vertical=12.dp),fontSize=12.sp,lineHeight=18.sp,color=muted())
            OutlinedButton(onClick={export.launch("SimonHub_Backup_${LocalDate.now()}.json")},Modifier.fillMaxWidth()) {Icon(Icons.Outlined.FileDownload,null,Modifier.size(17.dp));Text("Backup exportieren",Modifier.padding(start=8.dp))}
            TextButton(onClick={import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.fillMaxWidth()) {Text("Backup wiederherstellen")}
            if(s.demo)TextButton(onClick=vm::removeDemo,Modifier.fillMaxWidth()) {Text("Beispieldaten entfernen")}else if(s.tasks.isEmpty() && s.goals.isEmpty() && s.notes.isEmpty())TextButton(onClick=vm::loadDemo,Modifier.fillMaxWidth()) {Text("Mit Beispieldaten ausprobieren")}
        }}
        item {Text("SIMON HUB · "+BuildConfig.VERSION_NAME+"\nDein Alltag, an einem Ort.",Modifier.padding(top=8.dp),fontSize=10.sp,lineHeight=17.sp,color=muted())}
    }
    if(bridgeDialog)BridgeDialog(s.settings,vm,{bridgeDialog=false})
    if(pendingImport!=null)AlertDialog(onDismissRequest={pendingImport=null},title={Text("Backup wiederherstellen?")},text={Text("Ersetzt Aufgaben, Ziele, Notizkopien und Layout. Exportiere vorher ein Backup, wenn du den aktuellen Stand behalten möchtest. Verbindungen werden deaktiviert.")},confirmButton={TextButton(onClick={vm.restoreBackup(pendingImport!!);pendingImport=null}) {Text("Wiederherstellen")}},dismissButton={TextButton(onClick={pendingImport=null}) {Text("Abbrechen")}})
}
@Composable private fun BridgeDialog(s: HubSettings,vm: HubViewModel,dismiss: ()->Unit) {
    var url by remember {mutableStateOf(s.bridgeUrl)};var token by remember {mutableStateOf("")};var enabled by remember {mutableStateOf(s.bridgeEnabled)};var error by remember {mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            Text("Mit ChatGPT verbunden.",fontSize=24.sp,fontWeight=FontWeight.Medium)
            Text("Die mitgelieferte Simon-Hub-Bridge auf deinem Server einrichten. Die App verwendet die Basisadresse; dein MCP-Client verbindet sich mit /mcp. ChatGPT-Zugriff hängt von den Funktionen deines Kontos ab.",fontSize=12.sp,lineHeight=18.sp,color=muted())
            OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("Bridge-Adresse")},placeholder={Text("https://hub.example.de")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("App-Token")},supportingText={Text(if(s.bridgeUrl==url && url.isNotBlank())"Leer lassen, um das gespeicherte Token zu behalten."else"Mindestens 32 Zeichen. Verschlüsselt auf deinem Gerät.")},visualTransformation=PasswordVisualTransformation(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            SettingSwitch("Verbindung aktivieren","Kontext übertragen und Vorschläge aus ChatGPT abholen.",enabled,{enabled=it})
            if(error!=null)Text(error!!,fontSize=12.sp,color=MaterialTheme.colorScheme.error)
            Button(onClick={val valid=runCatching {
                if(enabled) {val u=java.net.URI(url.trim());require(u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null && u.query==null && u.fragment==null) {"Bitte eine direkte HTTPS-Adresse eingeben."};require(token.length>=32 || (token.isBlank() && url.trim().trimEnd('/')==s.bridgeUrl && s.bridgeUrl.isNotBlank())) {"Bitte ein Token mit mindestens 32 Zeichen eingeben."}}
            };if(valid.isSuccess) {vm.saveBridge(url,token,enabled);dismiss()}else error=valid.exceptionOrNull()?.message},Modifier.fillMaxWidth().height(50.dp)) {Text("Verbindung speichern")}
        }
    }
}

