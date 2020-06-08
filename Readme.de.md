# RubRubMusic
RubRubMusic ist ein Mehrzweckbot für Discord. 

## Einladung zum Server
Wenn Sie den Bot zu Ihrem Server einladen will, können Sie diesen Link verwenden: https://discord.com/api/oauth2/authorize?client_id=698581641190178876&permissions=8&scope=bot
Kontaktieren Sie den Entwickler, wenn Sie glauben, dass der Link nicht mehr gültig ist.

## Liste der Funktionen
1. JShell
2. Musikabspielen

## Liste aller Befehle
Alle von RubRubMusic unterstützten Befehle beginnen mit diesem Symbol: #
### JShell-Befehle
VORSICHT: Die folgenden 2 Befehle haben nichts zu tun mit der Musikabspielenfunktion des Bots.
#### bind
Aus technischen Gründen kann der Bot momentan nur an einen Kanal von einem Server gebunden werden. Daher müssen Sie zuerst ```#bind``` eingeben, bevor Sie JShell benutzen können.

#### unbind
Dieser Befehl wird benutzt, wenn Sie JShell vom Kanal trennen wollen.

### Musikabspielen
#### play
1. Der einzige Argumenttyp, den der Bot akzeptiert, ist die URL eines YouTube-Videos.
2. Wenn es keinen Sprachkanal gibt, den "Music" heißt, wird der Bot zuerst so einen Kanal erstellen. Die Musik kann nur in diesem Kanal abgespielt werden.

### Verwaltungsbefehle
Schauen Sie sich den Quellcode an, um zu erfahren, welche Optionen unterstützt sind.
.
.
.
Just kidding! Hier sind die Optionen.
Um zu wissen, was die Argumente der folgenden Befehle wirklich bedeuten, müssen Sie zuerst verstehen, dass der Bot einen "Startup-Sound-Effect" abspielt, wenn er einen Kanal betritt. Das gleiche gilt, wenn er einen Kanal verlässt.
#### set [startup/shutdown] {YouTube-URL}
Dieser Befehl stellt den Startup/Shutdown-Soundeffekt des Bots fest.

#### configure [startup/shutdown] {Modus}
Dieser Befehl bestimmt, wie der Bot seinen Startup/Shutdown-Soundeffekt aus einer Liste auswählt, wenn er einen Kanal betritt oder verlässt.

#### add [startup/shutdown] {YouTube-URL}
Dieser Befehl fügt der Liste der Startup/Shutdown-Soundeffekte ein neues Element hinzu.


## Kontakt
Wenn Sie bei der Verwendung des Bots Probleme begegnen, erstellen Sie bitten ein neues Issue in diesem Repository. Der Entwickler wird das Problem beheben, wenn er Zeit hat.
