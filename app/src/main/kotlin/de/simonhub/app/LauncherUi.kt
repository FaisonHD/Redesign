@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package de.simonhub.app

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import de.simonhub.core.HubSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppCategory { SOCIAL, FINANCE, SHOPPING, MESSAGING, MEDIA, MUSIC, EDUCATION, FITNESS, SPORTS, TRAVEL, SECURITY, PRODUCTIVITY, UTILITIES, OTHER }
private data class LauncherApp(val title:String,val packageName:String,val activityName:String,val category:AppCategory)

@Composable internal fun LauncherHome(vm: HubViewModel,appsScrollState:LazyGridState=rememberLazyGridState(),onEditingChange:(Boolean)->Unit={},onToday: ()->Unit,onAllApps: ()->Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val city by vm.weatherCity.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val weatherBusy by vm.weatherBusy.collectAsStateWithLifecycle()
    val weatherError by vm.weatherError.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val todayEvents by vm.homeEvents.collectAsStateWithLifecycle()
    val appRevision by vm.launcherAppsRevision.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val haptic=LocalHapticFeedback.current
    var apps by remember(context) {mutableStateOf(emptyList<LauncherApp>())}
    LaunchedEffect(context,appRevision) {apps=withContext(Dispatchers.IO) {installedApps(context)}}
    var cityDialog by rememberSaveable {mutableStateOf(false)}
    var appPicker by rememberSaveable {mutableStateOf(false)}
    var homeOptions by rememberSaveable {mutableStateOf(false)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var draftOrder by rememberSaveable(stateSaver=listSaver<List<String>,String>(save={it},restore={it})) {mutableStateOf(emptyList<String>())}
    var dragging by remember {mutableStateOf<String?>(null)}
    var dragPoint by remember {mutableStateOf(Offset.Zero)}
    var dragOffset by remember {mutableStateOf(Offset.Zero)}
    val tileBounds=remember {mutableMapOf<String,Rect>()}
    LaunchedEffect(editing,homeOptions,appPicker) {onEditingChange(editing || homeOptions || appPicker)}
    DisposableEffect(Unit) {onDispose {onEditingChange(false)}}
    fun finishEditing() {vm.settings {it.copy(homeAppOrder=draftOrder)};editing=false;dragging=null}
    BackHandler(enabled=editing) {finishEditing()}
    var cityError by rememberSaveable {mutableStateOf<String?>(null)}
    val primary=MaterialTheme.colorScheme.onBackground
    val subtle=MaterialTheme.colorScheme.onSurfaceVariant
    val visibleApps=state.settings.homeAppPackages?.mapNotNull {packageName->apps.firstOrNull {it.packageName==packageName}}?.take(state.settings.homeAppCount) ?: apps.take(state.settings.homeAppCount)
    val order=if(editing)draftOrder else state.settings.homeAppOrder
    val orderedApps=(order.mapNotNull {id->visibleApps.firstOrNull {it.packageName==id}}+visibleApps.filter {it.packageName !in order})
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact=maxHeight<740.dp || LocalDensity.current.fontScale>1.2f
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=22.dp).testTag("launcher-home")) {
        Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SIMON HUB",color=subtle,fontSize=10.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp)
                Text(now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.GERMAN)).replaceFirstChar {it.titlecase(Locale.GERMAN)},Modifier.padding(top=4.dp),color=primary,fontSize=if(compact)16.sp else 18.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick={homeOptions=true},modifier=Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).testTag("home-customize")) {
                Icon(Icons.Outlined.Tune,"Home anpassen",Modifier.size(20.dp),tint=primary)
            }
        }
        state.settings.homeSectionOrder.forEach {section->when(section) {
        "info"->{
            if(state.settings.homeShowClock || state.settings.homeShowWeather)Surface(
                Modifier.fillMaxWidth().padding(top=8.dp,bottom=10.dp),shape=RoundedCornerShape(24.dp),
                color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(if(compact)12.dp else 14.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(state.settings.homeShowClock)Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        PixelClock(now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),compact=compact)
                        Text("DEINE ZEIT",fontSize=9.sp,lineHeight=12.sp,letterSpacing=1.5.sp,color=subtle)
                    }
                    if(state.settings.homeShowClock && state.settings.homeShowWeather)Box(Modifier.padding(horizontal=12.dp).width(1.dp).height(66.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    if(state.settings.homeShowWeather)Column(Modifier.weight(1f).testTag("home-weather"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(weather?.let {weatherIcon(it.code)} ?: Icons.Outlined.WbSunny,null,Modifier.size(27.dp),tint=primary)
                            Text(weather?.let {it.temperature.toString()+"°"} ?: if(weatherBusy)"···"else"--°",Modifier.padding(start=6.dp),fontSize=if(compact)26.sp else 30.sp,lineHeight=if(compact)30.sp else 36.sp,fontWeight=FontWeight.Light)
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(weather?.city ?: city,Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable {cityError=null;cityDialog=true}.padding(vertical=5.dp),fontSize=12.sp,lineHeight=16.sp,color=primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                            IconButton(onClick={vm.refreshWeather(true)},enabled=!weatherBusy,modifier=Modifier.size(32.dp).testTag("weather-refresh")) {
                                if(weatherBusy)CircularProgressIndicator(Modifier.size(12.dp),strokeWidth=1.5.dp)
                                else Icon(Icons.Outlined.Refresh,"Wetter aktualisieren",Modifier.size(16.dp),tint=subtle)
                            }
                        }
                        if(!compact)Text(weather?.let {weatherDescription(it.code)} ?: if(weatherBusy)"Wetter lädt"else"Wetter nicht verfügbar",fontSize=10.sp,lineHeight=14.sp,color=subtle,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(!compact)Text(weather?.let {"↑ "+it.high+"°  ↓ "+it.low+"°"} ?: "Tippe auf den Ort",fontSize=10.sp,lineHeight=14.sp,color=subtle,maxLines=1)
                        if(weather!=null && (weatherError!=null || java.time.Duration.between(weather!!.fetchedAt,now).toHours()>=2))Text("Zuletzt "+time(weather!!.fetchedAt),fontSize=9.sp,color=subtle)
                    }
                }
            }
        }
        "calendar"->{if(state.settings.homeShowCalendar)HomeTodayCalendar(homeAgenda(todayEvents,now),state.settings.calendarEnabled,now,compact,onToday)}
        "apps"->Column(Modifier.weight(1f).fillMaxWidth()) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("APPS",Modifier.weight(1f),color=subtle,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp)
            if(editing)TextButton(onClick={finishEditing()},modifier=Modifier.testTag("home-edit-done")) {Text("Fertig")}
            else TextButton(onClick={draftOrder=orderedApps.map {it.packageName};editing=true},modifier=Modifier.testTag("home-edit-apps")) {Text("Sortieren",fontSize=10.sp)}
            IconButton(onClick={appPicker=true},modifier=Modifier.size(44.dp).testTag("home-app-picker-button")) {Icon(Icons.Outlined.Edit,"Home-Apps auswählen",Modifier.size(16.dp),tint=subtle)}
            TextButton(onClick=onAllApps,contentPadding=PaddingValues(2.dp),modifier=Modifier.testTag("open-app-drawer")) {Text("Alle Apps",fontSize=12.sp,color=subtle)}
        }
        if(apps.isEmpty() || visibleApps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {Text(if(apps.isEmpty())"Apps werden geladen …"else"Keine Home-Apps ausgewählt",fontSize=12.sp,color=subtle)}
        } else LazyVerticalGrid(
            columns=GridCells.Fixed(state.settings.homeColumns),modifier=Modifier.weight(1f).fillMaxWidth().testTag("home-app-grid")
                .then(if(editing)Modifier.pointerInput(editing) {
                    fun appAt(point:Offset)=appsScrollState.layoutInfo.visibleItemsInfo.firstOrNull {item->
                        point.x>=item.offset.x && point.x<item.offset.x+item.size.width &&
                        point.y>=item.offset.y && point.y<item.offset.y+item.size.height
                    }?.key as? String
                    detectDragGestures(
                        onDragStart={point->dragging=appAt(point);dragPoint=point;dragOffset=Offset.Zero},
                        onDragEnd={dragging=null;dragOffset=Offset.Zero},
                        onDragCancel={dragging=null;dragOffset=Offset.Zero},
                        onDrag={change,delta->
                            val id=dragging
                            if(id!=null) {
                                change.consume();dragPoint+=delta;dragOffset+=delta
                                val target=appAt(dragPoint)
                                if(target!=null && target!=id) {
                                    val next=draftOrder.toMutableList()
                                    val from=next.indexOf(id);val to=next.indexOf(target)
                                    if(from>=0 && to>=0) {next.add(to,next.removeAt(from));draftOrder=next;dragOffset=Offset.Zero}
                                }
                            }
                        }
                    )
                }else Modifier),state=appsScrollState,userScrollEnabled=!editing,
            contentPadding=PaddingValues(top=if(compact)2.dp else 6.dp,bottom=if(compact)2.dp else 6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            items(orderedApps,key={it.packageName}) {app->
                val id=app.packageName
                val dragModifier=Modifier.onGloballyPositioned {tileBounds[id]=it.boundsInRoot()}
                    .zIndex(if(dragging==id)1f else 0f).graphicsLayer {
                        if(dragging==id) {translationX=dragOffset.x;translationY=dragOffset.y;scaleX=1.08f;scaleY=1.08f}
                    }
                AppTile(app,dragModifier,state.settings.homeColumns,state.settings.homeShowLabels,editing,compact=compact,
                    onLongClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);draftOrder=orderedApps.map {it.packageName};editing=true},
                    onClick={if(!editing)launchApp(context,app)})
            }
        }
        }
        }}
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick=onToday).padding(vertical=if(compact)8.dp else 12.dp).testTag("open-today-page"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(primary,androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.padding(start=5.dp,end=12.dp).size(5.dp).background(subtle.copy(alpha=.35f),androidx.compose.foundation.shape.CircleShape))
            Text("Monatsübersicht",fontSize=12.sp,lineHeight=16.sp,color=subtle)
            Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.padding(start=4.dp).size(18.dp),tint=subtle)
        }
    }
    }
    if(homeOptions)HomeLayoutEditor(state.settings,{homeOptions=false}) {settings->vm.settings {it.copy(homeAppCount=settings.homeAppCount,homeColumns=settings.homeColumns,homeShowLabels=settings.homeShowLabels,homeShowClock=settings.homeShowClock,homeShowWeather=settings.homeShowWeather,homeShowCalendar=settings.homeShowCalendar,homeSectionOrder=settings.homeSectionOrder)};homeOptions=false}
    if(cityDialog) {
        var proposed by remember(city) {mutableStateOf(city)}
        AlertDialog(
            onDismissRequest={cityDialog=false},
            title={Text("Wetter-Ort")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(proposed,{proposed=it.take(80)},modifier=Modifier.fillMaxWidth(),label={Text("Stadt oder Postleitzahl")},singleLine=true)
                Text("Wetterdaten: Open-Meteo. Es wird nur der eingetragene Ortsname gesendet; kein Standortzugriff.",fontSize=11.sp,lineHeight=16.sp,color=subtle)
                cityError?.let {Text(it,fontSize=11.sp,color=MaterialTheme.colorScheme.error)}
            }},
            confirmButton={
                TextButton(onClick={runCatching {vm.setWeatherCity(proposed)}.onSuccess {cityDialog=false}.onFailure {cityError=it.message ?: "Ort konnte nicht gespeichert werden."}}) {Text("Speichern")}
            },
            dismissButton={TextButton(onClick={cityDialog=false}) {Text("Abbrechen")}}
        )
    }
    if(appPicker)HomeAppPicker(apps,state.settings.homeAppCount,state.settings.homeAppPackages,{appPicker=false}) {selected->
        vm.settings {it.copy(homeAppPackages=selected)}
        appPicker=false
    }
}


@Composable internal fun HomeLayoutEditor(settings:HubSettings,dismiss:()->Unit,save:(HubSettings)->Unit) {
    var draft by remember {mutableStateOf(settings)}
    AlertDialog(
        onDismissRequest=dismiss,
        title={Text("Home anpassen")},
        text={Column(Modifier.verticalScroll(rememberScrollState()).testTag("home-layout-editor"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Anzeigen",fontWeight=FontWeight.SemiBold)
            HomeToggle("Uhrzeit",draft.homeShowClock,"home-toggle-clock") {draft=draft.copy(homeShowClock=it)}
            HomeToggle("Wetter",draft.homeShowWeather,"home-toggle-weather") {draft=draft.copy(homeShowWeather=it)}
            HomeToggle("Heutige Termine",draft.homeShowCalendar,"home-toggle-calendar") {draft=draft.copy(homeShowCalendar=it)}
            HomeToggle("App-Namen",draft.homeShowLabels,"home-toggle-labels") {draft=draft.copy(homeShowLabels=it)}
            HorizontalDivider()
            Text("Kachelgröße",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(5 to "Klein",4 to "Mittel",3 to "Groß").forEach {(columns,label)->
                    FilterChip(selected=draft.homeColumns==columns,onClick={draft=draft.copy(homeColumns=columns)},label={Text(label,fontSize=11.sp)},modifier=Modifier.testTag("home-columns-$columns"))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Home-Apps",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                IconButton(onClick={draft=draft.copy(homeAppCount=(draft.homeAppCount-4).coerceAtLeast(4))},enabled=draft.homeAppCount>4) {Icon(Icons.Outlined.Remove,"Weniger Home-Apps")}
                Text(draft.homeAppCount.toString(),fontSize=14.sp)
                IconButton(onClick={draft=draft.copy(homeAppCount=(draft.homeAppCount+4).coerceAtMost(64))},enabled=draft.homeAppCount<64) {Icon(Icons.Outlined.Add,"Mehr Home-Apps")}
            }
            Text("Bereiche anordnen",fontWeight=FontWeight.SemiBold)
            draft.homeSectionOrder.forEachIndexed {index,section->
                Row(Modifier.fillMaxWidth().testTag("home-section-$section"),verticalAlignment=Alignment.CenterVertically) {
                    Text(when(section) {"info"->"Uhr & Wetter";"calendar"->"Kalender";else->"Apps"},Modifier.weight(1f),fontSize=12.sp)
                    IconButton(onClick={val next=draft.homeSectionOrder.toMutableList();next.add(index-1,next.removeAt(index));draft=draft.copy(homeSectionOrder=next)},enabled=index>0,modifier=Modifier.size(40.dp).testTag("home-section-up-$section")) {Icon(Icons.Outlined.ArrowUpward,"Bereich nach oben",Modifier.size(18.dp))}
                    IconButton(onClick={val next=draft.homeSectionOrder.toMutableList();next.add(index+1,next.removeAt(index));draft=draft.copy(homeSectionOrder=next)},enabled=index<draft.homeSectionOrder.lastIndex,modifier=Modifier.size(40.dp).testTag("home-section-down-$section")) {Icon(Icons.Outlined.ArrowDownward,"Bereich nach unten",Modifier.size(18.dp))}
                }
            }
            Text("Apps auf Home lange drücken oder „Sortieren“ wählen, dann an die gewünschte Stelle ziehen.",fontSize=11.sp)
            TextButton(onClick={draft=draft.copy(homeColumns=4,homeShowLabels=true,homeShowClock=true,homeShowWeather=true,homeShowCalendar=true,homeSectionOrder=listOf("info","calendar","apps"))},modifier=Modifier.testTag("home-layout-reset")) {Text("Standardlayout")}
        }},
        confirmButton={TextButton(onClick={save(draft)},modifier=Modifier.testTag("home-layout-save")) {Text("Speichern")}},
        dismissButton={TextButton(onClick=dismiss) {Text("Abbrechen")}}
    )
}
@Composable private fun HomeToggle(label:String,checked:Boolean,tag:String,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),fontSize=12.sp)
        Switch(checked=checked,onCheckedChange=change,modifier=Modifier.testTag(tag))
    }
}

@Composable private fun HomeTodayCalendar(events:List<de.simonhub.core.HubEvent>,enabled:Boolean,now:Instant,compact:Boolean,onOpen:()->Unit) {
    val visibleCount=if(compact)3 else 4
    Column(Modifier.fillMaxWidth().testTag("home-today-calendar").padding(vertical=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("HEUTE",Modifier.weight(1f),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp)
            if(events.isNotEmpty())Text(events.size.toString()+if(events.size==1)" Termin"else" Termine",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick=onOpen,modifier=Modifier.size(if(compact)32.dp else 40.dp)) {Icon(Icons.Outlined.CalendarMonth,"Monatskalender öffnen",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        if(events.isEmpty()) {
            Text(if(enabled)"Keine Termine heute"else"Kalender verbinden",Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onOpen).padding(vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else events.take(visibleCount).forEach {event->
            val running=!event.allDay && event.start<=now && event.end>now
            val past=!event.allDay && event.end<=now
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(running)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick=onOpen).padding(horizontal=5.dp,vertical=if(compact)4.dp else 7.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(event.allDay)"Ganztags"else time(event.start),Modifier.width(if(compact)54.dp else 60.dp),fontSize=11.sp,lineHeight=16.sp,color=if(running)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontFamily=FontFamily.Monospace)
                Text(event.title,Modifier.weight(1f),fontSize=if(compact)12.sp else 13.sp,lineHeight=18.sp,color=if(past)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,fontWeight=if(running)FontWeight.SemiBold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(running)Text("Jetzt",Modifier.padding(start=5.dp),fontSize=10.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.primary)
            }
        }
        if(events.size>visibleCount)Text("+ "+(events.size-visibleCount)+" weitere Termine",Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick=onOpen).padding(start=5.dp,top=5.dp,bottom=5.dp),fontSize=11.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeAppPicker(apps:List<LauncherApp>,maximum:Int,saved:Set<String>?,dismiss:()->Unit,save:(Set<String>)->Unit) {
    var selected by remember(apps,maximum,saved) {mutableStateOf((saved ?: apps.take(maximum).map {it.packageName}.toSet()).toSet())}
    var query by rememberSaveable {mutableStateOf("")}
    val visible=remember(apps,query) {apps.filter {matchesAppSearch(it.title,it.packageName,query)}}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max=620.dp).imePadding().padding(horizontal=22.dp).padding(bottom=16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Apps auf Home",Modifier.weight(1f),fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"App-Auswahl schließen")}
            }
            Text(selected.size.toString()+" / "+maximum+" ausgewählt",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp).testTag("home-picker-search"),placeholder={Text("Apps suchen",fontSize=14.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=380.dp).testTag("home-app-picker-list")) {
                if(visible.isEmpty())item {Text(if(apps.isEmpty())"Keine startbaren Apps gefunden."else"Keine Treffer",Modifier.padding(vertical=16.dp),fontSize=13.sp)}
                items(visible,key={it.packageName}) {app->
                    val checked=app.packageName in selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        selected=if(checked)selected-app.packageName else if(selected.size<maximum)selected+app.packageName else selected
                    }.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=checked,onCheckedChange={on->selected=if(on && selected.size<maximum)selected+app.packageName else if(!on)selected-app.packageName else selected})
                        Text(app.title,Modifier.weight(1f).padding(start=5.dp),fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            if(selected.size>=maximum)Text("Limit erreicht. Erhöhe es unter Home anpassen.",Modifier.padding(top=8.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={save(selected)},enabled=selected.size<=maximum,modifier=Modifier.fillMaxWidth().padding(top=12.dp).height(50.dp)) {Text("Speichern")}
        }
    }
}

@Composable private fun AppTile(app:LauncherApp,modifier:Modifier=Modifier,columns:Int=4,showLabel:Boolean=true,editing:Boolean=false,compact:Boolean=false,onLongClick:()->Unit={},onClick:()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(editing)MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent).then(if(editing)Modifier else Modifier.combinedClickable(onClick=onClick,onLongClick=onLongClick)).padding(vertical=if(compact)6.dp else 8.dp).testTag("app-${app.packageName}").semantics {contentDescription=app.title},horizontalAlignment=Alignment.CenterHorizontally) {
        CategoryAppIcon(app.category,when(columns) {3->58.dp;5->36.dp;else->47.dp})
        if(showLabel)Text(app.title,Modifier.padding(top=5.dp),fontSize=11.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onBackground,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

private val pixelDigits=mapOf(
    '0' to listOf("01110","10001","10011","10101","11001","10001","01110"),
    '1' to listOf("00100","01100","00100","00100","00100","00100","01110"),
    '2' to listOf("01110","10001","00001","00010","00100","01000","11111"),
    '3' to listOf("11110","00001","00001","01110","00001","00001","11110"),
    '4' to listOf("00010","00110","01010","10010","11111","00010","00010"),
    '5' to listOf("11111","10000","10000","11110","00001","00001","11110"),
    '6' to listOf("01110","10000","10000","11110","10001","10001","01110"),
    '7' to listOf("11111","00001","00010","00100","01000","01000","01000"),
    '8' to listOf("01110","10001","10001","01110","10001","10001","01110"),
    '9' to listOf("01110","10001","10001","01111","00001","00001","01110"),
    ':' to listOf("00000","00100","00100","00000","00100","00100","00000")
)

@Composable private fun PixelClock(value:String,modifier:Modifier=Modifier,compact:Boolean=false) {
    BoxWithConstraints(modifier.fillMaxWidth().height(if(compact)52.dp else 68.dp).testTag("digital-clock").semantics {contentDescription="Uhrzeit "+value},contentAlignment=Alignment.Center) {
        val pixel=minOf(5.7.dp,maxWidth/29f)
        Row(horizontalArrangement=Arrangement.spacedBy(pixel*.45f),verticalAlignment=Alignment.CenterVertically) {
            value.forEach {glyph->Column(verticalArrangement=Arrangement.spacedBy(pixel*.13f)) {
                pixelDigits.getValue(glyph).forEach {line->Row(horizontalArrangement=Arrangement.spacedBy(pixel*.08f)) {
                    line.forEach {bit->Box(Modifier.size(pixel).clip(RoundedCornerShape(pixel*.14f)).background(if(bit=='1')MaterialTheme.colorScheme.onBackground else androidx.compose.ui.graphics.Color.Transparent))}
                }}
            }}
        }
    }
}

private fun appCategory(title:String,packageName:String):AppCategory {
    val id="$title $packageName".lowercase(Locale.ROOT).replace('_',' ').replace('.',' ')
    fun has(vararg words:String)=words.any(id::contains)
    return when {
        has("authenticator","password","passwort","security","vpn","bitwarden","keepass")->AppCategory.SECURITY
        has("ausweis","e-id","eid")->AppCategory.SECURITY
        has("sportsbook","bet365","football","soccer","formula 1","sports")->AppCategory.SPORTS
        has("bank","banking","finance","finanzen","paypal","revolut","wallet","sparkasse","volksbank","n26","wise","klarna")->AppCategory.FINANCE
        has("amazon","shop","shopping","ebay","zalando","outlet","temu","etsy","marketplace","benefits")->AppCategory.SHOPPING
        has("instagram","tiktok","facebook","snapchat","threads","reddit","linkedin","pinterest","social","bluesky","mastodon","be real","twitter")->AppCategory.SOCIAL
        has("whatsapp","telegram","messenger","signal","discord","messages","chat")->AppCategory.MESSAGING
        has("spotify","music","bose","soundcloud","deezer","tidal","podcast")->AppCategory.MUSIC
        has("youtube","netflix","prime video","disney","twitch","video","stream")->AppCategory.MEDIA
        has("school","study","duolingo","bibel","bible","learn","lernen","quizlet")->AppCategory.EDUCATION
        has("fitness","health","hevy","gym","sport","workout","laufen")->AppCategory.FITNESS
        has("maps","map","travel","reise","bahn","flight","uber","booking","trip")->AppCategory.TRAVEL
        has("calendar","kalender","mail","outlook","drive","docs","notion","keep","tasks","todo","office","productivity")->AppCategory.PRODUCTIVITY
        has("settings","einstellungen","calculator","rechner","clock","kamera","camera","files","file manager","device care","bixby")->AppCategory.UTILITIES
        else->AppCategory.OTHER
    }
}

private fun categoryIcon(category:AppCategory):ImageVector=when(category) {
    AppCategory.SOCIAL->Icons.Outlined.Groups
    AppCategory.FINANCE->Icons.Outlined.AccountBalanceWallet
    AppCategory.SHOPPING->Icons.Outlined.ShoppingBag
    AppCategory.MESSAGING->Icons.Outlined.ChatBubbleOutline
    AppCategory.MEDIA->Icons.Outlined.PlayCircle
    AppCategory.MUSIC->Icons.Outlined.MusicNote
    AppCategory.EDUCATION->Icons.Outlined.School
    AppCategory.FITNESS->Icons.Outlined.FitnessCenter
    AppCategory.SPORTS->Icons.Outlined.SportsSoccer
    AppCategory.TRAVEL->Icons.Outlined.Flight
    AppCategory.SECURITY->Icons.Outlined.Security
    AppCategory.PRODUCTIVITY->Icons.Outlined.Work
    AppCategory.UTILITIES->Icons.Outlined.Settings
    AppCategory.OTHER->Icons.Outlined.Apps
}

private fun categoryLabel(category:AppCategory)=when(category) {
    AppCategory.SOCIAL->"Social Media";AppCategory.FINANCE->"Finanzen";AppCategory.SHOPPING->"Shopping";AppCategory.MESSAGING->"Nachrichten";AppCategory.MEDIA->"Video";AppCategory.MUSIC->"Musik";AppCategory.EDUCATION->"Lernen";AppCategory.FITNESS->"Fitness";AppCategory.SPORTS->"Sport";AppCategory.TRAVEL->"Reisen";AppCategory.SECURITY->"Sicherheit";AppCategory.PRODUCTIVITY->"Produktivität";AppCategory.UTILITIES->"Werkzeuge";AppCategory.OTHER->"App"
}

@Composable private fun CategoryAppIcon(category:AppCategory,size:Dp) {
    val shape=RoundedCornerShape(16.dp)
    Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp,MaterialTheme.colorScheme.outlineVariant,shape),contentAlignment=Alignment.Center) {
        Icon(categoryIcon(category),categoryLabel(category),Modifier.size(size*.48f),tint=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable internal fun AppDrawer(vm:HubViewModel,active:Boolean,onBack:()->Unit) {
    val context=LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic=LocalHapticFeedback.current
    val appRevision by vm.launcherAppsRevision.collectAsStateWithLifecycle()
    var apps by remember(context) {mutableStateOf(emptyList<LauncherApp>())}
    LaunchedEffect(context,appRevision) {apps=withContext(Dispatchers.IO) {installedApps(context)}}
    var query by rememberSaveable {mutableStateOf("")}
    var menu by remember {mutableStateOf<LauncherApp?>(null)}
    val searchFocus=remember {FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var drawerWasActive by remember {mutableStateOf(false)}
    val matches=remember(apps,query) {
        val ranked=apps.mapNotNull {app->appSearchRank(app.title,app.packageName,query)?.let {rank->app to rank}}
        val best=ranked.minOfOrNull {it.second} ?: Int.MAX_VALUE
        ranked.filter {(_,rank)->when {best<=2->rank<=2;best==3->rank==3;else->true}}
            .sortedWith(compareBy<Pair<LauncherApp,Int>> {it.second}.thenBy(String.CASE_INSENSITIVE_ORDER) {it.first.title})
            .map {it.first}
    }
    fun open(app:LauncherApp) {focusManager.clearFocus(force=true);keyboard?.hide();launchApp(context,app)}
    LaunchedEffect(active) {
        if(active) {
            drawerWasActive=true
            query=""
            withFrameNanos { }
            runCatching {searchFocus.requestFocus()}
            keyboard?.show()
        } else if(drawerWasActive) {
            focusManager.clearFocus(force=true);keyboard?.hide();drawerWasActive=false
        }
    }
    BackHandler(enabled=active) {onBack()}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal=22.dp).testTag("app-drawer")) {
        Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,modifier=Modifier.size(44.dp)) {Icon(Icons.Outlined.ArrowBack,"Zurück zu Home")}
            Text("Alle Apps",Modifier.weight(1f).padding(start=4.dp),fontSize=26.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.6).sp)
            Text(apps.size.toString(),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().focusRequester(searchFocus).testTag("app-search"),
            placeholder={Text("Apps durchsuchen",fontSize=15.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},
            trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}) {Icon(Icons.Outlined.Close,"Suche leeren")}},
            singleLine=true,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,imeAction=ImeAction.Search),
            keyboardActions=KeyboardActions(onSearch={matches.firstOrNull()?.let(::open)}),shape=RoundedCornerShape(18.dp))
        Text(if(query.isBlank())"Gedrückt halten für Home und App-Info"else matches.size.toString()+if(matches.size==1)" Treffer · Eingabe zum Öffnen"else" Treffer · Eingabe öffnet den ersten",Modifier.padding(top=12.dp,bottom=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(matches.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.SearchOff,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(apps.isEmpty())"Keine Apps gefunden"else"Keine Treffer",Modifier.padding(top=12.dp),fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(query.isNotBlank())TextButton(onClick={query=""}) {Text("Suche leeren")}
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("drawer-app-list"),contentPadding=PaddingValues(top=6.dp,bottom=16.dp)) {
            items(matches,key={it.packageName}) {app->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick={open(app)},onLongClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);menu=app}).heightIn(min=54.dp).padding(vertical=12.dp,horizontal=8.dp).testTag("drawer-"+app.packageName),verticalAlignment=Alignment.CenterVertically) {
                    Text(app.title,Modifier.weight(1f).testTag("drawer-app-title"),fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            }
        }
    }
    menu?.let {app->
        val selected=state.settings.homeAppPackages ?: apps.take(state.settings.homeAppCount).map {it.packageName}.toSet()
        val onHome=app.packageName in selected
        AlertDialog(onDismissRequest={menu=null},title={Text(app.title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={
                if(!onHome && selected.size>=64)android.widget.Toast.makeText(context,"Maximal 64 Home-Apps. Entferne zuerst eine App.",android.widget.Toast.LENGTH_SHORT).show()
                else vm.settings {settings->
                    val current=settings.homeAppPackages ?: apps.take(settings.homeAppCount).map {it.packageName}.toSet()
                    val next=if(app.packageName in current)current-app.packageName else current+app.packageName
                    settings.copy(homeAppPackages=next,homeAppCount=maxOf(settings.homeAppCount,next.size).coerceAtMost(64))
                }
                menu=null
            },Modifier.fillMaxWidth().testTag("drawer-toggle-home")) {Text(if(onHome)"Von Home entfernen"else"Zu Home hinzufügen")}
            TextButton(onClick={
                runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+app.packageName)))}
                    .onFailure {android.widget.Toast.makeText(context,"App-Info konnte nicht geöffnet werden.",android.widget.Toast.LENGTH_SHORT).show()}
                menu=null
            },Modifier.fillMaxWidth()) {Text("App-Info öffnen")}
        }},confirmButton={TextButton(onClick={menu=null}) {Text("Schließen")}})
    }
}

@Composable internal fun SettingsPage(vm:HubViewModel,state:de.simonhub.core.HubState,active:Boolean,onCustomize:()->Unit,onBack:()->Unit) {
    BackHandler(enabled=active,onBack=onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("settings-page")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(Icons.Outlined.ArrowBack,"Zurück zu Home")}
            Text("Mein Hub",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text(BuildConfig.VERSION_NAME,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f)) {SettingsScreen(vm,state,onCustomize)}
    }
}

@Composable internal fun DashboardScaffold(tab:Int,onTab:(Int)->Unit,onAdd:()->Unit,onBack:()->Unit,backEnabled:Boolean=true,content:@Composable ()->Unit) {
    BackHandler(enabled=backEnabled,onBack=onBack)
    Scaffold(
        modifier=Modifier.testTag("today-dashboard"),containerColor=MaterialTheme.colorScheme.background,
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp,modifier=Modifier.testTag("dashboard-navigation")) {
            listOf("Heute" to Icons.Outlined.WbSunny,"Aufgaben" to Icons.Outlined.Checklist).forEachIndexed {i,p->
                NavigationBarItem(selected=tab==i,onClick={onTab(i)},icon={Icon(p.second,p.first)},label={Text(p.first,fontSize=12.sp)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))
            }
        }},
        floatingActionButton={if(tab==1)FloatingActionButton(onClick=onAdd,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,shape=RoundedCornerShape(20.dp),modifier=Modifier.size(56.dp)) {Icon(Icons.Outlined.Add,"Aufgabe hinzufügen")}}
    ) {insets->Box(Modifier.fillMaxSize().padding(insets)) {content()}}
}

private fun weatherIcon(code:Int):ImageVector=when(code) {
    0->Icons.Outlined.WbSunny;1,2->Icons.Outlined.WbCloudy;3,45,48->Icons.Outlined.Cloud;in 51..67,in 80..82->Icons.Outlined.WaterDrop;in 71..77,in 85..86->Icons.Outlined.AcUnit;in 95..99->Icons.Outlined.FlashOn;else->Icons.Outlined.WbSunny
}

internal fun normalizeAppSearch(value:String):String=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKD)
    .lowercase(Locale.ROOT).replace("ß","ss").filter {it.isLetterOrDigit()}

internal fun appSearchRank(title:String,packageName:String,query:String):Int? {
    val needle=normalizeAppSearch(query)
    val name=normalizeAppSearch(title)
    val words=title.split(Regex("[^\\p{L}\\p{N}]+")).map(::normalizeAppSearch)
    return when {
        needle.isEmpty() || name==needle->0
        name.startsWith(needle)->1
        words.any {it.startsWith(needle)}->2
        name.contains(needle)->3
        normalizeAppSearch(packageName).contains(needle)->4
        else->null
    }
}

internal fun matchesAppSearch(title:String,packageName:String,query:String)=appSearchRank(title,packageName,query)!=null

private fun installedApps(context:Context):List<LauncherApp> {
    val pm=context.packageManager
    val launch=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val seen=mutableSetOf<String>()
    return pm.queryIntentActivities(launch,0).mapNotNull {entry:ResolveInfo->
        val activity=entry.activityInfo ?: return@mapNotNull null
        val packageName=activity.packageName
        if(!seen.add(packageName))return@mapNotNull null
        runCatching {val title=entry.loadLabel(pm).toString();LauncherApp(title,packageName,activity.name,appCategory(title,packageName))}.getOrNull()
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {it.title})
}

private fun launchApp(context:Context,app:LauncherApp) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.packageName,app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }.onFailure {android.widget.Toast.makeText(context,"Die App konnte nicht geöffnet werden. Ist sie noch installiert?",android.widget.Toast.LENGTH_SHORT).show()}
}

