# Shomei zkevm state manager


**Shomei zkevm state manager** extends the Hyperledger Besu functionality.
This component communicates with Besu in order to maintain and update the zkevm state

## Shomei developers

* [Contribution Guidelines](CONTRIBUTING.md)
* [Coding Conventions](https://wiki.hyperledger.org/display/BESU/Coding+Conventions)


## Binary Releases

Binary releases are available from the [releases page](https://github.com/ConsenSys/shomei/releases).
Binary builds that track the latest changes on the main branch are available on
[Dockerhub](https://hub.docker.com/r/consensys/linea-shomei) 

We recommend only using release versions for Mainnet, but `develop` builds are useful for testing
the latest changes on testnets.

## Build Instructions

### Install Prerequisites

* Java 17

### Build and Test

To build, clone this repo and run with `gradle`:

```shell script
git clone https://github.com/ConsenSys/shomei.git
cd shomei && ./gradlew
```

After a successful build, distribution packages are available in `build/distributions`.

### Other Useful Gradle Targets

| Target      | Builds                                                  |
|-------------|---------------------------------------------------------|
| distTar     | Full distribution in build/distributions (as `.tar.gz`) |
| distZip     | Full distribution in build/distributions (as `.zip`)    |
| installDist | Expanded distribution in `build/install/shomei`         |
| dockerDist  | The `consensys/linea-shomei` docker image               |

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```shell script 
./gradlew spotlessApply
```

Code style is checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:

```shell script 
./gradlew test
```

## Release process
The release process is automated via github actions, but requires a few steps.  To release:
### Step 1 Create a PR that updates the shomei to a release version
  * Update the version in `gradle.properties` to the release version
  * Release version should be [major].[minor].[patch], for example 1.2.0. 
  * Once the PR merges, github actions will trigger a build and publish of the image to dockerhub
### Step 2 Create a release tag
  * Create a release tag with the same version as the release version, for example 1.2.0
  * Push the tag to the repo, this will trigger a github action that creates draft release on github.  
  * Edit the draft and publish it.
### Step 3 Create a PR that updates shomei to the next snapshot version
  * Update the version in `gradle.properties` to the next snapshot version, for example 1.2.1-SNAPSHOT
  * Once the PR merges, github actions will trigger a build and publish of the `develop` image to dockerhub

Finally, notify the team that the release is complete.