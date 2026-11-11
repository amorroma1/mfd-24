# MFD-24

*Multi-Function Display, 24 Stunden: der Bildschirm im Cockpit, und der Tag in einer Umdrehung des Stundenzeigers.*

[![build](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml/badge.svg)](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml)

[English](../../README.md) · [Français](README.fr.md) · **Deutsch** · [Italiano](README.it.md) · [日本語](README.ja.md) · [中文](README.zh.md)

Ein 24-Stunden-Instrumentenzifferblatt für Menschen, die Wache gehen — Piloten, Seeleute,
Drohnenbesatzungen, Diensthabende. Eine Umdrehung des Stundenzeigers pro Tag, die eigene Wache als
Bogen auf diesem Tag, ein Totmann-Monitor, der zum SOS eskaliert und einen Eintrag hinterlässt, und
die Telemetrie, mit der eine Wache tatsächlich läuft.

Alles funktioniert offline, außer dem Wetter und der täglichen Release-Prüfung. Keine Begleit-App, kein Konto, keine Analytik. GPL.

| Interaktiv | Always-on |
|---|---|
| ![](../screenshots/earth.png) | ![](../screenshots/ambient.png) |

*Eine laufende Wache in New York JFK. Dasselbe Zifferblatt in beiden Aufnahmen: Always-on ist das
interaktive Blatt, gedimmt, ohne den Sekundencursor und ohne die Hintergrundtönung.*

**Wear OS 3.0+ (API 30).** Gebaut und getragen auf einer TicWatch Pro 3 Ultra (454 × 454).
**[Installieren →](INSTALL.de.md)**

---

## Das bekommen Sie

| | |
|---|---|
| **24-h-Zifferblatt** | Eine Umdrehung pro Tag. Mittag oben oder Mitternacht oben. Die Minuten auf ihrem eigenen Ring; die Sekunde ist ein Cursor, der von Strich zu Strich springt — nichts wischt über das Blatt. |
| **Wachen-Timer** | Sofort starten oder vorbestellen. Ein Bogen spannt die Dienststunden auf und verblasst, während sie abgeleistet werden; `DUTY: 04:40 REM` zählt herunter. Beide Grenzen piepen und vibrieren, auch bei dunklem Display. Eine beendete Wache räumt sich eine Stunde später selbst ab. |
| **Vigilanz-Monitor** | Standardmäßig aus. Alle 5/10/15 Minuten verlangt er ein Lebenszeichen; dreißig Sekunden später sendet er SOS in Salven und hinterlässt `MAN DOWN` mit dem Zulu-Moment auf dem Blatt. Optional: Ihr Puls während des versäumten Checks, neben dem letzten, den der Puls-Slot gelesen hat, während Sie in Bewegung waren — einmal pro Minute vermerkt, einen Tag lang aufbewahrt. |
| **Log-Export** | Das Vorfallsprotokoll verlässt die Uhr auf zwei Wegen: als **QR-Code** für die Handykamera und als **Ton** — Bell 202 AFSK, 1200 Bd, `minimodem --rx 1200` am anderen Ende. Der Ton trägt alles; der QR-Code trägt die neuesten Zeilen, die in einen 1,2-Zoll-Code passen, und sagt es (`END 14 OF 32`). |
| **Zulu + Datum-Zeit-Gruppe** | `Z 11NOV 13:05`, die Uhr, in der ein Logbucheintrag geschrieben wird. |
| **Wetter** | Temperatur, Zustand in METAR-Kürzeln und QNH. An einem Flugplatz ist es der **eigene METAR** dieses Platzes; überall sonst Open-Meteo. Alle 30 Minuten aufgefrischt. |
| **5-km-Ortserkennung** | Nennt den nächstgelegenen Flugplatz, Hafen, Heliport oder die nächste Startanlage aus 9 649 Anlagen in einem 194-KB-Index an Bord. Militärische Anlagen werden in der Akzentfarbe gezeichnet. |
| **Nadir, Sonne und Mond** | Das Tageslicht als Schattierung über dem Stundenband, offline berechnet. Sonne und Mond stehen an ihren wahren Stundenwinkeln — die Marke auf den Himmelskörper selbst gerichtet, wird das Zifferblatt zum **Kompass**. Der Mond trägt seine echte Phase. |
| **Sensor-Slots** | Zwei optionale Anzeigen neben der Nabe: Puls, die tägliche Schrittzahl der Plattform selbst oder Stationsdruck. Standardmäßig aus; sie laufen nur bei eingeschaltetem Display. Im Always-on wird der Puls einmal pro Minute neu gelesen, solange die Uhr getragen wird — ein Fenster von fünfzehn Sekunden, Striche nach drei Minuten ohne Erfassung. |
| **Always-on** | Dasselbe Blatt, gedimmt. `AUTO` dünnt es nach Sonnenuntergang auf jedes zweite Pixel aus — nie während einer Nachtwache. |
| **Direct Boot** | Zeichnet mit Inhalt, bevor die Uhr entsperrt ist. |

## In Aktion

| Ein versäumter Check, spät quittiert | JFK nach Heathrow, der ganze Flug | Aufwachen |
|---|---|---|
| ![](../media/mandown.gif) | ![](../media/flight.gif) | ![](../media/wake.gif) |
| Das SOS blieb unbeantwortet: `MAN DOWN`, der Zulu-Moment, eine Marke auf dem Bogen. Ein Tippen antwortet mit `TAP AGAIN`; ein zweites, innerhalb einer Sekunde, löscht es — ein Ärmel kann das nicht. | BA 178 am 11. November 2026, nach dem echten Flugplan, in 36 Sekunden: eine Acht-Stunden-Wache, am Gate gestartet, der Platz und sein METAR fünf Minuten nach dem Abheben weg, die Kabine bei 790 hPa, Sonnenuntergang über dem Atlantik, `EGLL` zurück im Endanflug, und die Zeiger am Boden nach London gewandert. | Nur das Licht kommt. Always-on trägt dieselben Farbtöne, also gibt es nichts zu überblenden. |

*Der Flug dauert mit Absicht 36 Sekunden — ein ganzer Tag für alle, die einem zusehen wollen; am
Boden geht es langsam, über dem Ozean schnell. Die Landung allein, in voller Geschwindigkeit:
[landing.gif](../media/landing.gif). Zwei Dinge, nach denen oft gefragt wird: Die Zeiger wechseln
die Zeitzone erst nach der Landung, weil eine Uhr ihre Zone vom Telefon bezieht und das Telefon
erst am Boden ein Netz findet; und über dem Ozean steht die Sonnenmarke rechts vom Tageslichtband —
die Marke ist der wahre Stundenwinkel der Sonne, das Band ist der Tag in der Zone der Uhr, und der
Abstand zwischen beiden ist genau der Rückstand der Zone, den das Gleiten schließt. Puls,
Kabinendruck und Akku sind auf dem Emulator inszeniert; Position, Wetter und Astronomie sind die
des Zifferblatts selbst.*

## Installation

`app-earth-release.apk` aus dem
[aktuellen Release](https://github.com/amorroma1/mfd-24/releases/latest) laden und per Sideload
installieren. Wear OS hat keine Sideloading-Oberfläche, also ist das eine Aufgabe für ADB:

```
adb connect 192.168.1.50:41234
adb install -r app-earth-release.apk
```

Dann MFD-24 im Zifferblatt-Wähler der Uhr auswählen. Kein Rechner? Ein Telefon kann denselben
ADB-Handschlag ausführen: **[Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2)**
oder **[Bugjaeger](https://play.google.com/store/apps/details?id=eu.sisik.hackendebug)**, beide
von Dritten, beide in der Anleitung Schritt für Schritt beschrieben.
**[Vollständige Anleitung — drei Wege, von „nur ein Telefon“ bis zum Bauen aus dem Quellcode →](INSTALL.de.md)**

<details>
<summary><b>Updates, und warum das nicht bei Google Play ist</b></summary>

<br>

Die Uhr fragt GitHub **einmal täglich, außerhalb der Wache**, und meldet sich einmal pro Release.
Sie lädt nichts herunter und installiert nichts: **Wear OS lässt eine App keine App installieren**
(geprüft auf einer TicWatch Pro 3 Ultra). `ABOUT → RELEASES` zeigt die Release-Seite als QR-Code
für die Handykamera, und die Installation bleibt `adb install -r`. `ABOUT → RELEASE CHECK` schaltet
das Ganze ab.

Nicht bei Google Play ist es mit Absicht: Ein Totmann-Monitor darf keine Impuls-Installation sein,
die Berechtigungen, die er legitim braucht, halten ein Hobbyprojekt in dauernder Store-Prüfung, und
eine APK mit ihrer SHA-256 in den Release-Notizen ist nachprüfbarer als ein Store-Eintrag. Die
vollständige Begründung steht in
[INSTALL.de.md](INSTALL.de.md#warum-mfd-24-nicht-bei-google-play-ist).

</details>

<details>
<summary><b id="permissions">Berechtigungen — bei der Installation wird nichts angefragt</b></summary>

<br>

Der Standort wird vom Einstellungsbildschirm aus erfragt, denn ein Zifferblatt ist ein
`WallpaperService` und kann selbst keinen Dialog öffnen.

| Berechtigung | Wofür | Ohne sie |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Wetter, Nadir, die Ortserkennung | diese drei bleiben leer; Uhr und Astronomie sind nicht betroffen |
| `ACCESS_BACKGROUND_LOCATION` | dasselbe, aus dem 30-Minuten-Auffrischjob | die Plattform liefert null statt eines Fehlers, was genau wie kein Signal aussieht |
| `WAKE_LOCK`, `FOREGROUND_SERVICE_HEALTH` | der Vigilanz-Monitor, der bei dunklem Display weiter messen muss | nur diese Funktion |
| `VIBRATE` | Wachgrenzen und das SOS | stumme Grenzen |
| `SCHEDULE_EXACT_ALARM` | Grenzen auf die Minute genau | angefragt, aber **nicht** erforderlich; ohne sie kann eine Grenze um ein paar Minuten rutschen |
| `INTERNET` | Open-Meteo und METAR alle 30 Minuten, dazu die tägliche Versionsprüfung | kein Wetter, keine Versionsprüfung |
| `BODY_SENSORS` | der Puls-Slot und der Puls in einem Vorfall | Striche; die Zeile `LOG PULSE` wird nicht angeboten |
| `ACTIVITY_RECOGNITION` | der Schritte-Slot; auf Wear 5+ auch das, was den Vigilanz-Dienst starten lässt | Striche; auf neuerem Wear keine Vigilanz |
| `POST_NOTIFICATIONS` | der laufende Hinweis des Monitors und die Update-Meldung einmal pro Release | der Monitor läuft unangekündigt |

Keine Kontakte, kein Speicher, kein Netz jenseits dieser Aufrufe. Eine von Hand eingegebene Position
ersetzt den Standort vollständig — siehe **Position** in den Einstellungen.

</details>

## Einstellungen

**Lange aufs Zifferblatt drücken, dann auf den Stift tippen.** Sieben einklappbare Abschnitte,
jeweils einer offen; der offene ist umrandet.

| Abschnitte | Duty control | Scheduling | Display | About |
|---|---|---|---|---|
| ![](../screenshots/settings-sections.png) | ![](../screenshots/settings-duty.png) | ![](../screenshots/settings-schedule.png) | ![](../screenshots/settings-display.png) | ![](../screenshots/settings-about.png) |

- **Duty control** — Dauer `4h` `8h` `12h` `CST`, ein vorbestellter Start und ein Chip, der
  `START NOW` oder ein terrakottafarbenes `END DUTY` zeigt. Ein Start in der Vergangenheit lässt
  sich nicht buchen.
- **Vigilance** — ein/aus, Prüfintervall, `VIBE STRENGTH`, `SOS SOUND` (OFF/LOW/MED/HIGH),
  `LOG PULSE` und das Vorfallsprotokoll mit seinem Export.
- **Sensors** — was jeder Slot an der Nabe liest.
- **Position** — automatisch oder von Hand eingegebene Koordinaten.
- **Display** — Palette, Oben auf dem Blatt, Mitternachtsmarke, Nadir, Sonnen- und Mondmarke,
  Wetter, Always-on und `HINTS` für das Kleingedruckte unter jeder Zeile.
- **Units** — °C/°F, hPa/mmHg.
- **About** — Version, das Repository als QR-Code, die Versionsprüfung.

**[Jede Zeile, mit Begründung →](../DESIGN.md#settings)**

## Das Zifferblatt im Detail

| ![](../screenshots/duty.png) | ![](../screenshots/ambient-auto.png) | ![](../screenshots/ambient-duty.png) |
|---|---|---|
| Mitten in der Wache: Der Bogen ist die Schicht, der helle Teil das, was noch abzuleisten ist. | `ALWAYS-ON > AUTO` dünnt das Blatt nach Sonnenuntergang aus — halbes Licht, halber Einbrenn-Tastgrad. | Eine laufende Wache behält die ganze Nacht das volle Blatt: Eine Nachtwache ist genau dann, wenn das Blatt im Dunkeln gelesen wird. |

**[Wie es funktioniert — Layout, Astronomie, der Zustandsautomat der Vigilanz, die Tests →](../DESIGN.md)**

<details>
<summary><b>Fußnoten — die Schrittzahl, die nötige Hardware und was es wiegt</b></summary>

<br>

**Wie die Schrittzahl zustande kommt.** Die Zeile bevorzugt die Tageszahl der Plattform selbst, über
Health Services, denn das ist die Zahl, mit der der Rest der Uhr übereinstimmt, und ihr ist egal,
wann dieses Zifferblatt installiert wurde. Wo eine Uhr keinen solchen Anbieter hat, oder der
Anbieter sich bindet und dann nichts sagt, zählt das Zifferblatt selbst, aus dem
Hardware-Schrittzähler. Dieser Rückfall ist **ab der nächsten lokalen Mitternacht** exakt, weil der
Stand des Zählers an der Tagesgrenze festgehalten wird; am Tag der Installation kann er nur ab dem
Moment zählen, in dem Sie den Slot einschalten, und er zeigt Striche statt einer `0`, die er
erfunden hätte. Eine hier getestete Uhr meldet ihre „tägliche“ Summe als Schritte seit Beginn des
Abonnements statt seit Mitternacht — eine Eigenheit des Anbieters, keine Einstellung, und der
Grund, die Zeile als *gezählte Schritte* zu lesen und nicht als Fitness-Statistik.

**Display.** Rund, getestet bei **454 × 454**. Jede Position auf dem Blatt ist ein Bruchteil des
Radius, andere runde Größen sollten also passen; eckige und unten abgeschnittene Displays sind nicht
vorgesehen und beschneiden die äußere Skala. Auch der Text ist vom Radius abgeleitet — das
Breitenbudget der Anzeigen ist Arithmetik, und ein Test schlägt fehl, wenn eine Zeile die
Stundenziffern erreichen würde.

**Hardware und Leistung.** Wear OS 3.0+ (API 30), jede Uhr, auf der die Plattform läuft; keine
GPU-Funktionen jenseits eines gewöhnlichen Canvas. `render()` allokiert nichts, also gibt es keine
Garbage-Collector-Pause, um die herum geplant werden müsste, und die Bildrate folgt dem Bedarf:
**ein Bild pro Sekunde in Ruhe**, 16 ms nur, während ein Übergang animiert, und ein Bild pro Minute
im Always-on. Die optionalen Sensoren sind die einzigen echten Kosten — der Puls ist eine LED, und
der Vigilanz-Monitor lässt den Beschleunigungssensor im Sensor-Hub puffern, statt den Prozessor zu
wecken. Alles andere läuft aus zwischengespeicherten Werten.

**Größe.** Etwa **3,5 MB** installiert, davon rund 2,8 MB Code, 190 KB der Standortindex an Bord
und 220 KB die Vorschau für den Wähler. Der Index ist absichtlich unkomprimiert gespeichert, damit
er ohne Entpacken gelesen werden kann. Nur englische Ressourcen: Der eigene Text der App ist
Englisch, und achtzig Locales an Bibliotheks-Strings mitzuführen, die sie nie anzeigen kann,
kostete 260 KB.

</details>

## Was es nicht ist

Ein persönliches Instrument, kein zertifiziertes. Der Vigilanz-Monitor ist ein unzertifiziertes
Hilfsmittel: Er lässt sich abschalten, von einem losen Armband pausieren oder von den Doze-Grenzen
der Plattform verzögern. Das Vorfallsprotokoll gilt für **eine Wache** — der Start der nächsten
leert es, also verlässt alles Aufhebenswerte die Uhr vorher über `EXPORT LOG`. Nichts hier ersetzt
ein Gerät, das eine Aufsichtsbehörde zugelassen hat.

<details>
<summary><b>Das Vorfallsprotokoll exportieren — zwei Kanäle und ihre Grenzen</b></summary>

`EXPORT LOG`, im Vigilanz-Abschnitt, legt das Protokoll auf die beiden Kanäle, die eine Uhr
wirklich hat: einen **QR-Code** für die Handykamera, die ohnehin schon darauf zeigt, und **Ton** —
Bell 202 AFSK, 1200 Bd, 8N1, die Bitübertragungsschicht von APRS, am anderen Ende also
`minimodem --rx 1200`, ans Gehäuse gehalten. Beide tragen dasselbe Klartext-Paket: einen Kopf, das
Rufzeichen des Instruments, eine `WATCH`-Zeile mit Beginn, Ende und Länge der Wache, einen Vorfall
pro Zeile in Zulu-Zeit — mit `HR` und `BASE … AT`, wenn `LOG PULSE` an ist — und eine `END`-Zeile
mit der Anzahl und einer CRC32 über alles darüber, sodass ein verwackeltes Foto oder eine
verrauschte Aufnahme laut scheitert, statt leise eine Zeile zu verlieren.

- **Das Protokoll hält die neuesten 32 Vorfälle der laufenden Wache**, höchstens 30 Tage lang. Der
  Start der nächsten Wache leert es; vorher exportieren.
- **Der Ton trägt immer das ganze Protokoll** — achtzehn Sekunden Töne an der Obergrenze. Die
  Übertragung beginnt, wenn der Bildschirm sich öffnet, und wiederholt sich auf ein Tippen.
- **Der QR-Code trägt die neuesten Einträge, die hineinpassen** — in den größten Code, den ein
  1,2-Zoll-Display zeigen kann: alle 32, wenn die Zeilen bloße Zeitpunkte sind, etwa 14, wenn jede
  Zeile einen Puls und seinen Bezugswert trägt. Ein gekürzter Code sagt es: Seine `END`-Zeile
  lautet `END 14 OF 32`, nie `END 14`. Für jede Zeile eines so langen Protokolls den Ton nehmen.
- **Nur lesend.** Es gibt keinen Importpfad, also kann nichts, was ein Paket liest, den Datensatz
  schreiben.

</details>

## Bauen

```
git clone https://github.com/amorroma1/mfd-24.git
cd mfd-24
./gradlew :app:assembleEarthDebug
```

JDK 17+ und das Android-SDK. 192 JVM-Tests: `./gradlew :app:testEarthDebugUnitTest`.

## Wie es entstanden ist

Entworfen, entschieden und getragen von einer Person, geschrieben mit Claude (Anthropic) als
Pair-Programmer. Jede Konstante, die sich messen ließ, wurde auf der Uhr gemessen statt hergeleitet,
jede Invariante ist durch einen Test festgehalten, und nichts wird veröffentlicht, was nicht am
Handgelenk gelaufen ist. Die Arbeitsnotizen in `CLAUDE.md` sind das Protokoll dieser Zusammenarbeit
und bleiben absichtlich im Repository.

## Lizenz

GPL-3.0-or-later — siehe [LICENSE](../../LICENSE). Marinestützpunkte und Heliports stammen aus
OpenStreetMap (© OpenStreetMap-Mitwirkende, **ODbL**), also trägt jeder daraus gebaute Index diese
Lizenz ebenfalls; Flugplätze, Häfen und Startanlagen werden in diesem Repository kuratiert. Wetter
von [Open-Meteo](https://open-meteo.com/) (CC BY 4.0) und
[aviationweather.gov](https://aviationweather.gov/) (US NOAA, gemeinfrei).
