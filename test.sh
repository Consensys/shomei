#!/bin/bash

# Define the semver regex pattern
semver_regex=

# The version string to check
version="25.123.11"

# Check if the version matches the semver pattern
if [[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "$version is a valid semver"
else
  echo "$version is not a valid semver"
fi

github_ref="refs/heads/main"
git_event="pull_request"
if [ "$github_ref" == "refs/heads/main" ] && [ "$git_event" != "pull_request" ] && [ "$git_event" != "schedule" ] ; then
    echo "push=true"
else
    echo "push=false" 
fi