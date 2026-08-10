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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What every setting resolved to, and why.
 *
 * <p>Two shapes, for two audiences. {@link #groups()} is the plain nested map a component
 * parses into its own {@code Configuration} — the same currency the plugin SPI carries, so
 * it crosses without conversion. {@link #settings()} and {@link #explain()} keep the
 * provenance, which is what a person needs when the run did something they did not expect.
 */
public final class Resolution {

  private final List<Setting> settings;

  Resolution(List<Setting> settings) {
    this.settings = settings;
  }

  /**
   * A resolution over values somebody else already resolved.
   *
   * <p>For a component embedded in a host that did the layering — {@code spice} hands a
   * plugin its groups already decided, and the plugin still wants the typed accessors and
   * the error messages that name a setting and its source. Everything here shares one
   * origin, because from the embedded component's point of view there was only one.
   */
  public static Resolution of(Map<String, Map<String, Object>> groups, Origin origin) {
    List<Setting> settings = new java.util.ArrayList<>();
    groups.forEach(
        (group, values) ->
            values.forEach(
                (key, value) ->
                    settings.add(new Setting(new Setting.Name(group, key), value, origin))));
    return new Resolution(List.copyOf(settings));
  }

  /**
   * The key a group with no settings of its own is stored under.
   *
   * <p>Empty, because no TOML key can be empty, so it cannot collide with a real setting.
   */
  static final String WHOLE = "";

  /**
   * A group's value as it stands, for a group that is not a table of settings.
   *
   * <p>An array of tables — a list of repositories, say — has no keys to merge, so it is
   * carried whole and read back here. For an ordinary group this is empty and
   * {@link #group} is what you want.
   */
  public Optional<Object> value(String group) {
    return setting(group, WHOLE).map(Setting::value);
  }

  /** Every setting, with its origin. */
  public List<Setting> settings() {
    return settings;
  }

  /** One setting, if it has a value at all. */
  public Optional<Setting> setting(String group, String key) {
    Setting.Name name = new Setting.Name(group, key);
    return settings.stream().filter(s -> s.name().equals(name)).findFirst();
  }

  /**
   * The resolved values as {@code group -> key -> value}, provenance dropped.
   *
   * <p>This is what gets handed to a component that owns the schema: it is in the group's
   * own vocabulary, and the layering that produced it has already happened.
   */
  public Map<String, Map<String, Object>> groups() {
    Map<String, Map<String, Object>> byGroup = new LinkedHashMap<>();
    for (Setting setting : settings) {
      if (setting.name().key().equals(WHOLE)) {
        continue;
      }
      byGroup
          .computeIfAbsent(setting.name().group(), g -> new LinkedHashMap<>())
          .put(setting.name().key(), setting.value());
    }
    return byGroup;
  }

  /** One group's resolved values, or an empty map if it has none. */
  public Map<String, Object> group(String name) {
    return groups().getOrDefault(name, Map.of());
  }

  /**
   * The whole configuration, one setting per line, with where each value came from.
   *
   * <p>Sorted rather than in resolution order: this is read by a person comparing two runs,
   * and a stable order is what makes that possible.
   */
  public String explain() {
    Map<String, Map<String, Setting>> sorted = new TreeMap<>();
    for (Setting setting : settings) {
      sorted
          .computeIfAbsent(setting.name().group(), g -> new TreeMap<>())
          .put(setting.name().key(), setting);
    }
    StringBuilder out = new StringBuilder();
    sorted.forEach(
        (group, keys) -> {
          out.append('[').append(group).append("]\n");
          // Both columns padded, so the origins line up and the eye can run down them —
          // "which of these did I not set?" is the question this output is read with.
          int keyWidth = keys.keySet().stream().mapToInt(String::length).max().orElse(0);
          int valueWidth =
              keys.values().stream().mapToInt(s -> render(s.value()).length()).max().orElse(0);
          keys.forEach(
              (key, setting) -> {
                String value = render(setting.value());
                out.append("  ").append(key).append(" ".repeat(keyWidth - key.length()));
                out.append(" = ").append(value).append(" ".repeat(valueWidth - value.length()));
                out.append("    ").append(setting.origin().describe()).append('\n');
              });
          out.append('\n');
        });
    return out.toString();
  }

  private static String render(Object value) {
    if (value instanceof String text) {
      return '"' + text + '"';
    }
    if (value instanceof List<?> list) {
      return "["
          + String.join(", ", list.stream().map(Resolution::render).toList())
          + "]";
    }
    return String.valueOf(value);
  }
}
