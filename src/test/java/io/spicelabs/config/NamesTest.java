package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The mapping rule is the part of this library people will hold in their heads, so it has to
 * be exceptionless in fact and not only in intent. These tests are the statement of it.
 */
class NamesTest {

  @Test
  void aKeyBecomesItsFlag() {
    assertEquals("--max-records", Names.flag("max_records"));
    assertEquals("--threads", Names.flag("threads"));
  }

  @Test
  void aKeyBecomesItsEnvironmentVariable() {
    assertEquals(
        "SPICE_ANALYSIS_MAX_RECORDS", Names.environmentVariable("SPICE", "analysis", "max_records"));
    assertEquals(
        "GOATRODEO_ANALYSIS_MAX_RECORDS",
        Names.environmentVariable("GOATRODEO", "analysis", "max_records"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"threads", "max_records", "chunk_size_mb", "a", "a_b_c_d"})
  void everyNameFormRoundTripsToTheSameKey(String key) {
    assertEquals(key, Names.key(Names.flag(key)), "flag round-trip");
    assertEquals(
        Optional.of(new Setting.Name("analysis", key)),
        Names.fromEnvironmentVariable(
            "SPICE", Set.of("analysis"), Names.environmentVariable("SPICE", "analysis", key)),
        "environment round-trip");
  }

  @Test
  void aVariableNamingNoKnownGroupIsNotASetting() {
    // The wrapper's own variables live in the same namespace and must be left alone.
    Set<String> groups = Set.of("analysis", "upload");
    for (String wrapperVariable :
        List.of("SPICE_IMAGE", "SPICE_CACHE_DIR", "SPICE_PATH_MANIFEST", "SPICE_PASS")) {
      assertEquals(
          Optional.empty(),
          Names.fromEnvironmentVariable("SPICE", groups, wrapperVariable),
          wrapperVariable + " is wrapper plumbing, not a setting");
    }
  }

  @Test
  void theLongestMatchingGroupWins() {
    // SPICE_STATE_REPO_PATH could be [state] repo_path or [state_repo] path. With both
    // groups in play the longer one wins, so the answer never depends on iteration order.
    Set<String> both = Set.of("state", "state_repo");
    assertEquals(
        Optional.of(new Setting.Name("state_repo", "path")),
        Names.fromEnvironmentVariable("SPICE", both, "SPICE_STATE_REPO_PATH"));
    assertEquals(
        Optional.of(new Setting.Name("state", "repo_path")),
        Names.fromEnvironmentVariable("SPICE", Set.of("state"), "SPICE_STATE_REPO_PATH"));
  }

  @Test
  void aSharedKeyNameCanBeQualified() {
    assertEquals("--analysis-threads", Names.qualifiedFlag("analysis", "threads"));
  }

  @Test
  void aCommandScopedGroupNamesItsTable() {
    assertEquals("registry.analysis", Names.commandTable(List.of("registry"), "analysis"));
    assertEquals(
        "survey.inventory.analysis",
        Names.commandTable(List.of("survey", "inventory"), "analysis"));
  }
}
