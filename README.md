# Minecraft Fabric Mods

A collection of Fabric mods for Minecraft.

## Mods

| Mod | Description |
|-----|-------------|
| **True Infinity** | Allows bows with the Infinity enchantment to fire without any arrows in inventory |
| **Hot Deposit** | Quickly deposit items into nearby containers via keybind (N key) |
| **Craft From Nearby Storage** | Access items from nearby containers when crafting |
| **Dev Auth** | Authenticate with Microsoft while developing Minecraft mods |

## Branching Strategy

This repository uses **version branching** where each branch contains only the mods compatible with that Minecraft version.

### Branch Structure

| Branch | Purpose |
|--------|---------|
| `main` | Latest version of all mods targeting the newest Minecraft version |
| `1.21.11` | Mods targeting Minecraft 1.21.11 |
| `1.21.3` | Mods targeting Minecraft 1.21.3 |
| `1.20.1` | Mods targeting Minecraft 1.20.1 |

### Current Status

| Mod | Current Branch | Minecraft Version | Target |
|-----|----------------|-------------------|--------|
| True Infinity | `main`, `1.21.11` | 1.21.11 | ✓ Latest |
| Dev Auth | `main`, `1.21.11` | 1.21.11 | ✓ Latest |
| Hot Deposit | `1.21.3` | 1.21.3 | Needs update to 1.21.11 |
| Craft From Nearby Storage | `1.20.1` | 1.20.1 | Needs update to 1.21.11 |

### Workflow

1. When updating a mod to a new Minecraft version:
    - Update the mod on its current version branch
    - Create/update the new version branch with the updated mod
    - Once all mods are on the latest version, merge to `main`

2. Each version branch contains **only** the mods that target that specific Minecraft version

3. `main` always reflects the latest Minecraft version with all mods updated

## Git Commands

### Standard development workflow (keeping main and version branch in sync)

Develop on the version branch, then fast-forward main to match. This keeps the **same commit hash** on both branches.

```bash
# 1. Develop on version branch
git checkout 1.21.11
git add <files>
git commit -m "message"
git push origin 1.21.11

# 2. Fast-forward main to match (same commit, same hash)
git checkout main
git merge --ff-only 1.21.11
git push origin main
```

> **Note:** `--ff-only` ensures main can only be fast-forwarded. If branches have diverged, it will fail.
>
> **cherry-pick vs merge:**
> - `cherry-pick` = same changes, **different hash**
> - `merge --ff-only` = **same commit, same hash**

### Creating a new version branch

```bash
# Create orphan branch (clean slate, no history)
git checkout --orphan <version>
git rm -rf --cached .

# Add mods for this version
git add <mod_directory>/ .gitignore README.md
git commit -m "Add <mod_name> mod for Minecraft <version>"
git push -u origin <version>
```

### Adding a mod to a specific version branch only

```bash
git checkout <version>
git add <mod_directory>/
git commit -m "Add <mod_name> mod for Minecraft <version>"
git push origin <version>
```

## Building

Each mod is built independently from its directory:

```bash
cd <mod_directory>
./gradlew build
```

Output JARs are placed in `<mod>/build/libs/`.

## Testing

Run Minecraft with the mod loaded:

```bash
cd <mod_directory>
./gradlew runClient
```

## License

See individual mod directories for license information.
