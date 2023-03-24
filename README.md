# Shomei zkevm state manager


**Shomei zkevm state manager** extends the Hyperledger Besu functionality.
This component communicates with Besu in order to maintain and update the zkevm state

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

