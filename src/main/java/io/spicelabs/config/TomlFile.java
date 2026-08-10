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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * A config file, read as plain nested maps.
 *
 * <p>Every component needs exactly this and nothing more: a file turned into the data the
 * resolver layers over. Doing it here rather than in each component is the same argument as
 * the rest of this library — and it is not hypothetical, because this conversion has already
 * had a bug. {@link TomlTable#toMap()} is <em>shallow</em>: a nested table comes back as a
 * tomlj object rather than a plain map, which is invisible for a flat config file and wrong
 * the moment anybody nests one.
 *
 * <p>This is the library's only dependency. Reading a configuration file is configuration,
 * and the alternative was every component either carrying its own copy of this walk or
 * depending on some unrelated component for it — which is how {@code spice} came to depend
 * on the analysis engine to read its own config file.
 */
public final class TomlFile {

  private TomlFile() {}

  /**
   * Parse a TOML file into plain nested maps.
   *
   * @throws ConfigurationException if the file cannot be read or does not parse. A config
   *     file that cannot be read must stop the run: continuing would silently ignore every
   *     setting in it, which is worse than failing.
   */
  public static Map<String, Object> parse(Path path) {
    String text;
    try {
      text = Files.readString(path);
    } catch (IOException e) {
      throw new ConfigurationException("Could not read config file " + path + ": " + e.getMessage(), e);
    }
    TomlParseResult parsed = Toml.parse(text);
    if (!parsed.errors().isEmpty()) {
      throw new ConfigurationException(
          "Invalid config file " + path + ": " + parsed.errors().get(0));
    }
    return toPlainMap(parsed);
  }

  /** A parsed table as plain nested maps, all the way down. */
  public static Map<String, Object> toPlainMap(TomlTable table) {
    Map<String, Object> plain = new LinkedHashMap<>();
    for (String key : table.keySet()) {
      plain.put(key, plainValue(table.get(key)));
    }
    return plain;
  }

  private static Object plainValue(Object value) {
    if (value instanceof TomlTable nested) {
      return toPlainMap(nested);
    }
    if (value instanceof TomlArray array) {
      List<Object> items = new java.util.ArrayList<>();
      for (Object item : array.toList()) {
        items.add(plainValue(item));
      }
      return items;
    }
    return value;
  }
}
