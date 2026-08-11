// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package io.spicelabs.config;

import java.util.Map;
import java.util.Set;

/**
 * The {@code [logging]} group: how much a run says, and where it says it.
 *
 * <p>Every Spice command-line tool logs, every one of them had its own answer, and none of
 * those answers was configurable. `spice` took `--log-level` and `--log-file`; Allspice took
 * `--verbose`; Goat Rodeo and Sassafras took nothing at all and shipped a fixed
 * {@code logback.xml}. Someone debugging a run through three components had three different
 * levers, two of which did not exist.
 *
 * <p>One group, claimed by every tool, with the same two keys and the same names in every
 * form:
 *
 * <pre>
 *   [logging] level   →  --log-level  →  SPICE_LOGGING_LEVEL
 *   [logging] file    →  --log-file   →  SPICE_LOGGING_FILE
 * </pre>
 *
 * <h2>What this class does and does not do</h2>
 *
 * <p>It defines the group — the key names, the accepted levels, the defaults — and nothing
 * else. Applying a level to a logging backend is left to each component, because binding a
 * backend here would impose one on every consumer of this library, and this library's whole
 * argument is that it should impose as little as possible.
 *
 * <p>In practice every Spice tool uses logback, so each applies these in a handful of lines
 * at startup. What matters is that they agree on the names and the meanings, which is what
 * lives here.
 */
public final class Logging {

  /** The group name. */
  public static final String GROUP = "logging";

  /** The keys this group accepts. */
  public static final Set<String> KEYS = Set.of("level", "file");

  /** The levels accepted for {@code [logging] level}, loudest last. */
  public static final java.util.List<String> LEVELS =
      java.util.List.of("error", "warn", "info", "debug", "trace");

  private Logging() {}

  /** The group's defaults, as a layer. */
  public static Map<String, Map<String, Object>> defaults() {
    return Map.of(GROUP, Map.of("level", "info"));
  }

  /**
   * The level to apply, normalised to upper case.
   *
   * @throws ConfigurationException if the value is not a level, naming what is accepted —
   *     a misspelt level that silently means "info" is how a run stays quiet when someone
   *     has asked it not to be
   */
  public static String level(Resolution resolved) {
    String value = resolved.setting(GROUP, "level").map(Setting::asString).orElse("info");
    String normalised = value.trim().toLowerCase();
    if (!LEVELS.contains(normalised)) {
      throw new ConfigurationException(
          "[logging] level must be one of " + String.join(", ", LEVELS) + ", got: " + value);
    }
    return normalised.toUpperCase();
  }

  /** The file to log to as well as the console, if one was asked for. */
  public static java.util.Optional<String> file(Resolution resolved) {
    return resolved.setting(GROUP, "file").map(Setting::asString).filter(s -> !s.isBlank());
  }

  /** Keys in the group that are not settings, for a component's unknown-key check. */
  public static java.util.List<String> unknownKeys(Resolution resolved) {
    return resolved.group(GROUP).keySet().stream().filter(k -> !KEYS.contains(k)).sorted().toList();
  }
}
