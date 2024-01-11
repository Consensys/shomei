# Changelog

## 2.1.0

### Additions and Improvements

### Bug Fixes

### Release Link

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

⚠️  It is important to upgrade to this version in order to obtain a correct world state. **It is also necessary to resync from scratch.**

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
