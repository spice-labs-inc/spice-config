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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * Applies the {@link Logging} group to logback.
 *
 * <p>Every Spice command-line tool uses logback, so this is the wiring once rather than four
 * near-identical copies. logback is an <em>optional</em> dependency of this library and is
 * not transitive: nothing loads this class unless a component calls it, and a component
 * using a different backend applies {@link Logging} itself.
 *
 * <p><strong>Only a program that owns the process should call this.</strong> A library
 * reconfiguring the logging of whatever embedded it is a rude surprise — the host chose its
 * levels deliberately. In practice that means calling it from {@code main}, not from the
 * method a host invokes.
 */
public final class LogbackLogging {

  /** The pattern every Spice tool logs with, so two logs can be read side by side. */
  public static final String PATTERN = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

  private LogbackLogging() {}

  /**
   * Set this program's log level, and add a file if one was asked for.
   *
   * @param settings the resolved configuration, which must include the {@code logging} group
   * @param loggers the logger names this program owns, e.g. {@code "io.spicelabs.allspice"}.
   *     Only these are moved: a level is a statement about how much *this* program should
   *     say, and lifting a noisy third-party logger to DEBUG along with it buries the output
   *     the person actually asked for.
   * @throws ConfigurationException if the group has an unknown key or an unusable level
   */
  public static void apply(Resolution settings, String... loggers) {
    List<String> unknown = Logging.unknownKeys(settings);
    if (!unknown.isEmpty()) {
      throw new ConfigurationException(
          "[" + Logging.GROUP + "] has no setting called " + String.join(", ", unknown));
    }

    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      // Some other backend is bound. Say nothing and change nothing: a warning here would
      // be a warning about the build, aimed at whoever is least able to act on it.
      return;
    }

    Level level = Level.toLevel(Logging.level(settings), Level.INFO);
    for (String logger : loggers) {
      context.getLogger(logger).setLevel(level);
    }

    Logging.file(settings).ifPresent(path -> addFileAppender(context, path));
  }

  /** Add a file alongside whatever the console already does, rather than replacing it. */
  private static void addFileAppender(LoggerContext context, String path) {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(PATTERN);
    encoder.setCharset(StandardCharsets.UTF_8);
    encoder.start();

    FileAppender<ILoggingEvent> appender = new FileAppender<>();
    appender.setContext(context);
    appender.setFile(path);
    appender.setAppend(true);
    appender.setEncoder(encoder);
    appender.start();

    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
  }
}
