# Building from source

If you have already downloaded the prebuilt packages you can skip to [Using the ReVanced CLI](7_usage.md).

Before continuing you need to be authenticated to GitHub Packages.
\
This will assume you have a GitHub account. Create a PAT with the scope `read:packages` [here](https://github.com/settings/tokens/new?scopes=read:packages&description=Revanced) and add your token to ~/.gradle/gradle.properties.
\
Example `gradle.properties` file:

```properties
gpr.user = YourUsername
gpr.key = ghp_longrandomkey
```

## Overview

1. [Building the ReVanced Patcher](building_revanced_patcher)
2. [Building the ReVanced Patches](building_revanced_patches)
3. [Building the ReVanced Integrations](building_revanced_integrations)
4. [Building the ReVanced CLI](building_revanced_cli)
