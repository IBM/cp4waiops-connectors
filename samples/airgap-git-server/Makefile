ifneq (,$(wildcard ./build-args.env))
    include build-args.env
    export
endif

.PHONY: build
build:
	docker build build/ -t ${DOCKER_IMAGE_NAME} --pull --build-arg REPO=${REPO} --build-arg EMAIL=${EMAIL} --build-arg USERNAME=${USERNAME} --build-arg BUNDLE_ARTIFACTS_DIR=${BUNDLE_ARTIFACTS_DIR} --build-arg COMMIT_MESSAGE="${COMMIT_MESSAGE}"

.PHONY: push
push:
	docker push ${DOCKER_IMAGE_NAME}

.PHONY: get-helm
get-helm:
	which helm >> /dev/null 2>&1 || (curl -fsSL -o - https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash)

.PHONY: deploy
deploy: get-helm
	helm upgrade --install ${RELEASE_NAME} deploy/connector-airgap

.PHONY: uninstall
uninstall:
	helm uninstall ${RELEASE_NAME}