# Agent Rules

## Code Quality Plugins

When working with Maven code quality plugins (Checkstyle, PMD, SpotBugs, Detekt, JaCoCo, etc.), you **MUST**:

1. **Always use the latest versions** - Never downgrade plugins. Always fetch the latest stable versions from Maven Central.
2. **Verify plugin compatibility** - Ensure the plugin version supports the project's Java/Kotlin version.
3. **Configure executions properly** - Add `<executions>` blocks so plugins run automatically during appropriate build phases.
4. **Distinguish between plugin and library versions** - For plugins like SpotBugs, the plugin version (e.g., `4.9.3.0`) differs from the annotations/library version (e.g., `4.9.3`).
5. **Use correct property names** - Create separate properties for plugin versions vs library versions when they differ.

### Current Latest Versions (as of 2026-03-13)
- Checkstyle Plugin: `3.6.0`
- PMD Plugin: `3.28.0`
- SpotBugs Plugin: `4.9.3.0`
- SpotBugs Annotations: `4.9.3`
- JaCoCo: `0.8.14`
- Detekt: `1.23.8`

### Example Configuration
```xml
<properties>
    <spotbugs.plugin.version>4.9.3.0</spotbugs.plugin.version>
    <spotbugs.version>4.9.3</spotbugs.version>
    <checkstyle.version>3.6.0</checkstyle.version>
    <pmd.version>3.28.0</pmd.version>
</properties>
```
