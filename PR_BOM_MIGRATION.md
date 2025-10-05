title: feat: migrate to cashu-platform-bom for centralized version management

## Summary
Related issue: #____
Migrate cashu-wallet to use `cashu-platform-bom` for centralized dependency version management across the Cashu ecosystem. This eliminates duplicate version properties and ensures consistent dependency versions.

## What changed?
- **Version bump**: `0.1.2` → `0.1.3`
- Replace 20+ version properties with single `cashu-platform-bom.version` property (F:pom.xml†L45)
- Import `cashu-platform-bom:1.0.0` in `dependencyManagement` (F:pom.xml†L50-L57)
- Remove version tags from all dependencies in child modules:
  - `cashu-wallet-protocol`: removed versions for cashu-lib dependencies (F:cashu-wallet-protocol/pom.xml)
  - `cashu-wallet-client`: removed versions for Spring Boot, cashu-lib dependencies (F:cashu-wallet-client/pom.xml†L46-L56)
- Simplify plugin management - versions now inherited from BOM (F:pom.xml†L64-L88)
- Remove unused `spring-boot-maven-plugin` from cashu-wallet-client build section

## Benefits
- **Single source of truth**: All Cashu ecosystem versions managed in one place
- **Consistency**: Identical dependency versions across all Cashu projects
- **Simplified updates**: Bump dependency versions once in BOM, all projects inherit it
- **Reduced duplication**: From 20+ version properties to 1
- **Cleaner builds**: Removed unnecessary plugin declarations

## Architecture
```
cashu-wallet (this project)
  └─ imports cashu-platform-bom
       ├─ imports nostr-java-bom (shared: Jackson, Lombok, BouncyCastle, test deps)
       ├─ imports spring-boot-dependencies
       ├─ defines all Cashu module versions
       └─ defines Cashu-specific dependencies
```

## BREAKING
None. Internal build configuration change only; no API or runtime behavior changes.

## Protocol Compliance
- No change to protocol semantics or endpoints. Behavior remains compliant with Cashu NUTs (see https://github.com/cashubtc/nuts/blob/main/00.md and related NUTs).

## Testing
- ✅ `mvn clean compile -U` - BUILD SUCCESS
- All modules compile successfully with BOM-managed versions
- No warnings or dependency resolution errors

## Checklist
- [x] Title uses `type: description`
- [x] File citations included
- [x] Version bumped to 0.1.3
- [x] Build verified with BOM
- [x] No functional changes; protocol compliance unchanged
- [x] BOM deployed to https://maven.398ja.xyz/releases/xyz/tcheeric/cashu-platform-bom/1.0.0/
