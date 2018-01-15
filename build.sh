#!/bin/bash

set -eu

mvn clean compile assembly:single

repo="mauzr/cep"
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
commit=$(git rev-parse HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)
branch=${branch#heads/}
if [ "$branch" == "master" ]
then
  branch=""
fi
tag="$repo:main-$commit"

docker build -t $tag -f .dockerfile --pull --build-arg VERSION=$branch --build-arg VCS_REF=$commit --build-arg BUILD_DATE=$timestamp .
docker push $tag
