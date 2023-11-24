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

## Usage

Shomei can be launched in two different modes:

### Mode 1: Trace Generation

The first mode is used for the generation of traces so that the prover can retrieve them.
To use this mode, you need to activate it with these flags: `--enable-trace-generation=true` and `--trace-start-block-number=BLOCK_NUMBER`. The `--trace-start-block-number` flag allows you to define from when we want to start the generation of traces.
This allows for a faster sync rather than generating traces for old blocks that we no longer need.

it's important to enable `--min-confirmations-before-importing` if you have reorg in your network (in general 2 for goerli and 4 for mainnet in linea)

```bash
shomei --enable-trace-generation=true --trace-start-block-number=1970000
```

### Mode 2: Serving GetProof for Finalized Blocks

The second mode is used to serve getProof for finalized blocks. This mode does not require the generation of traces, so you should use this `-enable-trace-generation=false` flag. In addition, you should add `--enable-finalized-block-limit=true`, `--use-finalized-block-number=BLOCK_NUMBER`, and `--use-finalized-block-hash=BLOCK_HASH`. 
Setting these will allow Shomei to stop the sync at this block and thus be able to serve the proof for it. It is recommended to choose a finalized block. Then the node will stop and wait before moving forward. 
As long as it does not move forward, it can serve the getProof of this block. 
As soon as the coordinator calls `rollup_forkChoiceUpdated` (for passing the new finalized block), Shomei will again move forward to this last one and stop.
It can thus serve the getProof for this last one and those before it (knowing that there is a cache to serve the last 128 finalized blocks encountered by the node since it started).

it's important to enable `--min-confirmations-before-importing` if you have reorg in your network (in general 2 for goerli and 4 for mainnet in linea)

```bash
shomei --enable-trace-generation=false  --enable-finalized-block-limit=true --use-finalized-block-number=1855350 --use-finalized-block-hash=0xabc0cca83e3eec5a0a30db97dcd4fbbec07361f38c4395c9f79ecf15ee92a07c
```

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