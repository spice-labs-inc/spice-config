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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The one mapping between a setting's three names.
 *
 * <pre>
 *   config file      [analysis] max_records
 *   flag             --max-records
 *   environment      SPICE_ANALYSIS_MAX_RECORDS
 * </pre>
 *
 * <p>The rule has no exceptions, so nobody has to remember a table of them: the config key
 * is canonical, the flag is its kebab-case form, and the environment variable is the prefix,
 * the group and the key in upper snake case. Standalone components differ only in the
 * prefix — {@code GOATRODEO_ANALYSIS_MAX_RECORDS} names the same setting as
 * {@code SPICE_ANALYSIS_MAX_RECORDS}.
 *
 * <p>Both directions are here on purpose. Rendering names is what documentation and
 * {@code explain} need; reading them back is what lets the environment be scanned without a
 * schema, so a component need not enumerate its keys for its settings to be reachable.
 */
public final class Names {

  private Names() {}

  /** The flag for a config key: {@code max_records} to {@code --max-records}. */
  public static String flag(String key) {
    return "--" + key.replace('_', '-');
  }

  /**
   * The flag for a key that two of a command's groups both define.
   *
   * <p>The only variation in the whole scheme, and still derivable rather than remembered:
   * {@code --analysis-threads} when plain {@code --threads} would be ambiguous.
   */
  public static String qualifiedFlag(String group, String key) {
    return "--" + group.replace('_', '-') + "-" + key.replace('_', '-');
  }

  /** The config key a flag names: {@code --max-records} to {@code max_records}. */
  public static String key(String flag) {
    String bare = flag.startsWith("--") ? flag.substring(2) : flag;
    return bare.replace('-', '_');
  }

  /**
   * The environment variable for a setting.
   *
   * @param prefix the component prefix without its underscore: {@code SPICE},
   *     {@code GOATRODEO}, {@code ALLSPICE}
   */
  public static String environmentVariable(String prefix, String group, String key) {
    return prefix.toUpperCase() + "_" + group.toUpperCase() + "_" + key.toUpperCase();
  }

  /**
   * The setting an environment variable names, if it names one at all.
   *
   * <p>Matched against the groups in play rather than parsed, which settles the one
   * ambiguity in the scheme: with groups {@code state} and {@code state_repo},
   * {@code SPICE_STATE_REPO_PATH} could be either {@code [state] repo_path} or
   * {@code [state_repo] path}. The longest matching group wins, deterministically, and a
   * variable naming no known group is not a setting — which is what keeps this from
   * colliding with the wrapper's own {@code SPICE_IMAGE} and {@code SPICE_CACHE_DIR}.
   *
   * @return the group and key, or empty if this variable is not a setting for these groups
   */
  public static Optional<Setting.Name> fromEnvironmentVariable(
      String prefix, Collection<String> groups, String variable) {
    String head = prefix.toUpperCase() + "_";
    if (!variable.startsWith(head)) {
      return Optional.empty();
    }
    String rest = variable.substring(head.length());
    return groups.stream()
        .sorted(Comparator.comparingInt(String::length).reversed())
        .filter(group -> rest.startsWith(group.toUpperCase() + "_"))
        .findFirst()
        .map(group -> new Setting.Name(group, rest.substring(group.length() + 1).toLowerCase()));
  }

  /** The table path a command-scoped group sits at: {@code registry.analysis}. */
  public static String commandTable(List<String> commandPath, String group) {
    return String.join(".", commandPath) + "." + group;
  }
}
