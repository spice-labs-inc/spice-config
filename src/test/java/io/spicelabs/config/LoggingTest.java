package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingTest {

  private static Resolution of(Map<String, Object> group) {
    return Resolution.of(Map.of(Logging.GROUP, group), Origin.defaultValue());
  }

  @Test
  void aLevelIsNormalised() {
    assertEquals("DEBUG", Logging.level(of(Map.of("level", " Debug "))));
  }

  @Test
  void theDefaultIsInfo() {
    assertEquals("INFO", Logging.level(of(Map.of())));
  }

  @Test
  void aMisspeltLevelNamesWhatIsAccepted() {
    // Silently meaning "info" is how a run stays quiet when someone asked it not to be.
    ConfigurationException thrown =
        assertThrows(ConfigurationException.class, () -> Logging.level(of(Map.of("level", "verbose"))));

    assertTrue(thrown.getMessage().contains("verbose"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("debug"), thrown.getMessage());
  }

  @Test
  void aFileIsOptionalAndBlankMeansNone() {
    assertTrue(Logging.file(of(Map.of())).isEmpty());
    assertTrue(Logging.file(of(Map.of("file", "   "))).isEmpty());
    assertEquals("/tmp/run.log", Logging.file(of(Map.of("file", "/tmp/run.log"))).orElseThrow());
  }

  @Test
  void anUnknownKeyIsReported() {
    assertEquals(java.util.List.of("levl"), Logging.unknownKeys(of(Map.of("levl", "info"))));
  }
}
