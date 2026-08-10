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
import java.util.List;
import java.util.Optional;

/**
 * One resolved setting: a value, and where it came from.
 *
 * <p>Values are the TOML data model — {@link String}, {@link Long}, {@link Double},
 * {@link Boolean}, {@code List<Object>}, {@code Map<String, Object>} and the {@code
 * java.time} date types — which is the same currency the plugin SPI uses, so a value can
 * cross both without conversion.
 *
 * <p>The exception is the environment, which has only strings. Rather than guess a type
 * there, an environment value stays a {@link String} and the typed accessors below coerce on
 * demand: the component reading a setting knows what it wants, and the resolver does not.
 */
public final class Setting {

  /** A setting's identity: which group, which key. */
  public record Name(String group, String key) {
    @Override
    public String toString() {
      return group + "." + key;
    }
  }

  private final Name name;
  private final Object value;
  private final Origin origin;

  Setting(Name name, Object value, Origin origin) {
    this.name = name;
    this.value = value;
    this.origin = origin;
  }

  /** Which setting this is. */
  public Name name() {
    return name;
  }

  /** The value, in the TOML data model, or a {@link String} if it came from the environment. */
  public Object value() {
    return value;
  }

  /** Where the value came from. */
  public Origin origin() {
    return origin;
  }

  /** The value as text. */
  public String asString() {
    return String.valueOf(value);
  }

  /**
   * The value as a whole number.
   *
   * @throws ConfigurationException if it is not one — including a string from the
   *     environment that does not parse, which is a user error worth reporting by name
   */
  public long asLong() {
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(asString().trim());
    } catch (NumberFormatException e) {
      throw badValue("a whole number");
    }
  }

  /** The value as a number, whole or fractional. */
  public double asDouble() {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(asString().trim());
    } catch (NumberFormatException e) {
      throw badValue("a number");
    }
  }

  /**
   * The value as a boolean.
   *
   * <p>Only {@code true} and {@code false} are accepted. {@code Boolean.parseBoolean} is not
   * used because it reads every other string as {@code false}, so a typo would silently turn
   * a setting off.
   */
  public boolean asBoolean() {
    if (value instanceof Boolean b) {
      return b;
    }
    String text = asString().trim().toLowerCase();
    if (text.equals("true")) {
      return true;
    }
    if (text.equals("false")) {
      return false;
    }
    throw badValue("true or false");
  }

  /**
   * The value as a list.
   *
   * <p>A single value counts as a list of one, and an environment value is split on commas —
   * there being no other way to write a list in an environment variable.
   */
  public List<Object> asList() {
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    if (origin.layer() == Origin.Layer.ENVIRONMENT) {
      List<Object> items = new ArrayList<>();
      for (String item : asString().split(",")) {
        String trimmed = item.trim();
        if (!trimmed.isEmpty()) {
          items.add(trimmed);
        }
      }
      return items;
    }
    return List.of(value);
  }

  /** The value as a list of strings. */
  public List<String> asStringList() {
    return asList().stream().map(String::valueOf).toList();
  }

  private ConfigurationException badValue(String expected) {
    return new ConfigurationException(
        name + " must be " + expected + ", but " + origin.describe() + " gave " + value);
  }

  /** Convenience for the common {@code Optional<Setting>} shape. */
  public static Optional<Long> asLong(Optional<Setting> setting) {
    return setting.map(Setting::asLong);
  }

  @Override
  public String toString() {
    return name + " = " + value + " (" + origin.describe() + ")";
  }
}
