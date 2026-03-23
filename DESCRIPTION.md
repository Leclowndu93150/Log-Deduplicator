# Log Deduplicator

If you've ever opened your console and seen the same error repeated thousands of times, this mod is for you. Log Deduplicator detects repeated log messages and suppresses them after a configurable threshold, so your console stays readable and your log files don't balloon to hundreds of megabytes.

This is especially useful for large modpacks where broken mods or misconfigured setups can spam the log hard enough to cause stutters and lag. Less log spam means less I/O, less CPU time spent formatting and writing log lines, and a smoother game overall.

When a message gets suppressed, you'll still see a summary like `[suppressed 482 times over 12.3s]` so nothing is silently lost.

## How it works

The mod intercepts log messages at every level: Log4j, java.util.logging, and System.out/err. It recognizes that messages like `Entity at (1.0, 2.0, 3.0)` and `Entity at (50.0, 64.0, -20.0)` are really the same message with different numbers, so it groups them together instead of treating each one as unique.

It also cleans up stack traces by removing duplicate frames, so when the same exception gets logged repeatedly you get the useful information without all the noise.

## Config

Everything is configurable through an in-game screen (requires YACL) or by editing `config/log_deduplicator.toml`.

- **Thresholds per log level** - set how many duplicates are allowed before suppression kicks in (default: 5 for errors, 20 for info)
- **Time windows** - control how long messages are grouped together before the count resets
- **Fuzzy matching** - groups messages that only differ in numbers, coordinates, UUIDs, or timings
- **Normalization** - toggle whether coordinates, UUIDs, memory addresses, and timing values get normalized before comparing
- **Logger filters** - whitelist or blacklist specific loggers
- **System stream dedup** - deduplicate System.out/err with multiline detection for stack traces
- **Stack trace cleaning** - remove duplicate frames, optionally strip framework internals, limit max depth

## Supported platforms

- NeoForge 1.21.1
- Fabric 1.21.1
