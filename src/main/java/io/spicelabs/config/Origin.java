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
import java.util.Objects;

/**
 * Where a value came from.
 *
 * <p>Carried alongside every resolved setting, for two reasons. It lets the resolver say
 * which source displaced which — "where did this value come from" is the question people
 * actually ask when a run behaves unexpectedly, and until now nothing could answer it. And
 * it lets {@code explain} print a whole configuration with an attribution per key.
 *
 * <p>{@link Layer} is the precedence order: a value may only be displaced by one from a
 * strictly later layer, which is what makes resolution independent of the order sources
 * happen to be supplied in.
 */
public final class Origin {

  /**
   * The precedence ladder, lowest first.
   *
   * <p>{@link #PASS} is not on the ladder at all. Values from the Spice Pass are properties
   * of the credential the platform issued, not settings a user chooses, and nothing in a
   * file, an environment variable or an argument may supply or override them. It is listed
   * here so such a value can still be attributed when explained.
   */
  public enum Layer {
    /** A component's built-in default. */
    DEFAULT,
    /** A shared group at the top level of the config file: {@code [analysis]}. */
    FILE_SHARED,
    /** A group scoped to one command: {@code [registry.analysis]}. */
    FILE_COMMAND,
    /** An environment variable. */
    ENVIRONMENT,
    /** A command-line argument. */
    FLAG,
    /** The Spice Pass. Off the ladder: see {@link Layer}. */
    PASS
  }

  private final Layer layer;
  private final String detail;

  private Origin(Layer layer, String detail) {
    this.layer = layer;
    this.detail = detail;
  }

  /** A component's built-in default. */
  public static Origin defaultValue() {
    return new Origin(Layer.DEFAULT, "default");
  }

  /** A shared group at the top level of {@code path}. */
  public static Origin sharedTable(Path path, String group) {
    return new Origin(Layer.FILE_SHARED, "[" + group + "] in " + path);
  }

  /** A command-scoped group in {@code path}, e.g. {@code [registry.analysis]}. */
  public static Origin commandTable(Path path, String commandPath, String group) {
    return new Origin(Layer.FILE_COMMAND, "[" + commandPath + "." + group + "] in " + path);
  }

  /** An environment variable, named in full. */
  public static Origin environment(String variable) {
    return new Origin(Layer.ENVIRONMENT, variable);
  }

  /** A command-line argument, named as the user would type it. */
  public static Origin flag(String flag) {
    return new Origin(Layer.FLAG, flag);
  }

  /** The Spice Pass. */
  public static Origin pass(String claim) {
    return new Origin(Layer.PASS, "the Spice Pass claim " + claim);
  }

  /**
   * A host program that resolved this value and passed it in.
   *
   * <p>An embedded component cannot see which of the host's layers a value came from, and
   * should not pretend otherwise: it says where the value entered <em>this</em> program,
   * which is the honest answer and the useful one when its own defaults are in play.
   *
   * @param table how the host named the settings it handed over, e.g. {@code [analysis]}
   */
  public static Origin embedded(String table) {
    return new Origin(Layer.FILE_COMMAND, "[" + table + "] from the host program");
  }

  /** Which layer this came from. */
  public Layer layer() {
    return layer;
  }

  /**
   * Whether this value was supplied by someone, rather than being what the component would
   * have done anyway.
   *
   * <p>The distinction is what keeps override reporting useful: every setting overrides its
   * default on every run, so saying so would bury the handful of cases where two things a
   * person actually wrote disagree.
   */
  public boolean isUserSupplied() {
    return layer != Layer.DEFAULT;
  }

  /** How this reads in a message: {@code --threads}, {@code SPICE_ANALYSIS_THREADS}. */
  public String describe() {
    return detail;
  }

  @Override
  public String toString() {
    return describe();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Origin o && layer == o.layer && detail.equals(o.detail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layer, detail);
  }
}
