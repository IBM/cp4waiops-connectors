CONNECTOR_SDK_VERSION := 1.4.3

ifeq ($(shell uname -s),Darwin)
	# gnu-sed, can be installed using homebrew
	SED_EXE := gsed
else
	SED_EXE := sed
endif

# Build the docker image
.PHONY: docker-build-ci
docker-build-ci: docker/arch/build

# Push the docker image
.PHONY: docker-push-ci
docker-push-ci: docker/arch/image/push

.PHONY: test
test:
	cd connector && mvn test