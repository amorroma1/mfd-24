# MFD-24 — press kit

Everything in this folder may be reproduced freely in coverage of the project. Screenshots are
the real face on a 454 × 454 Wear OS display, staged at a known place and time (a pilot's morning
departure from New York JFK) so that every figure on them is genuine; nothing is mocked up.

**Project:** MFD-24 — Multi-Function Display, 24 hours
**Repository:** https://github.com/amorroma1/mfd-24
**Releases:** https://github.com/amorroma1/mfd-24/releases (one current release at a time; its
page carries the SHA-256 of the APK)
**Licence:** GPL-3.0-or-later; map data from OpenStreetMap under ODbL
**Platform:** Wear OS 3 and 4 (API 30–33). Built and worn on a TicWatch Pro 3 Ultra (Wear OS 3.5);
Wear OS 4 exercised on the emulator. Wear OS 5 and newer refuse AndroidX watch faces — see the
README's Compatibility section
**Price:** none. No account, no store, no cloud
**Author:** Roman Kravtsov, via GitHub (https://github.com/amorroma1)
**Documentation:** English plus German, French, Italian, Japanese and Chinese

---

## One line

A 24-hour instrument dial for Wear OS with a duty arc, a dead-man's monitor, METAR weather, sun
and moon — offline, open source, nothing leaves the watch.

## 50 words

MFD-24 turns a Wear OS watch into a 24-hour instrument dial for people who stand watches: pilots,
sailors, crews and anyone on duty. One hand, one revolution a day, a booked watch drawn as an arc,
a dead-man's monitor that escalates when you stop answering, METAR weather and the nearest
field or port. Offline, open source, no account.

## 150 words

MFD-24 is a watch face for Wear OS built around one idea: the day is a single revolution of one
hand. On that 24-hour dial it draws the daylight band for wherever you are, the sun and the moon
at their true hour angles, and the watch you are standing — booked in advance or started on the
spot — as an arc the hand travels along, with the time remaining in the hub.

Underneath sits a dead-man's monitor. Switched on for a watch, it asks for a flick of the wrist at
an interval you set and escalates if nobody answers: a nudge, a thirty-second window, then an SOS
on the speaker and the vibrator. An unanswered check is recorded as an incident, marked on the
arc and kept in a log that leaves the watch as a QR code or as audio tones a laptop can decode.

Around that: METAR from the nearest aerodrome with a fallback forecast, the nearest airfield,
port, helipad or spaceport from an on-watch index of 9 649 sites, optional heart rate, steps and
station pressure. Everything runs on the watch; the only network traffic is the weather and a
daily check for a new release. GPL-3.0, documentation in six languages.

## What it is not

MFD-24 is not a medical device and is not certified as a substitute for any certified
hardware-and-software system. The vigilance monitor is an uncertified aid for people who already
stand watches, and the software is supplied as is, without warranty, under the GPL.

---

## The same, in the documentation's other languages

**Deutsch.** MFD-24 macht aus einer Wear-OS-Uhr ein 24-Stunden-Instrumentenzifferblatt für
Menschen, die Wache gehen: ein Zeiger, eine Umdrehung pro Tag, die angetretene Wache als Bogen,
ein Totmann-Monitor, der eskaliert, wenn niemand quittiert, METAR-Wetter und der nächste Platz
oder Hafen. Offline, quelloffen, ohne Konto. Kein Medizinprodukt, kein Ersatz für zertifizierte
Systeme.

**Français.** MFD-24 fait d'une montre Wear OS un cadran-instrument de 24 heures pour ceux qui
prennent le quart : une aiguille, un tour par jour, le quart en cours dessiné en arc, un
dispositif homme mort qui monte en puissance quand personne n'acquitte, la météo METAR et le
terrain ou le port le plus proche. Hors ligne, libre, sans compte. Ni dispositif médical, ni
substitut d'un système certifié.

**Italiano.** MFD-24 trasforma un orologio Wear OS in un quadrante-strumento di 24 ore per chi
monta di guardia: una lancetta, un giro al giorno, la guardia in corso disegnata come arco, un
uomo morto che escala quando nessuno tacita, il meteo METAR e il campo o il porto più vicino.
Offline, open source, senza account. Non è un dispositivo medico né sostituisce sistemi
certificati.

**日本語.** MFD-24 は Wear OS の時計を、当直に立つ人のための 24 時間計器ダイヤルにします。針は一本、
一日一周。進行中の当直は円弧で示され、応答が途絶えるとデッドマン装置が段階的に警報を上げます。
METAR 気象と最寄りの飛行場・港を表示。オフライン、オープンソース、アカウント不要。医療機器ではなく、
認証済みシステムの代替でもありません。

**中文.** MFD-24 把 Wear OS 手表变成一只为值更者设计的 24 小时仪表表盘：一根指针，一天一圈，
当前值更以圆弧显示；失能警报在无人应答时逐级升级；显示 METAR 气象和最近的机场或港口。
离线、开源、无需账号。它不是医疗器械，也不能替代任何经认证的系统。

---

## Files

| File | What it is |
|---|---|
| `mfd-24-dial.png`, `@2x` | The dial, a watch under way at New York JFK |
| `mfd-24-always-on.png`, `@2x` | The same face in always-on |
| `mfd-24-duty-arc.png`, `@2x` | A booked watch on the 24-hour arc |
| `mfd-24-man-down.png`, `@2x` | An unanswered check: MAN DOWN on the dial |
| `mfd-24-settings.png`, `@2x` | The settings editor on the watch |
| `social-preview.png` | 1280 × 640 card for link previews |

Moving pictures live beside the README: a day flight (`docs/media/flight.gif`, `.mp4`), a landing
(`landing.gif`), the dead-man's escalation (`mandown.gif`) and the wake from always-on
(`wake.gif`). All were recorded from the face itself.

The pictures are regenerated by `tools/press/make_press_kit.py` from `docs/screenshots`, so a
re-shoot updates the kit in one command.
