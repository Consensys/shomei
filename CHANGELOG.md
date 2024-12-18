# Changelog

## 2.3.0 - 2024-10-16
Java 21 is required for this shomei release.  

### Additions and Improvements
- add block head metrics [#100](https://github.com/Consensys/shomei/pull/100)
- bump to latest besu dep (24.9.1), java 21, and docker simplification [#101](https://github.com/Consensys/shomei/pull/101)

### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.3.0



## 2.2.2 - 2024-09-12

### Bug Fixes
- Added a fix for an issue on the getProof RPC call. The bug could have led to invalid proofs when leaves were deleted from the trie[#92](https://github.com/Consensys/shomei/pull/92)

### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.2.2


## 2.2.1 - 2024-09-09
 
⚠️ A resync is only required if upgrading from a version prior to 2.1. 

### Bug Fixes
- create snapshot on archive creation, to address restart issue  [#89]

### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.2.1


## 2.2.0
⚠️ This release introduces a breaking change to the RPC method `rollup_getProof` which has been renamed to `linea_getProof`.
A resync is only required if upgrading from a version prior to 2.1. 

### Breaking Changes
- RPC method `rollup_getProof` has been renamed to `linea_getProof`

### Additions and Improvements
- Various build and CI improvements.

### Bug Fixes

### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.2.0

## 2.1.1
This is a minor release on top of version 2.1.0.  A resync is required only if upgrading from a version prior to 2.1

### Additions and Improvements
* add zkEndStateRootHash to trace response [#77](https://github.com/Consensys/shomei/pull/77)

### Bug Fixes

### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.1.1

## 2.1.0

⚠️  It is important to upgrade to this version in order to obtain a correct world state. **It is also necessary to resync from scratch.**

Version 2.1.0 requires a re-sync of the state and requires version [0.3.0](https://github.com/Consensys/besu-shomei-plugin/releases/tag/v0.3.0)+ of the besu-shomei plugin.

### Release Date 2024-01-11

### Additions and Improvements

### Bug Fixes
- Updates the parameters of the zktrie to complete the integration of the new mimc bl12-377 hash [#73](https://github.com/Consensys/shomei/pull/73)

### Release Link

https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.1.0

## 2.0.0

Version 2.0.0 requires a re-sync of the state and requires version [0.3.0](https://github.com/Consensys/besu-shomei-plugin/releases/tag/v0.3.0)+ of the besu-shomei plugin.

### Release Date 2024-01-05

### Additions and Improvements
- Added support for Mimc on bls12-377 [#69](https://github.com/Consensys/shomei/pull/69)

### Bug Fixes
- Added a fix to correctly handle the scenario of contract self-destruction and recreation within the same block by creating a new tree for the recreated contract. [#68](https://github.com/Consensys/shomei/pull/68)
### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=2.0.0

## 1.4.1

### Release Date 2023-07-20
### Additions and Improvements
### Bug Fixes
- fix for worldstate trie creation logic [#64](https://github.com/Consensys/shomei/pull/64)
### Release Link
https://hub.docker.com/r/consensys/linea-shomei/tags?name=1.4.1


## 1.4.0
### Release Date 2023-10-23
### Additions and Improvements
### Bug Fixes
- Block limit for import block step [#62](https://github.com/Consensys/shomei/pull/62)
### Release Link
Link : https://hub.docker.com/r/consensys/linea-shomei/tags?name=1.4.0


## 1.3.0
### Release Date 2023-07-20
### Additions and Improvements
### Bug Fixes
- fix for worldstate partitioning [#55](https://github.com/Consensys/shomei/pull/55)
### Release Link
Link : https://hub.docker.com/r/consensys/linea-shomei/tags?name=1.3.0


## 1.2.0 
### Release Date 2023-07-11
### Additions and Improvements
- revamp docker release publishing [#48](https://github.com/Consensys/shomei/pull/48)
- Remove pretty-printer from json-rpc [#47](https://github.com/Consensys/shomei/pull/47)
### Bug Fixes
- fix transaction closed issue [#45](https://github.com/Consensys/shomei/pull/45)
### Release Link
Link : https://hub.docker.com/r/consensys/linea-shomei/tags?name=1.2.0


## 1.1.1
### Additions and Improvements
- Add --trace-start-block-number flag [#43](https://github.com/Consensys/shomei/pull/43)
### Release Link
Link : https://hub.docker.com/r/consensys/linea-shomei/tags?name=1.1.1

## 1.0.0
### Additions and Improvements
- Initial feature complete release

## 0.0.01
### Additions and Improvements
- Init project
### Bug Fixes
