#!/usr/bin/env perl

use strict;
use warnings;
use utf8;

use v5.14;

use Sysadm::Install qw(tap);

our $ttnAppId = 'fzi-biggis-sensors';
our $nodePrefix = 'fzi-biggis-sensor-';
our $ttnctl = '/usr/local/bin/ttnctl';
our $pio = '/usr/local/bin/platformio';
our $srcDir = '/home/lutz/BigGIS/sensebox-station/LoRaSenseNode';

our ($devId) = @ARGV;

ttnCheckLogin();
my ($appEUI, $devEUI, $appKey) = getTtnInfo();
programDevice($devId, $appEUI, $devEUI, $appKey);

sub ttnCheckLogin {
  my ($stdout, $stderr, $rc) = tap( $ttnctl, 'user' );

  if ($stdout =~ /No account information found/) {
    say "Nicht eingeloggt. Bitte mit ttnctl user login <access code> einloggen. Der Access Code kann im Webinterface (https://account.thethingsnetwork.org/) abgefragt werden.";
    exit -1;
  }

  ##TODO: Problem. Wenn $ttnAppId nicht existiert, fragt ttnctl interaktiv, was man will und entsprechend hängt das (endlos)
  ##      Um das zu fixen, müsste man statt Sysadm::Install::tap() dings verwenden (TODO: wie heißt es?)
  ($stdout, $stderr, $rc) = tap( $ttnctl, 'applications', 'select', $ttnAppId );

  ### erstmal gehen wir davon aus, dass das passt, deshalb keine Prüfung der Ausgabe
}

sub getTtnInfo {
  my ($appEUI, $devEUI, $appKey) = ttnCheckDevice($devId);

  if ($devEUI and $appKey) {
    say "Device $devId existiert bereits. Verwende AppEUI=$appEUI, DevEUI=$devEUI, AppKey=$appKey";

    return ($appEUI, $devEUI, $appKey)
  }

  if ($devId) {
    say "Device $devId existiert nicht. Lege es an.";
  }
  else {
    $devId = ttnAutoDevId();
    say "Erstelle Device $devId.";
  }

  ($appEUI, $devEUI, $appKey) = ttnRegisterDevice($devId);

  return ($appEUI, $devEUI, $appKey);
}

sub ttnRegisterDevice {
  my ($devId) = @_;

  my ($stdout, $stderr, $rc) = tap($ttnctl, 'devices', 'register', $devId);

  my ($appEUI, $appKey, $devEUI) = $stdout =~ /AppEUI\e\[\d+m=(\S+).*AppKey\e\[\d+m=(\S+).*DevEUI\e\[\d+m=(\S+)/;

  unless ($devEUI and $appKey) {
    say "Can't find AppKey and DevEUI in output of ttnctl.";
    exit -1;
  }

  return ($appEUI, $devEUI, $appKey);
}

sub ttnCheckDevice {
  my ($devId) = @_;

  return unless $devId;

  my ($stdout, $stderr, $rc) = tap($ttnctl, 'devices', 'info', $devId);

  return if ($stdout =~ /Could not get existing device/); ### existiert nicht

  my ($appEUI) = $stdout =~ /AppEUI: ([0-9A-F]+)/;
  my ($devEUI) = $stdout =~ /DevEUI: ([0-9A-F]+)/;
  my ($appKey) = $stdout =~ /AppKey: ([0-9A-F]+)/;

  return ($appEUI, $devEUI, $appKey);
}

sub ttnAutoDevId {
  my ($stdout, $stderr, $rc) = tap($ttnctl, 'devices', 'list');

  my @lines = split /\n/, $stdout;

  shift @lines while $lines[0] !~ /^DevID/; ### Schrott davor weg
  shift @lines; ### Header weg
  pop @lines while $lines[-1] ne ''; ### Schrott danach weg
  pop @lines; ### Leerzeile weg

  my @devIds = map { /^(\S+)/ } @lines;

  my @autoNodes = grep /^$nodePrefix\d+$/, @devIds;
  my @nums = sort { $a <=> $b } map { s/^$nodePrefix//r } @autoNodes;

  my $newId = $nums[-1] + 1;

  return "$nodePrefix$newId";
}

sub programDevice {
  my ($devId, $appEUI, $devEUI, $appKey) = @_;

  my $appEUIstr = join ', ', map { "0x$_" } reverse $appEUI =~ /(..)/g;
  my $devEUIstr = join ', ', map { "0x$_" } reverse $devEUI =~ /(..)/g;
  my $appKeyStr = join ', ', map { "0x$_" } $appKey =~ /(..)/g;

  local $ENV{PLATFORMIO_SRC_BUILD_FLAGS} = "-DAppEUI='{ $appEUIstr }' -DDevEUI='{ $devEUIstr }' -DAppKey='{ $appKeyStr }'";

  chdir($srcDir);
  system($pio, 'run', '--target=upload');
}
