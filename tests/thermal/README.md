# Local Thermal test profile

This folder describes the first integration workload. Original CoFH binaries are intentionally not committed.

1. Obtain the official original CoFH JARs through the project's official distribution channels.
2. Place them in `tests/thermal/jars/`.
3. Run `python3 tools/inspect_mod_jar.py tests/thermal/jars/*.jar`.
4. Save the output and SHA-256 values in a local report, not in this folder.
5. Do not run an untrusted JAR on a machine containing personal data. Use an isolated test instance.

The expected module IDs and source-version notes are in `profile.json`. The final runtime target is Minecraft 26.3 + Fabric; the historical 1.20.1 data is used only as a control for the old Forge API.

The repository CI uses synthetic JAR fixtures. It does not download or execute original CoFH binaries.
