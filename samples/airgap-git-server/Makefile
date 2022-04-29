ifneq (,$(wildcard ./build-args.env))
    include build-args.env
    export
endif

.PHONY: build
build:
	docker build build/ -t ${DOCKER_IMAGE_NAME} --pull --build-arg REPO=${REPO} --build-arg EMAIL=${EMAIL} --build-arg USERNAME=${USERNAME} --build-arg BUNDLE_ARTIFACTS_DIR=${BUNDLE_ARTIFACTS_DIR}

.PHONY: push
push:
	docker push ${DOCKER_IMAGE_NAME}