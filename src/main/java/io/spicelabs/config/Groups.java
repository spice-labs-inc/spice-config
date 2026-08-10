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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks on a config file as a whole, rather than on one command's view of it.
 *
 * <p>Separate from {@link Resolver} because a resolver is built for the command being run
 * and knows only that command's groups. Whether a table is a typo depends on every group
 * every command claims, plus every command name — knowledge only the host has. Putting the
 * check here keeps the resolver from pretending to an authority it does not have.
 */
public final class Groups {

  private Groups() {}

  /**
   * Top-level tables that no command claims and no command is named after.
   *
   * <p>A misspelled group is otherwise perfectly silent: the file parses, the run proceeds,
   * and the setting does nothing at all. The whole cost of catching it is this set
   * difference, and it needs no knowledge of what any group contains.
   *
   * @param root the parsed config file
   * @param claimed every group claimed by any command, built-in or plugin
   * @param commands every top-level command name, since {@code [survey.inventory]} is a
   *     command scope rather than a group
   * @return the unrecognised table names, in the order they appear in the file
   */
  public static List<String> unclaimed(
      Map<String, Object> root, Collection<String> claimed, Collection<String> commands) {
    Set<String> known = new java.util.HashSet<>(claimed);
    known.addAll(commands);
    List<String> unknown = new ArrayList<>();
    root.forEach(
        (name, value) -> {
          if (value instanceof Map<?, ?> && !known.contains(name)) {
            unknown.add(name);
          }
        });
    return unknown;
  }

  /**
   * Group names that are also command names.
   *
   * <p>At the root of a file a table is either a group or a command's scope, so
   * {@code [registry.analysis]} can only mean "the analysis group, for the registry command"
   * if nothing called {@code registry} is also a group. A collision does not produce a wrong
   * answer so much as two defensible answers, which is worse: the file's meaning would depend
   * on which reading the reader had in mind.
   *
   * <p>This is a check on the <em>programs</em>, not on any user's file, so it belongs in a
   * test rather than in a run — nobody can fix it by editing their configuration.
   */
  public static List<String> collisions(
      Collection<String> claimed, Collection<String> commands) {
    Set<String> commandNames = new java.util.HashSet<>(commands);
    return claimed.stream().filter(commandNames::contains).sorted().toList();
  }

  /**
   * A message naming the unrecognised tables, or empty if there are none.
   *
   * <p>Phrased as a warning rather than an error: an unknown table might belong to a plugin
   * that is not installed in this run, and refusing to start would make a shared config file
   * unusable across machines with different plugins.
   */
  public static java.util.Optional<String> describeUnclaimed(List<String> unclaimed) {
    if (unclaimed.isEmpty()) {
      return java.util.Optional.empty();
    }
    String tables = String.join(", ", unclaimed.stream().map(name -> "[" + name + "]").toList());
    return java.util.Optional.of(
        "No command reads " + tables + " — check the spelling, or the plugin may not be installed");
  }
}
