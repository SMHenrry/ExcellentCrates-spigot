# Paper 26.3 integration probe

Compile `Paper26_3Probe.java` with Java 25 using the built ExcellentCrates jar, NightCore 2.16.6, and Paper's API/server library jars on the classpath. Package the resulting class and this directory's `plugin.yml` into a separate probe jar. Install it only on a disposable Paper 26.3 server alongside this fork and NightCore.

The probe exercises real NightCore decoding, modern and legacy item config formats, base-32 binary NBT rewards, nested bundle items, idempotence, and preservation of custom NBT numeric/array types. Expected result: `COMPATIBILITY CHECKS PASSED` and no `NbtSerializer` decode errors. Unknown registry references outside the two explicitly migrated names remain errors and are not silently stripped.

Use JVM option `-Dcrates.probe.stop=true` to shut down the disposable server automatically after the checks. Never install the probe on a production server.
