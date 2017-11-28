#!/bin/bash

set -eu

mvn clean compile assembly:single

repo="eqrx/mauzr-cep"
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
commit=$(git rev-parse HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)
branch=${branch#heads/}
if [ "$branch" == "master" ]
then
  branch="latest"
fi

docker build -t $repo:$branch -f .dockerfile --pull --build-arg VERSION=$branch --build-arg VCS_REF=$commit --build-arg BUILD_DATE=$timestamp .
docker tag $repo:$branch $repo:$commit

docker push $repo:$branch
docker push $repo:$commit
