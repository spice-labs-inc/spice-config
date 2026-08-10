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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Decides what every setting's value is, and says so when two sources disagree.
 *
 * <p>Sources are added in any order and applied in the order of {@link Origin.Layer}:
 *
 * <pre>
 *   defaults  &lt;  [group]  &lt;  [command.group]  &lt;  environment  &lt;  flag
 * </pre>
 *
 * <p>Resolution is therefore independent of the order a caller happens to supply sources in,
 * which matters because the caller is a CLI whose parse order is picocli's or scopt's
 * business, not configuration's.
 *
 * <h2>Groups</h2>
 *
 * <p>A resolver is built for the groups a command claims, and it will resolve nothing else.
 * That is what stops a command reading settings meant for another, and it is why a key in a
 * group nobody claims can be reported rather than silently ignored — see
 * {@link Resolution#unclaimed()}.
 *
 * <h2>Reporting overrides</h2>
 *
 * <p>When a value someone wrote displaces another value someone wrote, the reporter is
 * called. Displacing a <em>default</em> is not reported: that happens for every setting on
 * every run, and the noise would bury the handful of cases where two deliberate choices
 * conflict.
 */
public final class Resolver {

  private final String environmentPrefix;
  private final Set<String> groups;
  private final Consumer<String> reporter;

  private final Map<Setting.Name, Setting> resolved = new LinkedHashMap<>();

  /**
   * @param environmentPrefix the component's environment prefix without its underscore:
   *     {@code SPICE} when embedded, {@code GOATRODEO} or {@code ALLSPICE} standalone
   * @param groups the groups the command claims; nothing outside them is resolved
   * @param reporter where override messages go — a logger, usually
   */
  public Resolver(String environmentPrefix, Set<String> groups, Consumer<String> reporter) {
    this.environmentPrefix = environmentPrefix;
    this.groups = new LinkedHashSet<>(groups);
    this.reporter = reporter;
  }

  /** The groups this resolver will resolve. */
  public Set<String> groups() {
    return Set.copyOf(groups);
  }

  /**
   * A component's built-in defaults, as {@code group -> key -> value}.
   *
   * <p>Supplying these is optional. It costs nothing at resolution time and buys a complete
   * {@code explain} output — without them, a setting nobody has touched simply does not
   * appear, and "not shown" is a poor way to say "8".
   */
  public Resolver withDefaults(Map<String, Map<String, Object>> defaults) {
    defaults.forEach(
        (group, values) ->
            values.forEach((key, value) -> apply(group, key, value, Origin.defaultValue())));
    return this;
  }

  /**
   * The parsed config file.
   *
   * @param path where it was read from, for messages
   * @param root the whole file as nested maps
   * @param commandPath this command's path, e.g. {@code ["registry"]}; a group repeated
   *     under it overrides the shared one
   */
  public Resolver withFile(Path path, Map<String, Object> root, List<String> commandPath) {
    for (String group : groups) {
      table(root, List.of(group))
          .forEach((key, value) -> apply(group, key, value, Origin.sharedTable(path, group)));
    }
    if (!commandPath.isEmpty()) {
      for (String group : groups) {
        List<String> at = new ArrayList<>(commandPath);
        at.add(group);
        Origin origin = Origin.commandTable(path, String.join(".", commandPath), group);
        table(root, at).forEach((key, value) -> apply(group, key, value, origin));
      }
    }
    return this;
  }

  /**
   * The process environment.
   *
   * <p>Scanned rather than looked up key by key, so a component's settings are reachable
   * without it having to enumerate them anywhere. Variables that do not name a claimed group
   * are left alone, which is what keeps the wrapper's own {@code SPICE_IMAGE} and
   * {@code SPICE_CACHE_DIR} out of this.
   */
  public Resolver withEnvironment(Map<String, String> environment) {
    environment.forEach(
        (variable, value) ->
            Names.fromEnvironmentVariable(environmentPrefix, groups, variable)
                .ifPresent(
                    name ->
                        apply(name.group(), name.key(), value, Origin.environment(variable))));
    return this;
  }

  /**
   * A value given on the command line.
   *
   * <p>Null values are ignored, so a caller can hand over every option it has without first
   * working out which were actually given — which is exactly the shape picocli and scopt
   * leave their results in.
   *
   * @param flag the flag as the user would type it, for messages
   */
  public Resolver withFlag(String group, String key, Object value, String flag) {
    if (value != null) {
      apply(group, key, value, Origin.flag(flag));
    }
    return this;
  }

  /** A value from the Spice Pass, which no other source may displace. */
  public Resolver withPassValue(String group, String key, Object value, String claim) {
    if (value != null) {
      apply(group, key, value, Origin.pass(claim));
    }
    return this;
  }

  /** Everything resolved so far. */
  public Resolution resolve() {
    return new Resolution(List.copyOf(resolved.values()));
  }

  private void apply(String group, String key, Object value, Origin origin) {
    if (!groups.contains(group)) {
      return;
    }
    Setting.Name name = new Setting.Name(group, key);
    Setting existing = resolved.get(name);
    if (existing != null) {
      // A source may only be displaced by a strictly later layer. Anything else is a
      // caller supplying the same layer twice, and the first value stands rather than
      // depending on which happened to arrive last.
      if (origin.layer().compareTo(existing.origin().layer()) <= 0) {
        return;
      }
      if (existing.origin().isUserSupplied()) {
        reporter.accept(
            name
                + " = "
                + value
                + " ("
                + origin.describe()
                + ") overrides "
                + existing.value()
                + " ("
                + existing.origin().describe()
                + ")");
      }
    }
    resolved.put(name, new Setting(name, value, origin));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> table(Map<String, Object> root, List<String> path) {
    Object here = root;
    for (String segment : path) {
      if (!(here instanceof Map<?, ?> map)) {
        return Map.of();
      }
      here = map.get(segment);
    }
    if (here instanceof Map<?, ?> map) {
      Map<String, Object> plain = new LinkedHashMap<>();
      map.forEach((k, v) -> plain.put(String.valueOf(k), v));
      // Sub-tables are another command's scope, not this group's settings.
      plain.values().removeIf(v -> v instanceof Map<?, ?>);
      return plain;
    }
    return Map.of();
  }
}
