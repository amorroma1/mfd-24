# MFD-24

*Multi-Function Display, 24 heures : l'écran du cockpit, et la journée en un tour d'aiguille des heures.*

[![build](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml/badge.svg)](https://github.com/amorroma1/mfd-24/actions/workflows/build.yml)

[English](../../README.md) · **Français** · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [中文](README.zh.md)

Un cadran d'instrument 24 heures pour ceux qui prennent le quart — pilotes, marins, opérateurs de
drones, officiers de permanence. Un tour d'aiguille des heures par jour, votre quart dessiné comme
un arc de ce jour, un dispositif d'homme mort qui monte en SOS et laisse une trace au journal, et la
télémétrie dont un quart a réellement besoin.

Tout fonctionne hors ligne, sauf la météo et la vérification quotidienne des versions. Pas d'application compagnon, pas de compte, pas de
télémétrie vers qui que ce soit. GPL-3.0.

| Interactif | Always-on |
|---|---|
| ![](../screenshots/earth.png) | ![](../screenshots/ambient.png) |

*Un quart en cours à New York JFK. Le même cadran sur les deux images : l'always-on est le
cadran interactif, atténué, sans le curseur des secondes ni le lavis de fond.*

**Wear OS 3.0+ (API 30).** Conçu et porté sur une TicWatch Pro 3 Ultra (454 × 454).
**[Installer →](INSTALL.fr.md)**

---

## L'essentiel

| | |
|---|---|
| **Cadran 24 h** | Un tour par jour. Midi en haut ou minuit en haut. Les minutes sur leur propre anneau ; la seconde est un curseur qui saute de graduation en graduation, rien ne balaie. |
| **Minuteur de quart** | Prise immédiate ou planifiée. Un arc couvre les heures de quart et s'atténue à mesure qu'elles sont tenues ; `DUTY: 04:40 REM` fait le compte à rebours. La prise et la relève sonnent et vibrent, même écran éteint. Un quart relevé s'efface de lui-même une heure plus tard. |
| **Homme mort** | Désactivé par défaut. Toutes les 5/10/15 min il lance un appel à acquitter ; trente secondes plus tard il sonne le SOS en salves et laisse `MAN DOWN` — homme à terre — avec l'instant Zulu sur le cadran. En option, il note votre pouls à l'appel non acquitté, à côté de votre dernière lecture en mouvement. |
| **Export du journal** | Le journal de bord quitte la montre en **code QR** pour l'appareil photo d'un téléphone ou en **son** — Bell 202 AFSK, décodé par `minimodem`. Le son emporte tout ; le QR emporte ce qui tient, et le dit. [Canaux et limites ↓](#export) |
| **Zulu + groupe date-heure** | `Z 11NOV 13:05`, l'heure dans laquelle s'écrit une entrée de journal. |
| **Météo** | Température, conditions en abrégés METAR et QNH. Sur un aérodrome, c'est le **METAR du terrain lui-même** ; partout ailleurs, Open-Meteo. Rafraîchie toutes les 30 minutes. |
| **Accrochage de site à 5 km** | Nomme l'aérodrome, le port, l'héliport ou la base de lancement le plus proche parmi 9 649 sites d'un index embarqué de 194 Ko. Les sites militaires sont dessinés en couleur d'accent. |
| **Nadir, soleil et lune** | L'arc diurne ombré sur l'anneau des heures, calculé hors ligne. La marque solaire et la marque lunaire sont à leur véritable angle horaire — pointez une marque vers l'astre lui-même et le cadran devient un **compas**. La lune porte sa phase réelle. |
| **Lectures capteurs** | Deux lectures optionnelles de part et d'autre du moyeu : fréquence cardiaque, les pas du jour, ou pression de station. Désactivées par défaut ; elles tournent écran allumé, et en always-on le pouls est relu une fois par minute tant que la montre est portée. [Comment cette fenêtre se règle ↓](#pulse-window) |
| **Always-on** | Le même cadran, atténué. `AUTO` le réduit à un pixel sur deux après le coucher du soleil — jamais pendant un quart de nuit. |
| **Démarrage direct** | S'affiche déjà rempli avant que la montre soit déverrouillée. |

## En action

| Un appel manqué, acquitté tard | De JFK à Heathrow, le vol entier | Réveil |
|---|---|---|
| ![](../media/mandown.gif) | ![](../media/flight.gif) | ![](../media/wake.gif) |
| Le SOS est resté sans acquittement : `MAN DOWN`, l'instant Zulu, une marque sur l'arc. Un toucher répond par `TAP AGAIN` ; un second, dans la seconde, l'efface — une manche de veste ne le peut pas. | BA 178 le 11 novembre 2026, l'horaire réel, en 36 secondes : un quart de huit heures pris à la porte d'embarquement, le site et son METAR disparus cinq minutes après la rotation, la cabine à 790 hPa, le coucher du soleil sur l'Atlantique, `EGLL` de retour en courte finale, et les aiguilles passées à Londres au sol. | Seule la lumière arrive. L'always-on porte les mêmes teintes, il n'y a donc rien à fondre. |

*Le vol dure 36 secondes à dessein — une journée entière pour qui veut en regarder une ; le sol
passe lentement et l'océan vite. L'atterrissage seul, à pleine vitesse :
[landing.gif](../media/landing.gif).*

<details>
<summary><b>Deux choses que l'on nous demande sur le vol</b></summary>

<br>

**Pourquoi les aiguilles ne changent-elles de fuseau qu'après l'atterrissage ?** Une montre prend
son fuseau du téléphone, et le téléphone trouve un réseau au sol. Jusque-là le cadran garde le
fuseau du départ — ce qui explique aussi pourquoi, au-dessus de l'océan, la marque solaire se tient
à droite de l'arc diurne : la marque est le véritable angle horaire du soleil, l'arc est le jour
dans le fuseau de la montre, et l'écart entre les deux est exactement le retard du fuseau. La
glissade au sol le referme.

**Qu'y a-t-il de réel ?** L'horaire est celui du BA 178 de ce jour-là et les positions suivent sa
route habituelle de l'Atlantique Nord, données au cadran comme des points GPS ; la météo et
l'astronomie sont le travail du cadran lui-même à partir de ces points. Le pouls, la pression
cabine et la batterie sont mis en scène sur l'émulateur, parce qu'un émulateur n'a pas de poignet.

</details>

## Installation

Téléchargez `app-earth-release.apk` depuis la
[dernière version](https://github.com/amorroma1/mfd-24/releases/latest) et installez-le en
sideload. Wear OS n'a pas d'interface de sideloading, c'est donc un travail pour ADB :

```
adb connect 192.168.1.50:41234
adb install -r app-earth-release.apk
```

Puis choisissez MFD-24 dans le sélecteur de cadrans de la montre. Pas d'ordinateur ? Un téléphone
peut exécuter la même poignée de main ADB :
**[Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2)**
ou **[Bugjaeger](https://play.google.com/store/apps/details?id=eu.sisik.hackendebug)**, tous deux
tiers, tous deux détaillés dans le guide.
**[Guide complet — trois routes, de « un téléphone et rien d'autre » à la compilation depuis le source →](INSTALL.fr.md)**

<details>
<summary><b>Les mises à jour, et pourquoi ce n'est pas sur Google Play</b></summary>

<br>

La montre interroge GitHub **une fois par jour, hors quart**, et vous prévient une fois par
version. Elle ne télécharge rien et n'installe rien : **Wear OS ne laisse pas une application en
installer une autre** (vérifié sur une TicWatch Pro 3 Ultra). `À PROPOS → VERSIONS` affiche la page
de la version en QR code pour l'appareil photo d'un téléphone, et l'installation reste
`adb install -r`. `À PROPOS → MISES À JOUR` désactive tout.

Il n'est pas sur Google Play à dessein : un dispositif d'homme mort ne doit pas être une
installation impulsive, les permissions dont il a légitimement besoin maintiendraient un projet de
loisir en révision permanente par le magasin, et un APK avec son SHA-256 dans les notes de version
se vérifie mieux qu'une fiche. Le raisonnement complet est dans
[INSTALL.fr.md](INSTALL.fr.md#pourquoi-mfd-24-nest-pas-sur-google-play).

</details>

<details>
<summary><b id="permissions">Permissions — rien n'est demandé à l'installation</b></summary>

<br>

La localisation est demandée depuis l'écran des réglages, parce qu'un cadran est un
`WallpaperService` et ne peut pas ouvrir de dialogue lui-même.

| Permission | Pour | Sans elle |
|---|---|---|
| `ACCESS_FINE_LOCATION` | la météo, le Nadir, l'accrochage de site | ces trois-là restent vides ; l'horloge et l'astronomie ne sont pas touchées |
| `ACCESS_BACKGROUND_LOCATION` | les mêmes, depuis la tâche de rafraîchissement de 30 minutes | la plateforme renvoie null plutôt qu'une erreur, ce qui ressemble exactement à une absence de signal |
| `WAKE_LOCK`, `FOREGROUND_SERVICE_HEALTH` | l'homme mort, qui doit continuer à mesurer écran éteint | cette fonction seulement |
| `VIBRATE` | la prise et la relève du quart, et le SOS | une prise et une relève silencieuses |
| `SCHEDULE_EXACT_ALARM` | une prise et une relève qui tombent à la minute | demandée mais **pas** requise ; sans elle, la prise ou la relève peut glisser de quelques minutes |
| `INTERNET` | Open-Meteo et METAR toutes les 30 min, plus le contrôle quotidien des versions | pas de météo, pas de contrôle des versions |
| `BODY_SENSORS` | la lecture fréquence cardiaque et le pouls dans un incident | des tirets ; la ligne `POULS AU JOURNAL` n'est pas proposée |
| `ACTIVITY_RECOGNITION` | la lecture pas ; sur Wear 5+, aussi ce qui permet au service d'homme mort de démarrer | des tirets ; sur les Wear récents, pas d'homme mort |
| `POST_NOTIFICATIONS` | la notification permanente de l'homme mort, et l'avis de nouvelle version, une fois par version | l'homme mort tourne sans s'annoncer |

Pas de contacts, pas de stockage, et aucun réseau au-delà de ces appels. Un point manuel saisi à la
main remplace entièrement la localisation — voir **Position** dans les réglages.

</details>

## Réglages

**Appui long sur le cadran, puis touchez le crayon.** Sept sections repliables, une seule ouverte
à la fois ; celle qui est ouverte est encadrée.

Les réglages suivent la langue de la montre — anglais, allemand, français, italien, japonais ou
chinois — dans le vocabulaire du service, pas celui du magasin d'applications : *Wache*, *quart*,
*guardia*, 当直, 值更. Le cadran lui-même reste en anglais sur toutes les montres : `DUTY`, `Z`,
`QFE` et la ligne METAR sont de la nomenclature d'instrument, pas de la prose.

| Sections | Quart | Planification | Affichage | À propos |
|---|---|---|---|---|
| ![](../screenshots/settings-sections.png) | ![](../screenshots/settings-duty.png) | ![](../screenshots/settings-schedule.png) | ![](../screenshots/settings-display.png) | ![](../screenshots/settings-about.png) |

- **Quart** — durée `4h` `8h` `12h` `LIBRE`, une prise planifiée, et une seule puce qui
  lit `PRENDRE LE QUART` ou un `FIN DE QUART` terre cuite. Une prise dans le passé ne peut pas être planifiée.
- **Homme mort** — marche/arrêt, intervalle d'appel, `VIBRATION`, `SIGNAL SOS`
  (MUET/MIN/MED/MAX), `POULS AU JOURNAL`, et le journal de bord avec son export.
- **Capteurs** — ce que montre chaque lecture, de part et d'autre du moyeu.
- **Position** — point GPS, ou point manuel aux coordonnées saisies à la main.
- **Affichage** — couleur lumineuse, haut du cadran, marque de minuit, Nadir, marques solaire et lunaire,
  météo, always-on, et `AIDE` pour les petits caractères sous chaque ligne.
- **Unités** — °C/°F, hPa/mmHg.
- **À propos** — version, le dépôt en QR code, le contrôle des versions.

**[Chaque ligne, avec le raisonnement (en anglais) →](../DESIGN.md#settings)**

## Le cadran, en détail

| ![](../screenshots/duty.png) | ![](../screenshots/ambient-auto.png) | ![](../screenshots/ambient-duty.png) |
|---|---|---|
| En plein quart : l'arc est le quart, la partie vive est ce qui reste à tenir. | `ALWAYS-ON > AUTO` allège le cadran après le coucher du soleil — moitié moins de lumière, moitié moins de temps d'exposition au marquage. | Un quart en cours garde le cadran complet toute la nuit : un quart de nuit est précisément le moment où le cadran se lit dans le noir. |

**[Comment ça marche — disposition, astronomie, la machine à états de l'homme mort, les tests (en anglais) →](../DESIGN.md)**

<details>
<summary><b>Notes — la fenêtre de pouls, le compte de pas, le matériel requis, et ce que ça pèse</b></summary>

<br>

<b id="pulse-window">La fenêtre de pouls en always-on.</b> Écran éteint, la lecture de fréquence
cardiaque n'est pas laissée en marche : une fois par minute le cadran ouvre une fenêtre de quinze
secondes, allume le capteur, prend la première lecture plausible et l'éteint à nouveau. Trois
minutes sans accrochage et le chiffre quitte le cadran, remplacé par des tirets. Trois fenêtres
vides d'affilée ralentissent aussi la cadence à une fenêtre par quart d'heure, jusqu'à ce qu'une
lecture, un poignet levé ou le front *portée* du détecteur de poignet lui-même y mette fin — parce
que le détecteur peut se tromper sur une table, et l'a fait, une nuit entière : six cents fenêtres
contre le bois. Quarante par nuit désormais, et rien ne change au poignet, où une lecture trois
minutes sans accrochage affiche déjà des tirets.

**D'où vient le compte de pas.** La ligne préfère le total du jour fourni par la plateforme
elle-même, via Health Services, parce que c'est le chiffre sur lequel le reste de la montre
s'accorde et qu'il ne dépend pas de la date d'installation de ce cadran. Quand une montre n'a
pas ce fournisseur, ou que le fournisseur se connecte puis ne dit rien, le cadran compte lui-même à
partir du compteur de pas matériel. Ce repli est exact **à partir du minuit local suivant**, parce
que la valeur du compteur est relevée à la frontière du jour ; le jour de l'installation, il ne
peut compter qu'à partir du moment où vous activez la lecture, et il affiche des tirets plutôt
qu'un `0` qu'il aurait inventé. Une montre testée ici rapporte son total « du jour » comme les pas
depuis le début de l'abonnement plutôt que depuis minuit — une particularité du fournisseur, pas
un réglage, et la raison pour laquelle la ligne se lit comme *des pas comptés*, pas comme une
statistique de forme.

**Écran.** Rond, et testé en **454 × 454**. Chaque position du cadran est une fraction du rayon,
les autres tailles rondes devraient donc convenir ; les écrans carrés ou tronqués en bas ne sont
pas prévus et rogneront l'échelle extérieure. Le texte est lui aussi dimensionné d'après le rayon —
le budget de largeur des affichages est de l'arithmétique, et un test échoue si une ligne devait
atteindre les chiffres des heures.

**Matériel et performances.** Wear OS 3.0+ (API 30), toute montre où la plateforme tourne ;
aucune fonction GPU au-delà d'un canvas ordinaire. `render()` n'alloue rien, il n'y a donc aucune
pause du ramasse-miettes à contourner, et la cadence d'images suit la demande : **une image par
seconde au repos**, 16 ms seulement pendant qu'une transition s'anime, et une image par minute en
always-on. Les capteurs optionnels sont le seul coût réel — la fréquence cardiaque est une
LED, et l'homme mort laisse l'accéléromètre accumuler ses lots dans le hub de capteurs
plutôt que de réveiller le processeur. Tout le reste tourne sur des valeurs en cache.

**Taille.** Environ **3,5 Mo** installé, dont à peu près 2,8 Mo de code, 190 Ko pour l'index de
sites embarqué et 220 Ko pour l'aperçu du sélecteur. L'index est stocké non compressé à dessein,
pour être lu sans décompression. Six locales — anglais, allemand, français, italien, japonais, chinois — pour les chaînes
de l'application ; les quatre-vingts autres locales de chaînes de bibliothèque, qu'elle ne
pourrait jamais afficher, coûtaient 260 Ko et restent dehors.

</details>

## Ce qu'il n'est pas

Un instrument personnel, pas un instrument certifié. L'homme mort est une aide non
certifiée : il peut être désactivé, suspendu par un bracelet lâche, ou retardé par les limites de
sommeil (Doze) de la plateforme elle-même. Le journal de bord couvre **un seul quart** — en
prendre un autre le vide, donc tout ce qui vaut d'être gardé en sort d'abord par `JOURNAL · QR + ♪`.
Rien ici ne remplace un appareil approuvé par un régulateur.

<details>
<summary><b id="export">Exporter le journal de bord — deux canaux, et leurs limites</b></summary>

<br>

`JOURNAL · QR + ♪`, dans la section homme mort, met le journal sur les deux canaux qu'une montre possède
vraiment : un **QR code** pour l'appareil photo du téléphone déjà pointé dessus, et le **son** —
Bell 202 AFSK, 1200 Bd, 8N1, la couche physique qu'utilise l'APRS, de sorte que l'autre bout est
`minimodem --rx 1200` tenu contre le boîtier. Les deux portent le même paquet en texte brut : un
en-tête, l'indicatif de l'instrument, une ligne `WATCH` avec la prise, la relève et la durée du
quart, un incident par ligne en heure Zulu — avec `HR` et `BASE … AT` quand `POULS AU JOURNAL` est
activé — et une ligne `END` portant le compte et un CRC32 de tout ce qui précède, pour qu'une
photo floue ou un enregistrement bruité échoue bruyamment plutôt que de perdre une ligne en
silence.

- **Le journal garde les 32 incidents les plus récents du quart en cours**, 30 jours au plus.
  Prendre le quart suivant le vide ; exportez d'abord.
- **Le son porte toujours tout le journal** — dix-huit secondes de tonalités au plafond. La
  transmission démarre à l'ouverture de l'écran et se rejoue d'un toucher.
- **Le QR porte les entrées les plus récentes qui tiennent** dans le plus grand code qu'un écran
  de 1,2 pouce peut afficher : les 32 quand les lignes sont de simples instants, environ 14 quand
  chaque ligne porte un pouls et sa référence. Un code tronqué le dit — sa ligne `END` lit
  `END 14 OF 32`, jamais `END 14`. Pour chaque ligne d'un journal aussi long, utilisez le son.
- **Lecture seule.** Il n'y a pas de chemin d'import, donc rien de ce qui lit un paquet ne peut
  écrire l'enregistrement.
- **L'indicatif est dérivé, jamais saisi.** `KILO-15` est un SHA-256 salé de l'identifiant
  Android de la montre, projeté sur vingt préfixes × 99 numéros : la même montre donne le même
  indicatif à chaque réinstallation, une remise à zéro d'usine en attribue un nouveau, et
  l'identifiant lui-même ne quitte jamais la montre. Un nom parmi 1 980 sur un réseau, pas un
  numéro de série — le paquet ne porte rien de plus fin.
- **Pas de minimodem ?** [`tools/afsk/decode_afsk.py`](../../tools/afsk/decode_afsk.py) `enregistrement.m4a` est le même récepteur en
  un fichier Python : il prend n'importe quel enregistrement fait au téléphone, imprime le paquet
  et vérifie le CRC. Sous Windows, [`tools/afsk/AfskListener`](../../tools/afsk/AfskListener) ([`AfskListener.exe`](../../tools/afsk/AfskListener.exe), .NET 8) est le même récepteur en
  application à un bouton : *Listen*, la montre contre le micro, le paquet se lit à l'arrivée.

</details>

## Compilation

```
git clone https://github.com/amorroma1/mfd-24.git
cd mfd-24
./gradlew :app:assembleEarthDebug
```

JDK 17+ et le SDK Android. 193 tests JVM : `./gradlew :app:testEarthDebugUnitTest`.

## Comment il a été construit

Conçu, décidé et porté par une seule personne, écrit avec Claude (Anthropic) en binôme de
programmation. Chaque constante qui pouvait être mesurée l'a été sur la montre plutôt que déduite,
chaque invariant est fixé par un test, et rien n'est publié qui n'ait tourné au poignet. Les notes
de travail dans `CLAUDE.md` sont la trace de cette collaboration et restent dans le dépôt à dessein.

## Licence

GPL-3.0-or-later — voir [LICENSE](../../LICENSE). Les bases navales et les héliports
proviennent d'OpenStreetMap (© contributeurs OpenStreetMap, **ODbL**), donc tout index construit
à partir d'eux porte aussi cette licence ; aérodromes, ports et bases de lancement sont tenus à
jour dans ce dépôt. Météo de [Open-Meteo](https://open-meteo.com/) (CC BY 4.0) et
[aviationweather.gov](https://aviationweather.gov/) (NOAA américaine, domaine public).
