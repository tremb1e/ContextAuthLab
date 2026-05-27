.PHONY: build build-images build-server-image build-app-image apk up down restart logs shell health sample-ingest backup prune-data clean-data test-server test-android test-e2e test-docker test-load test-all server-test android-test e2e docker-test

build:
	docker compose build

build-images: build-server-image build-app-image

build-server-image:
	docker build -t contextauthlab/server:$${VERSION:-latest} ./server

apk:
	JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ./gradlew :android-app:assembleDebug
	mkdir -p artifacts
	cp android-app/build/outputs/apk/debug/android-app-debug.apk artifacts/contextauthlab-debug.apk

build-app-image: apk
	docker build -f android-app/Dockerfile.artifact -t contextauthlab/android-app-debug:$${VERSION:-latest} android-app

up:
	docker compose up -d

down:
	docker compose down

restart:
	docker compose restart contextauthlab-server

logs:
	docker compose logs -f contextauthlab-server

shell:
	docker compose exec contextauthlab-server sh

health:
	curl -fsS http://127.0.0.1:8000/health

sample-ingest:
	python tools/send_sample_batch.py --server http://127.0.0.1:8000

backup:
	mkdir -p backups
	tar -czf backups/contextauthlab-$$(date +%Y%m%d-%H%M%S).tar.gz data/paper logs

prune-data:
	@echo "Manual retention review only; prune tool is not shipped in this prototype."

clean-data:
	rm -rf data/paper logs

test-server:
	PYTHONPATH=server pytest -q server/tests

test-android:
	JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ./gradlew :android-app:testDebugUnitTest

test-e2e:
	bash tools/test_e2e.sh

test-docker:
	bash tools/test_docker_deployment.sh

test-load:
	python tools/test_load.py --iterations 60 --interval 5 --devices 50

test-all: test-server test-android test-e2e test-docker

server-test: test-server
android-test: test-android
e2e: test-e2e
docker-test: test-docker
