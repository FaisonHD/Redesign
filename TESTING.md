# Teststatus

## Version 1.3.7

Der GitHub-Actions-Workflow unter .github/workflows/android-apk.yml führt die Android-/Robolectric-Tests und Planner-Tests aus, baut die Release-APK, prüft deren Signatur und liest Paketname sowie Versionsangaben aus. Der APK-Build wird 14 Tage als Workflow-Artefakt bereitgestellt.

Die Tests decken Launcher und Wetter-/Heute-Kalender, fokussierte Appsuche beim Wischen nach links, die App-Auswahl, iconsfreie App-Liste, Monatskalender mit drei Titeln pro Tag und ohne separate Terminliste, Entfernen des Ziele-Tabs, sofort fokussiertes Aufgabenfeld, Task-Erstellung mit standardmäßig aktivierter Datums- und Uhrzeit-Erinnerung sowie persistierte Einstellungen und Backups ab. Der Monatsbereich bleibt scrollbar, damit die Task-Kachel auch auf kleinen Displays erreichbar ist. Task-Tests laufen bei einer üblichen Handyauflösung und prüfen einen echten Tap auf „Aufgabe speichern“ bei sichtbarer Tastatur ohne manuelles Scrollen. verification/ enthält ältere Ergebnisse und ist kein Nachweis für die aktuelle Quelle.

Lokal mit JDK 17, Android SDK 35 und Build Tools 35.0.0:

    ./gradlew :app:testDebugUnitTest :planner:test :app:assembleRelease


