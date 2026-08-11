package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

class LogbackLoggingTest {

  private static final String OWNED = "io.spicelabs.example";
  private static final String FOREIGN = "com.someone.else";

  private static LoggerContext context() {
    return (LoggerContext) LoggerFactory.getILoggerFactory();
  }

  @AfterEach
  void reset() {
    context().getLogger(OWNED).setLevel(null);
    context().getLogger(FOREIGN).setLevel(null);
    context().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAndStopAllAppenders();
  }

  private static Resolution settings(Map<String, Object> logging) {
    return Resolution.of(Map.of(Logging.GROUP, logging), Origin.defaultValue());
  }

  @Test
  void theLevelReachesTheLoggersThisProgramOwns() {
    LogbackLogging.apply(settings(Map.of("level", "debug")), OWNED);

    assertEquals(Level.DEBUG, context().getLogger(OWNED).getLevel());
  }

  @Test
  void aLoggerThisProgramDoesNotOwnIsLeftAlone() {
    // Lifting a noisy third-party logger along with ours buries the output the person
    // actually asked for.
    LogbackLogging.apply(settings(Map.of("level", "debug")), OWNED);

    assertNull(context().getLogger(FOREIGN).getLevel());
  }

  @Test
  void aFileIsAddedAlongsideTheConsole(@TempDir Path dir) throws Exception {
    Path log = dir.resolve("run.log");
    LogbackLogging.apply(settings(Map.of("level", "info", "file", log.toString())), OWNED);

    LoggerFactory.getLogger(OWNED).info("hello");

    assertTrue(Files.exists(log), "the file was created");
    assertTrue(Files.readString(log).contains("hello"), Files.readString(log));
  }

  @Test
  void anUnknownKeyIsRefused() {
    ConfigurationException thrown =
        assertThrows(
            ConfigurationException.class,
            () -> LogbackLogging.apply(settings(Map.of("levl", "info")), OWNED));

    assertTrue(thrown.getMessage().contains("levl"), thrown.getMessage());
  }
}
