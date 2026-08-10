package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The environment has only strings, so coercion happens where the reader knows what it
 * wants. What matters is that a value that cannot be read is reported by name and source,
 * not quietly turned into something else.
 */
class SettingTest {

  private static Setting fromEnvironment(String value) {
    return new Setting(
        new Setting.Name("analysis", "threads"), value, Origin.environment("SPICE_ANALYSIS_THREADS"));
  }

  private static Setting fromFile(Object value) {
    return new Setting(
        new Setting.Name("analysis", "threads"),
        value,
        Origin.sharedTable(java.nio.file.Path.of("/c.toml"), "analysis"));
  }

  @Test
  void aTypedValuePassesThrough() {
    assertEquals(16L, fromFile(16L).asLong());
    assertTrue(fromFile(true).asBoolean());
    assertEquals(1.5, fromFile(1.5).asDouble());
  }

  @Test
  void anEnvironmentStringIsCoerced() {
    assertEquals(16L, fromEnvironment("16").asLong());
    assertEquals(16L, fromEnvironment("  16  ").asLong());
    assertTrue(fromEnvironment("true").asBoolean());
    assertFalse(fromEnvironment("FALSE").asBoolean());
  }

  @Test
  void aValueThatCannotBeReadNamesItselfAndItsSource() {
    ConfigurationException thrown =
        assertThrows(ConfigurationException.class, () -> fromEnvironment("lots").asLong());

    assertTrue(thrown.getMessage().contains("analysis.threads"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("SPICE_ANALYSIS_THREADS"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("lots"), thrown.getMessage());
  }

  @Test
  void anythingOtherThanTrueOrFalseIsAnError() {
    // Boolean.parseBoolean reads "yes" as false, which would turn a setting off silently.
    assertThrows(ConfigurationException.class, () -> fromEnvironment("yes").asBoolean());
    assertThrows(ConfigurationException.class, () -> fromEnvironment("1").asBoolean());
  }

  @Test
  void anEnvironmentListIsCommaSeparated() {
    Setting setting =
        new Setting(
            new Setting.Name("analysis", "mime_filter"),
            "+application/java-archive, -text/plain",
            Origin.environment("SPICE_ANALYSIS_MIME_FILTER"));

    assertEquals(List.of("+application/java-archive", "-text/plain"), setting.asStringList());
  }

  @Test
  void aFileListIsAlreadyAList() {
    Setting setting =
        new Setting(
            new Setting.Name("analysis", "mime_filter"),
            List.of("+application/java-archive"),
            Origin.sharedTable(java.nio.file.Path.of("/c.toml"), "analysis"));

    assertEquals(List.of("+application/java-archive"), setting.asStringList());
  }
}
