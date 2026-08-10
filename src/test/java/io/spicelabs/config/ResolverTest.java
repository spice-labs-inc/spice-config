package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The behaviour this library exists for: one value written once reaches every command that
 * wants it, a later layer displaces an earlier one, and a person is told when that happens.
 */
class ResolverTest {

  private static final Path FILE = Path.of("/home/u/.config/spice/config.toml");

  private final List<String> reported = new ArrayList<>();

  private Resolver resolver(String... groups) {
    return new Resolver("SPICE", Set.of(groups), reported::add);
  }

  private static Map<String, Object> table(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void aSharedGroupReachesEveryCommandThatClaimsIt() {
    // The point of the whole design: `threads` is written once, at the top level, and both
    // commands see it without either naming a file of its own.
    Map<String, Object> file = table("analysis", table("threads", 16L));

    for (List<String> command : List.of(List.of("registry"), List.of("survey", "inventory"))) {
      Resolution resolved = resolver("analysis").withFile(FILE, file, command).resolve();
      assertEquals(16L, resolved.setting("analysis", "threads").orElseThrow().value(), command.toString());
    }
  }

  @Test
  void aCommandScopedGroupOverridesTheSharedOne() {
    Map<String, Object> file =
        table(
            "analysis", table("threads", 16L, "max_records", 100000L),
            "registry", table("analysis", table("threads", 4L)));

    Resolution resolved =
        resolver("analysis").withFile(FILE, file, List.of("registry")).resolve();

    assertEquals(4L, resolved.setting("analysis", "threads").orElseThrow().value());
    assertEquals(
        100000L,
        resolved.setting("analysis", "max_records").orElseThrow().value(),
        "a key the command did not override keeps the shared value");
  }

  @Test
  void anotherCommandsScopeIsNotRead() {
    Map<String, Object> file =
        table("analysis", table("threads", 16L), "registry", table("analysis", table("threads", 4L)));

    Resolution resolved =
        resolver("analysis").withFile(FILE, file, List.of("survey", "inventory")).resolve();

    assertEquals(16L, resolved.setting("analysis", "threads").orElseThrow().value());
  }

  @Test
  void theLadderRunsFileThenEnvironmentThenFlag() {
    Resolution resolved =
        resolver("analysis")
            .withDefaults(Map.of("analysis", Map.of("threads", 8L)))
            .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
            .withEnvironment(Map.of("SPICE_ANALYSIS_THREADS", "8"))
            .withFlag("analysis", "threads", 4, "--threads")
            .resolve();

    assertEquals(4, resolved.setting("analysis", "threads").orElseThrow().value());
  }

  @Test
  void theOrderSourcesAreSuppliedInDoesNotMatter() {
    Resolution forwards =
        resolver("analysis")
            .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
            .withFlag("analysis", "threads", 4, "--threads")
            .resolve();
    Resolution backwards =
        resolver("analysis")
            .withFlag("analysis", "threads", 4, "--threads")
            .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
            .resolve();

    assertEquals(
        forwards.setting("analysis", "threads").orElseThrow().value(),
        backwards.setting("analysis", "threads").orElseThrow().value());
  }

  @Test
  void everyOverrideBetweenThingsSomeoneWroteIsReported() {
    resolver("analysis")
        .withDefaults(Map.of("analysis", Map.of("threads", 8L)))
        .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
        .withEnvironment(Map.of("SPICE_ANALYSIS_THREADS", "8"))
        .withFlag("analysis", "threads", 4, "--threads")
        .resolve();

    assertEquals(2, reported.size(), "two user-supplied overrides, not three: " + reported);
    assertEquals(
        "analysis.threads = 8 (SPICE_ANALYSIS_THREADS) overrides 16 ([analysis] in "
            + FILE
            + ")",
        reported.get(0));
    assertEquals(
        "analysis.threads = 4 (--threads) overrides 8 (SPICE_ANALYSIS_THREADS)", reported.get(1));
  }

  @Test
  void overridingADefaultIsNotReported() {
    // Otherwise every setting reports on every run and the messages that matter are lost.
    resolver("analysis")
        .withDefaults(Map.of("analysis", Map.of("threads", 8L)))
        .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
        .resolve();

    assertEquals(List.of(), reported);
  }

  @Test
  void aGroupTheCommandDidNotClaimIsNotResolved() {
    Resolution resolved =
        resolver("analysis")
            .withFile(FILE, table("upload", table("chunk_size_mb", 64L)), List.of())
            .withEnvironment(Map.of("SPICE_UPLOAD_CHUNK_SIZE_MB", "128"))
            .resolve();

    assertTrue(resolved.setting("upload", "chunk_size_mb").isEmpty());
    assertEquals(Map.of(), resolved.group("upload"));
  }

  @Test
  void aSubTableIsNotOneOfTheGroupsSettings() {
    // [analysis] threads is a setting; [analysis.registry] would be a scope, not a value.
    Map<String, Object> file =
        table("analysis", table("threads", 16L, "nested", table("threads", 1L)));

    Resolution resolved = resolver("analysis").withFile(FILE, file, List.of()).resolve();

    assertEquals(Map.of("threads", 16L), resolved.group("analysis"));
  }

  @Test
  void aValueFromThePassCannotBeDisplaced() {
    Resolution resolved =
        resolver("scope")
            .withPassValue("scope", "cutoff", "2026-01-01T00:00:00Z", "x-cutoff")
            .withFlag("scope", "cutoff", "2030-01-01T00:00:00Z", "--cutoff")
            .withEnvironment(Map.of("SPICE_SCOPE_CUTOFF", "2030-01-01T00:00:00Z"))
            .resolve();

    assertEquals("2026-01-01T00:00:00Z", resolved.setting("scope", "cutoff").orElseThrow().value());
  }

  @Test
  void aNullFlagValueIsNotAValue() {
    // Callers hand over every option they have; picocli leaves the ungiven ones null.
    Resolution resolved =
        resolver("analysis")
            .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
            .withFlag("analysis", "threads", null, "--threads")
            .resolve();

    assertEquals(16L, resolved.setting("analysis", "threads").orElseThrow().value());
    assertEquals(List.of(), reported);
  }

  @Test
  void explainShowsEveryValueAndWhereItCameFrom() {
    String explained =
        resolver("analysis", "upload")
            .withDefaults(Map.of("upload", Map.of("chunk_size_mb", 64L)))
            .withFile(FILE, table("analysis", table("threads", 16L)), List.of())
            .withFlag("analysis", "max_records", 100000L, "--max-records")
            .resolve()
            .explain();

    assertTrue(explained.contains("[analysis]"), explained);
    assertTrue(explained.contains("threads"), explained);
    assertTrue(explained.contains("[analysis] in " + FILE), explained);
    assertTrue(explained.contains("--max-records"), explained);
    assertTrue(explained.contains("default"), explained);
  }
}
