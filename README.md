# spice-config

The one place a `spice` setting's value is decided.

Every component of `spice` — the CLI itself, the analysis engine, the registry surveyor, the
uploader — needs to know what a setting is called, where it may be set, and which source wins
when two disagree. Those rules must have exactly one implementation. They have drifted
before: an allowlist of another program's flags, kept in a second program, that ended up
permitting three flags the first program does not have, with nothing to notice.

One dependency: the TOML parser, because reading a config file *is* configuration. Values
still cross as plain `java.*` maps — the same currency the plugin SPI uses — so nothing here
imposes tomlj on a caller that already has a parser.

logback is a second, *optional* dependency, used only by `LogbackLogging` and not transitive.
Every Spice tool logs through logback, so the wiring lives here once rather than four times;
a component using another backend applies the `[logging]` group itself and never loads that
class.

## The model

### One name, three forms

```
config file      [analysis] max_records
flag             --max-records
environment      SPICE_ANALYSIS_MAX_RECORDS
```

The config key is canonical. The flag is its kebab-case form. The environment variable is
the prefix, the group and the key in upper snake case. There are no exceptions, so there is
no table of them to remember.

Run standalone, a component changes only the prefix:

| Component | Prefix |
| --- | --- |
| `spice` | `SPICE_` |
| `goatrodeo` | `GOATRODEO_` |
| `allspice` | `ALLSPICE_` |
| `sassafras` | `SASSAFRAS_` |

`GOATRODEO_ANALYSIS_MAX_RECORDS` names the same setting as `SPICE_ANALYSIS_MAX_RECORDS`.

The one variation: when a command claims two groups that both define `threads`, the flag is
qualified as `--analysis-threads`. Still derived, not remembered.

### Groups are shared; commands may override

```toml
[analysis]              # every command that claims `analysis` sees this
threads = 16
max_records = 100000

[upload]
target_chunk_size = 64

[registry.analysis]     # only `spice registry` sees this
threads = 4
```

Write a setting once. Override it where it matters. A command resolves a claimed group `g`
as `[g]` overlaid by `[<command path>.g]`, and reads nothing else — a command cannot see
settings meant for another, because the groups it did not claim are never resolved.

A group is usually a table of settings. Some name a list of things — an array of
repositories — and have no keys to layer, so a later source replaces such a group whole;
`Resolution.value` reads it back.

**A group may not share a name with a command.** At the root of a file a table is either a
group or a command's scope, so `[registry.analysis]` can only have one reading if nothing
called `registry` is also a group. `Groups.collisions` checks it.

### The ladder

    defaults  <  [group]  <  [command.group]  <  environment  <  flag

Resolution does not depend on the order sources are supplied in: a value may only be
displaced by one from a strictly later layer. There is no fifth channel. System properties
are not a configuration input; setting one to steer a third-party library is an *output*,
written once from a resolved configuration and never read back.

Values from the Spice Pass are not on the ladder at all. They are properties of the
credential the platform issued rather than settings a user chooses, and nothing written in a
file, an environment variable or an argument may supply or override them.

### Disagreements are reported

The resolver is the only place sources combine, so it is the only thing that can say which
one won:

```
analysis.threads = 4 (--threads) overrides 16 ([analysis] in ~/.config/spice/config.toml)
```

Overriding a *default* is not reported. That happens for every setting on every run, and the
noise would bury the cases where two deliberate choices conflict.

`Resolution.explain()` renders the whole configuration with an attribution per key, which is
what `spice config explain` and `--explain-config` print.

## Using it

```java
Map<String, Object> file = TomlFile.parse(configFile);   // plain nested maps, all the way down

Resolution resolved =
    new Resolver("SPICE", Set.of("analysis", "upload"), log::info)
        .withDefaults(Map.of("analysis", Map.of("threads", 8L)))
        .withFile(configFile, file, List.of("registry"))
        .withEnvironment(System.getenv())
        .withFlag("analysis", "threads", threadsOption, "--threads")
        .resolve();

resolved.group("analysis");                       // group -> key -> value, for the owner
resolved.setting("analysis", "threads");          // with provenance
resolved.explain();                               // the whole thing, attributed
```

Values are the TOML data model — `String`, `Long`, `Double`, `Boolean`, `List<Object>`,
`Map<String, Object>` and the `java.time` date types — which is the same currency the plugin
SPI carries, so a group crosses it without conversion. The environment has only strings, so
`Setting` coerces on demand: the component reading a setting knows what type it wants, and
the resolver does not.

## Reserved variable names

`SPICE_IMAGE`, `SPICE_CACHE_DIR`, `SPICE_PATH_MANIFEST`, `SPICE_PASS` and the other wrapper
variables are host-side plumbing read before any JVM exists. They are not settings. Because
the environment is matched against *claimed group names* rather than parsed, they can never
be mistaken for one — but no group may be named `image`, `cache`, `path` or `pass`.
