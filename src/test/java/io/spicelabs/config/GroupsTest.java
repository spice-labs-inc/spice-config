package io.spicelabs.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupsTest {

  private static Map<String, Object> file() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("analysis", Map.of("threads", 16L));
    root.put("anaylsis", Map.of("threads", 16L)); // the typo this check exists for
    root.put("survey", Map.of("inventory", Map.of("analysis", Map.of("threads", 4L))));
    return root;
  }

  @Test
  void aMisspeltGroupIsReportedByName() {
    List<String> unknown =
        Groups.unclaimed(file(), List.of("analysis", "upload"), List.of("survey", "registry"));

    assertEquals(List.of("anaylsis"), unknown);
  }

  @Test
  void aCommandScopeIsNotAnUnclaimedGroup() {
    assertFalse(
        Groups.unclaimed(file(), List.of("analysis"), List.of("survey")).contains("survey"));
  }

  @Test
  void theMessageSuggestsBothCauses() {
    String message = Groups.describeUnclaimed(List.of("anaylsis")).orElseThrow();

    assertTrue(message.contains("[anaylsis]"), message);
    assertTrue(message.contains("spelling"), message);
    assertTrue(message.contains("plugin"), message);
  }

  @Test
  void aCleanFileSaysNothing() {
    assertTrue(
        Groups.describeUnclaimed(Groups.unclaimed(Map.of("analysis", Map.of()), List.of("analysis"), List.of()))
            .isEmpty());
  }
}
