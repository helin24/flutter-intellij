// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cli_util/cli_logging.dart';
import 'package:git/git.dart';

import 'build_spec.dart';
import 'globals.dart';

Future<int> exec(String cmd, List<String> args, {String? cwd}) async {
  if (cwd != null) {
    log(_shorten('$cmd ${args.join(' ')} {cwd=$cwd}'));
  } else {
    log(_shorten('$cmd ${args.join(' ')}'));
  }

  final process = await Process.start(cmd, args, workingDirectory: cwd);
  _toLineStream(process.stderr).listen(log);
  _toLineStream(process.stdout).listen(log);

  return await process.exitCode;
}

Future<String> makeDevLog(BuildSpec spec) async {
  if (lastReleaseName.isEmpty) {
    return '';
  } // The shallow on travis causes problems.
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var since = lastReleaseName;
  var processResult = await gitDir.runCommand([
    'log',
    '--oneline',
    '$since..HEAD',
  ]);
  String out = processResult.stdout as String;
  var messages = out.trim().split('\n');
  var devLog = StringBuffer();
  devLog.writeln('## Changes since ${since.replaceAll('_', ' ')}');
  for (var m in messages) {
    devLog.writeln(m.replaceFirst(RegExp(r'^[A-Fa-f\d]+\s+'), '- '));
  }
  devLog.writeln();
  return devLog.toString();
}

Future<DateTime> dateOfLastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult = await gitDir.runCommand([
    'branch',
    '--list',
    '-v',
    '--no-abbrev',
    lastReleaseName,
  ]);
  String out = processResult.stdout as String;
  var logLine = out.trim().split('\n').first.trim();
  var match = RegExp(
    r'release_\d+\s+([A-Fa-f\d]{40})\s',
  ).matchAsPrefix(logLine);
  var commitHash = match!.group(1);
  processResult = await gitDir.runCommand([
    'show',
    '--pretty=tformat:"%cI"',
    commitHash!,
  ]);
  out = processResult.stdout as String;
  var date = out.trim().split('\n').first.trim();
  return DateTime.parse(date.replaceAll('"', ''));
}

Future<String> lastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult = await gitDir.runCommand([
    'branch',
    '--list',
    'release_*',
  ]);
  String out = processResult.stdout as String;
  var release = out.trim().split('\n').last.trim();
  if (release.isNotEmpty) return release;
  processResult = await gitDir.runCommand([
    'branch',
    '--list',
    '-a',
    '*release_*',
  ]);
  out = processResult.stdout as String;
  var remote =
      out.trim().split('\n').last.trim(); // "remotes/origin/release_43"
  release = remote.substring(remote.lastIndexOf('/') + 1);
  await gitDir.runCommand(['branch', '--track', release, remote]);
  return release;
}

final Ansi ansi = Ansi(true);

void separator(String name) {
  log('');
  log('${ansi.red}${ansi.bold}$name${ansi.none}', indent: false);
}

void log(String s, {bool indent = true}) {
  indent ? print('  $s') : print(s);
}

void createDir(String name) {
  final dir = Directory(name);
  if (!dir.existsSync()) {
    log('creating $name/');
    dir.createSync(recursive: true);
  }
}

Future<int> curl(String url, {required String to}) async {
  return await exec('curl', ['-o', to, url]);
}

/// Remove the directory without exceptions if it does not exists.
Future<void> removeAll(String dir) async {
  await Directory(dir).delete(recursive: true).then((_) {}, onError: (_) {});
}

bool isNewer(FileSystemEntity newer, FileSystemEntity older) {
  return newer.statSync().modified.isAfter(older.statSync().modified);
}

void _checkGitDir() async {
  var isGitDir = await GitDir.isGitDir(rootPath);
  if (!isGitDir) {
    throw 'the current working directory is not managed by git: $rootPath';
  }
}

String _shorten(String str) {
  return str.length < 200
      ? str
      : '${str.substring(0, 170)} ... ${str.substring(str.length - 30)}';
}

Stream<String> _toLineStream(Stream<List<int>> s) =>
    s.transform(utf8.decoder).transform(const LineSplitter());

String readTokenFromKeystore(String keyName) {
  var env = Platform.environment;
  var base = env['KOKORO_KEYSTORE_DIR'];
  var id = env['FLUTTER_KEYSTORE_ID'];
  var name = env[keyName];

  var file = File('$base/${id}_$name');
  return file.existsSync() ? file.readAsStringSync() : '';
}

int get devBuildNumber {
  // The dev channel is automatically refreshed weekly, so the build number
  // is just the number of weeks since the last stable release.
  var today = DateTime.now();
  var daysSinceRelease = today.difference(lastReleaseDate).inDays;
  var weekNumber = daysSinceRelease ~/ 7 + 1;
  return weekNumber;
}

String buildVersionNumber(BuildSpec spec) {
  var releaseNo = spec.isDevChannel ? _nextRelease() : spec.release;
  if (releaseNo == null) {
    releaseNo = 'SNAPSHOT';
  } else {
    releaseNo = '$releaseNo.$pluginCount';
    if (spec.isDevChannel) {
      releaseNo += '-dev.$devBuildNumber';
    }
  }
  return releaseNo;
}

String _nextRelease() {
  var current = RegExp(
    r'release_(\d+)',
  ).matchAsPrefix(lastReleaseName)!.group(1);
  var val = int.parse(current!) + 1;
  return '$val.0';
}
