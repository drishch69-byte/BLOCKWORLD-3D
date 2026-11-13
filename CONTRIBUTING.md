# Contributing to CUBEVERSE 3D

Thanks for helping improve the project.

## Before opening an issue
- Check whether the feature or bug has already been reported.
- Include the exact game version and Java version.
- For bugs, add clear reproduction steps and relevant log excerpts.
- Do not post passwords, access tokens, or personal information.

## Development guidelines
- Keep gameplay rules in small, testable Java classes where practical.
- Add or update tests when changing recipes, inventory, world generation, or persistence.
- Preserve compatibility with existing saves unless a migration is explicitly implemented and tested.
- Do not silently change the behavior of older selectable version profiles.
- Document new controls and update the changelog.

## Build and test
Use JDK 17 and Maven:

```bash
mvn clean package
mvn test
```

A source-level check is not a substitute for testing the actual game window and graphics behavior.

## Mods and safety
Java mods execute code with the permissions of the current user. Only install and test mods from sources you trust. Mod code should clearly document any access to files, network resources, or other user data.
