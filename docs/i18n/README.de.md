# MFD-24

*Multi-Function Display, 24 Stunden: der Bildschirm im Cockpit, und der Tag in einer Umdrehung des Stundenzeigers.*

[![build](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml/badge.svg)](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml)

[English](../../README.md) · [Français](README.fr.md) · **Deutsch** · [Italiano](README.it.md) · [日本語](README.ja.md) · [中文](README.zh.md)

Ein 24-Stunden-Instrumentenzifferblatt für Menschen, die Wache gehen — Piloten, Seeleute,
Drohnenbesatzungen, Diensthabende. Eine Umdrehung des Stundenzeigers pro Tag, die eigene Wache als
Bogen auf diesem Tag, ein Totmannschalter, der zum SOS eskaliert und einen Logbucheintrag hinterlässt, und
die Telemetrie, mit der eine Wache tatsächlich läuft.

Alles funktioniert offline, außer dem Wetter und dem täglichen Release-Check. Keine Begleit-App,
kein Konto, keine Analytik. GPL-3.0.

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
| **Wach-Timer** | Sofort antreten oder ansetzen. Ein Wachbogen spannt die Wachstunden auf und verblasst, während sie abgeleistet werden; `DUTY: 04:40 REM` zählt herunter. Antritt und Ablösung piepen und vibrieren, auch bei dunklem Display. Eine übergebene Wache räumt sich eine Stunde später selbst ab. |
| **Totmannschalter** | Standardmäßig aus. Alle 5/10/15 Minuten fragt er ein Lebenszeichen ab; bleibt die Quittung dreißig Sekunden aus, sendet er SOS in Salven und hinterlässt `MAN DOWN` — Totmann ausgelöst — mit dem Zulu-Moment auf dem Blatt. Optional vermerkt er Ihren Puls während der unquittierten Abfrage, neben Ihrem letzten Wert in Bewegung. |
| **Logbuch senden** | Das Logbuch verlässt die Uhr als **QR-Code** für die Handykamera oder als **Ton** — Bell 202 AFSK, dekodiert von `minimodem`. Der Ton trägt alles; der QR-Code trägt, was hineinpasst, und sagt es. [Kanäle und Grenzen ↓](#export) |
| **Zulu + Datum-Zeit-Gruppe** | `Z 11NOV 13:05`, die Uhr, in der ein Logbucheintrag geschrieben wird. |
| **Wetter** | Temperatur, Zustand in METAR-Kürzeln und QNH. An einem Flugplatz ist es der **eigene METAR** dieses Platzes; überall sonst Open-Meteo. Alle 30 Minuten aufgefrischt. |
| **5-km-Standortkennung** | Nennt den nächstgelegenen Flugplatz, Hafen, Heliport oder die nächste Startanlage aus 9 649 Anlagen in einem 194-KB-Index an Bord. Militärische Anlagen werden in der Akzentfarbe gezeichnet. |
| **Nadir, Sonne und Mond** | Der Tagbogen als Schattierung über dem Stundenband, offline berechnet. Sonnenstand und Mondstand sitzen an ihren wahren Stundenwinkeln — die Marke auf den Himmelskörper selbst gerichtet, wird das Zifferblatt zum **Kompass**. Der Mond trägt seine echte Phase. |
| **Sensor-Slots** | Zwei optionale Anzeigen neben der Nabe: Puls, die Schritte des Tages oder Stationsdruck. Standardmäßig aus; sie laufen bei eingeschaltetem Display, und im Always-on wird der Puls einmal pro Minute neu gelesen, solange die Uhr getragen wird. [Wie dieses Fenster seinen Takt findet ↓](#pulse-window) |
| **Always-on** | Dasselbe Blatt, gedimmt. `AUTO` dünnt es nach Sonnenuntergang auf jedes zweite Pixel aus — nie während einer Nachtwache. |
| **Direct Boot** | Zeichnet mit Inhalt, bevor die Uhr entsperrt ist. |

## In Aktion

| Eine versäumte Abfrage, spät quittiert | JFK nach Heathrow, der ganze Flug | Aufwachen |
|---|---|---|
| ![](../media/mandown.gif) | ![](../media/flight.gif) | ![](../media/wake.gif) |
| Das SOS blieb unquittiert: `MAN DOWN`, der Zulu-Moment, eine Marke auf dem Wachbogen. Ein Tippen antwortet mit `TAP AGAIN`; ein zweites, innerhalb einer Sekunde, löscht es — ein Ärmel kann das nicht. | BA 178 am 11. November 2026, nach dem echten Flugplan, in 36 Sekunden: eine Acht-Stunden-Wache, am Gate angetreten, der Standort und sein METAR fünf Minuten nach dem Abheben weg, die Kabine bei 790 hPa, Sonnenuntergang über dem Atlantik, `EGLL` zurück im Endanflug, und die Zeiger am Boden nach London gewandert. | Nur das Licht kommt. Always-on trägt dieselben Farbtöne, also gibt es nichts zu überblenden. |

*Der Flug dauert mit Absicht 36 Sekunden — ein ganzer Tag für alle, die einem zusehen wollen; am
Boden geht es langsam, über dem Ozean schnell. Die Landung allein, in voller Geschwindigkeit:
[landing.gif](../media/landing.gif).*

<details>
<summary><b>Zwei Dinge, nach denen beim Flug gefragt wird</b></summary>

<br>

**Warum wechseln die Zeiger die Zeitzone erst nach der Landung?** Eine Uhr bezieht ihre Zeitzone
vom Telefon, und das Telefon findet erst am Boden ein Netz. Bis dahin behält das Zifferblatt die
Abflugzone — und darum steht über dem Ozean auch der Sonnenstand rechts vom Tagbogen: Die Marke ist
der wahre Stundenwinkel der Sonne, der Tagbogen ist der Tag in der Zone der Uhr, und der Abstand
zwischen beiden ist genau der Rückstand der Zone. Das Gleiten am Boden schließt ihn.

**Was daran ist echt?** Der Flugplan ist der von BA 178 an jenem Tag, und die Positionen folgen
seiner üblichen Nordatlantikroute, dem Zifferblatt als GPS-Fixes zugespielt; Wetter und Astronomie
sind daraus die eigene Arbeit des Zifferblatts. Puls, Kabinendruck und Akku sind auf dem Emulator
inszeniert, denn ein Emulator hat kein Handgelenk.

</details>

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

Die Uhr fragt GitHub **einmal täglich, wachfrei**, und meldet sich einmal pro Release.
Sie lädt nichts herunter und installiert nichts: **Wear OS lässt eine App keine App installieren**
(geprüft auf einer TicWatch Pro 3 Ultra). `INFO → VERSIONEN` zeigt die Release-Seite als QR-Code
für die Handykamera, und die Installation bleibt `adb install -r`. `INFO → RELEASE-CHECK` schaltet
das Ganze ab.

Nicht bei Google Play ist es mit Absicht: Ein Totmannschalter darf keine Impuls-Installation sein,
die Berechtigungen, die er legitim braucht, halten ein Hobbyprojekt in dauernder Store-Prüfung, und
eine APK mit ihrer SHA-256 in den Release-Notizen ist nachprüfbarer als ein Store-Eintrag. Die
vollständige Begründung steht in
[INSTALL.de.md](INSTALL.de.md#warum-mfd-24-nicht-bei-google-play-ist).

</details>

<details>
<summary><b id="permissions">Berechtigungen — bei der Installation wird nichts angefragt</b></summary>

<br>

Die Ortung wird vom Einstellungsbildschirm aus erfragt, denn ein Zifferblatt ist ein
`WallpaperService` und kann selbst keinen Dialog öffnen.

| Berechtigung | Wofür | Ohne sie |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Wetter, Nadir, die Standortkennung | diese drei bleiben leer; Uhr und Astronomie sind nicht betroffen |
| `ACCESS_BACKGROUND_LOCATION` | dasselbe, aus dem 30-Minuten-Auffrischjob | die Plattform liefert null statt eines Fehlers, was genau wie kein Signal aussieht |
| `WAKE_LOCK`, `FOREGROUND_SERVICE_HEALTH` | der Totmannschalter, der bei dunklem Display weiter messen muss | nur diese Funktion |
| `VIBRATE` | Antritt und Ablösung der Wache und das SOS | stumme Grenzen |
| `SCHEDULE_EXACT_ALARM` | Antritt und Ablösung auf die Minute genau | angefragt, aber **nicht** erforderlich; ohne sie kann eine Grenze um ein paar Minuten rutschen |
| `INTERNET` | Open-Meteo und METAR alle 30 Minuten, dazu der tägliche Release-Check | kein Wetter, kein Release-Check |
| `BODY_SENSORS` | der Puls-Slot und der Puls zu einem Vorkommnis | Striche; die Zeile `PULS INS LOGBUCH` wird nicht angeboten |
| `ACTIVITY_RECOGNITION` | der Schritte-Slot; auf Wear 5+ auch das, was den Totmann-Dienst starten lässt | Striche; auf neuerem Wear kein Totmann |
| `POST_NOTIFICATIONS` | der laufende Hinweis des Totmanns und die Update-Meldung einmal pro Release | der Totmann läuft unangekündigt |

Keine Kontakte, kein Speicher, kein Netz jenseits dieser Aufrufe. Eine von Hand eingegebene Position
ersetzt die Ortung vollständig — siehe **Position** in den Einstellungen.

</details>

## Einstellungen

**Lange aufs Zifferblatt drücken, dann auf den Stift tippen.** Sieben einklappbare Abschnitte,
jeweils einer offen; der offene ist umrandet.

Die Einstellungen folgen der Sprache der Uhr — Englisch, Deutsch, Französisch, Italienisch, Japanisch
oder Chinesisch — im Vokabular des Dienstes, nicht des App-Stores: *Wache*, *quart*, *guardia*, 当直,
值更. Das Zifferblatt selbst bleibt auf jeder Uhr Englisch: `DUTY`, `Z`, `QFE` und die METAR-Zeile
sind Instrumentenbeschriftung, keine Prosa.

| Abschnitte | Wache | Wachplan | Anzeige | Info |
|---|---|---|---|---|
| ![](../screenshots/settings-sections.png) | ![](../screenshots/settings-duty.png) | ![](../screenshots/settings-schedule.png) | ![](../screenshots/settings-display.png) | ![](../screenshots/settings-about.png) |

- **Wache** — Wachdauer `4h` `8h` `12h` `FREI`, ein angesetzter Antritt und ein Chip, der
  `WACHE ANTRETEN` oder ein terrakottafarbenes `WACHE ÜBERGEBEN` zeigt. Ein Antritt in der Vergangenheit lässt
  sich nicht ansetzen.
- **Totmann** — ein/aus, Abfrageintervall, `VIBRATION`, `SOS-SIGNAL` (AUS/MIN/MED/MAX),
  `PULS INS LOGBUCH` und das Logbuch samt Senden.
- **Sensoren** — was jeder Slot an der Nabe liest.
- **Position** — Ortung oder von Hand eingegebene Koordinaten.
- **Anzeige** — Leuchtfarbe, Zifferblatt oben, Mitternachtsmarke, Nadir, Sonnenstand und Mondstand,
  Wetter, Always-on und `HINWEISE` für das Kleingedruckte unter jeder Zeile.
- **Einheiten** — °C/°F, hPa/mmHg.
- **Info** — Version, das Repository als QR-Code, der Release-Check.

**[Jede Zeile, mit Begründung →](../DESIGN.md#settings)**

## Das Zifferblatt im Detail

| ![](../screenshots/duty.png) | ![](../screenshots/ambient-auto.png) | ![](../screenshots/ambient-duty.png) |
|---|---|---|
| Mitten in der Wache: Der Wachbogen ist die Wache, der helle Teil das, was noch abzuleisten ist. | `ALWAYS-ON > AUTO` dünnt das Blatt nach Sonnenuntergang aus — halbes Licht, halber Einbrenn-Tastgrad. | Eine laufende Wache behält die ganze Nacht das volle Blatt: Eine Nachtwache ist genau dann, wenn das Blatt im Dunkeln gelesen wird. |

**[Wie es funktioniert — Layout, Astronomie, der Zustandsautomat des Totmanns, die Tests →](../DESIGN.md)**

<details>
<summary><b>Fußnoten — das Pulsfenster, die Schrittzahl, die nötige Hardware und was es wiegt</b></summary>

<br>

<b id="pulse-window">Das Pulsfenster im Always-on.</b> Bei dunklem Display bleibt der Puls-Slot nicht
einfach laufen: Einmal pro Minute öffnet das Zifferblatt ein Fenster von fünfzehn Sekunden, schaltet
den Sensor ein, nimmt den ersten plausiblen Wert und schaltet den Sensor wieder aus. Drei Minuten ohne
Erfassung, und die Zahl weicht auf dem Blatt Strichen. Drei leere Fenster in Folge verlangsamen
zudem den Takt auf ein Fenster pro Viertelstunde, bis ein Wert, ein gehobenes Handgelenk oder die
eigene *getragen*-Flanke des Tragedetektors es beendet — denn der Detektor kann sich bei einem Tisch
irren, und hat es eine ganze Nacht lang getan: sechshundert Fenster gegen das Holz. Heute vierzig pro
Nacht, und am Handgelenk ändert sich nichts, wo ein Slot nach drei Minuten ohne Erfassung ohnehin
schon Striche zeigt.

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
der Totmannschalter lässt den Beschleunigungssensor im Sensor-Hub puffern, statt den Prozessor zu
wecken. Alles andere läuft aus zwischengespeicherten Werten.

**Größe.** Etwa **3,5 MB** installiert, davon rund 2,8 MB Code, 190 KB der Standortindex an Bord
und 220 KB die Vorschau für den Wähler. Der Index ist absichtlich unkomprimiert gespeichert, damit
er ohne Entpacken gelesen werden kann. Sechs Locales — Englisch, Deutsch, Französisch, Italienisch,
Japanisch, Chinesisch — für die eigenen Strings der App; die übrigen achtzig Locales an
Bibliotheks-Strings, die sie nie anzeigen könnte, kosteten 260 KB und bleiben draußen.

</details>

## Was es nicht ist

Ein persönliches Instrument, kein zertifiziertes. Der Totmannschalter ist ein unzertifiziertes
Hilfsmittel: Er lässt sich abschalten, von einem losen Armband aussetzen oder von den Doze-Grenzen
der Plattform verzögern. Das Logbuch gilt für **eine Wache** — der Antritt der nächsten
leert es, also verlässt alles Aufhebenswerte die Uhr vorher über `LOGBUCH · QR + ♪`. Nichts hier ersetzt
ein Gerät, das eine Aufsichtsbehörde zugelassen hat.

<details>
<summary><b id="export">Das Logbuch senden — zwei Kanäle und ihre Grenzen</b></summary>

<br>

`LOGBUCH · QR + ♪`, im Abschnitt TOTMANN, legt das Logbuch auf die beiden Kanäle, die eine Uhr
wirklich hat: einen **QR-Code** für die Handykamera, die ohnehin schon darauf zeigt, und **Ton** —
Bell 202 AFSK, 1200 Bd, 8N1, die Bitübertragungsschicht von APRS, am anderen Ende also
`minimodem --rx 1200`, ans Gehäuse gehalten. Beide tragen dasselbe Klartext-Paket: einen Kopf, das
Rufzeichen des Instruments, eine `WATCH`-Zeile mit Antritt, Ablösung und Länge der Wache, ein Vorkommnis
pro Zeile in Zulu-Zeit — mit `HR` und `BASE … AT`, wenn `PULS INS LOGBUCH` an ist — und eine `END`-Zeile
mit der Anzahl und einer CRC32 über alles darüber, sodass ein verwackeltes Foto oder eine
verrauschte Aufnahme laut scheitert, statt leise eine Zeile zu verlieren.

- **Das Logbuch hält die neuesten 32 Vorkommnisse der laufenden Wache**, höchstens 30 Tage lang. Der
  Antritt der nächsten Wache leert es; vorher senden.
- **Der Ton trägt immer das ganze Logbuch** — achtzehn Sekunden Töne an der Obergrenze. Die
  Übertragung beginnt, wenn der Bildschirm sich öffnet, und wiederholt sich auf ein Tippen.
- **Der QR-Code trägt die neuesten Einträge, die hineinpassen** — in den größten Code, den ein
  1,2-Zoll-Display zeigen kann: alle 32, wenn die Zeilen bloße Zeitpunkte sind, etwa 14, wenn jede
  Zeile einen Puls und seinen Bezugswert trägt. Ein gekürzter Code sagt es: Seine `END`-Zeile
  lautet `END 14 OF 32`, nie `END 14`. Für jede Zeile eines so langen Logbuchs den Ton nehmen.
- **Nur lesend.** Es gibt keinen Importpfad, also kann nichts, was ein Paket liest, den Datensatz
  schreiben.
- **Das Rufzeichen wird abgeleitet, nie eingegeben.** `KILO-15` ist ein gesalzener SHA-256 der
  Android-ID der Uhr, abgebildet auf zwanzig Präfixe × 99 Nummern: dieselbe Uhr ergibt bei jeder
  Neuinstallation dasselbe Rufzeichen, ein Werksreset vergibt ein neues, und die ID selbst verlässt
  die Uhr nie. Einer von 1 980 Namen im Netz, keine Seriennummer — feiner trägt das Paket nichts.
- **Kein minimodem?** [`tools/afsk/decode_afsk.py`](../../tools/afsk/decode_afsk.py) `aufnahme.m4a` ist derselbe Empfänger in einer
  Python-Datei: er nimmt jede Aufnahme, die ein Telefon macht, druckt das Paket und prüft die CRC.
  Unter Windows ist [`tools/afsk/AfskListener`](../../tools/afsk/AfskListener) ([`AfskListener.exe`](../../tools/afsk/AfskListener.exe), .NET 8) derselbe Empfänger als Ein-Knopf-App: *Listen*
  drücken, die Uhr ans Mikrofon halten, das Paket beim Eintreffen lesen.

</details>

## Bauen

```
git clone https://github.com/amorroma1/mfd-24.git
cd mfd-24
./gradlew :app:assembleEarthDebug
```

JDK 17+ und das Android-SDK. 193 JVM-Tests: `./gradlew :app:testEarthDebugUnitTest`.

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
