package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlFileTest {

  private static Path write(Path dir, String toml) throws Exception {
    return Files.writeString(dir.resolve("config.toml"), toml);
  }

  @Test
  void nestedTablesArePlainMapsAllTheWayDown(@TempDir Path dir) throws Exception {
    // TomlTable.toMap() is shallow, so a nested table would come back as a tomlj
    // object — invisible for a flat file, wrong the moment anybody nests one.
    Map<String, Object> parsed =
        TomlFile.parse(
            write(
                dir,
                """
                [credentials]
                type = "vault"

                [credentials.vault]
                addr = "https://vault"
                """));

    Object credentials = parsed.get("credentials");
    assertInstanceOf(Map.class, credentials);
    assertInstanceOf(Map.class, ((Map<?, ?>) credentials).get("vault"));
  }

  @Test
  void anArrayOfTablesIsAListOfPlainMaps(@TempDir Path dir) throws Exception {
    Map<String, Object> parsed =
        TomlFile.parse(
            write(
                dir,
                """
                [[repositories]]
                id = "one"

                [[repositories]]
                id = "two"
                """));

    Object repositories = parsed.get("repositories");
    assertInstanceOf(List.class, repositories);
    List<?> list = (List<?>) repositories;
    assertEquals(2, list.size());
    assertInstanceOf(Map.class, list.get(0));
    assertEquals("one", ((Map<?, ?>) list.get(0)).get("id"));
  }

  @Test
  void aFileThatDoesNotParseStopsTheRun(@TempDir Path dir) throws Exception {
    ConfigurationException thrown =
        assertThrows(ConfigurationException.class, () -> TomlFile.parse(write(dir, "threads = \n")));
    assertTrue(thrown.getMessage().contains("Invalid config file"), thrown.getMessage());
  }

  @Test
  void aMissingFileSaysWhichOne(@TempDir Path dir) {
    Path missing = dir.resolve("nope.toml");
    ConfigurationException thrown =
        assertThrows(ConfigurationException.class, () -> TomlFile.parse(missing));
    assertTrue(thrown.getMessage().contains(missing.toString()), thrown.getMessage());
  }
}
