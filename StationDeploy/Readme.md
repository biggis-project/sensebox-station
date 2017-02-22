Dieses Skript richtet SenseBox-Knoten zur Verwendung mit LoRa via TTN oder Codekunst ein.
Dazu legt es bei TheThingsNetwork ein Device an (oder verwendet bei schon vorhandenen Devices deren DevEUI und AppKey)
und kompiliert die Firmware mit den entsprechenden Werten.

Installation
============

* ttnctl in passender Version von https://www.thethingsnetwork.org/docs/network/cli/quick-start.html runterladen und nach /usr/local/bin entpacken

* Folgende Perl-Module installieren (unter Ubuntu: _apt install libsysadm-install-perl_)
** Sysadm::Install

* Platform.io installieren (http://docs.platformio.org/en/latest/installation.html)

* Firmware-Source runterladen

* Pfade am Anfang des Scripts anpassen

Nutzung
=======
$ station-deploy.pl [<device id>]

Wenn keine Device-ID angegeben wird, wird diese auto-generiert.

Vergebene Device-IDs können mit ttnctl devices list angezeigt werden.
