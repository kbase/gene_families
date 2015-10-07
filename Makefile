KB_TOP ?= /kb/dev_container
KB_RUNTIME ?= /kb/runtime
DEPLOY_RUNTIME ?= $(KB_RUNTIME)
TARGET ?= /kb/deployment
CURR_DIR = $(shell pwd)
SERVICE_NAME = KBaseGeneFamilies
SERVICE = $(shell basename $(CURR_DIR))
SERVICE_DIR = $(TARGET)/services/$(SERVICE)
LIB_JARS_DIR = $(KB_TOP)/modules/jars/lib/jars
SERVICE_PORT = 8123
THREADPOOL_SIZE = 20
MEMORY = 1000
MAX_MEMORY = 10000
GLASSFISH_HOME ?= $(DEPLOY_RUNTIME)/glassfish3
ASADMIN = $(GLASSFISH_HOME)/glassfish/bin/asadmin
ANT = ant

default: compile build-docs

deploy-all: deploy

deploy: deploy-client deploy-service deploy-scripts deploy-docs

test: test-all

# temporarily removed deps for test-all: test-service test-client test-scripts
test-all: test-java

test-client:
	@echo "All tests are in java; make test-java"

test-service:
	@echo "All tests are in java; make test-java"

test-scripts:
	@echo "No scripts"

test-java:
	$(ANT) test

compile: download-thirdparty-bins
	$(ANT) war

setup-lib-dir:
	mkdir -p lib/biokbase/$(SERVICE_NAME)
	mkdir -p lib/javascript/$(SERVICE_NAME)

compile-typespec: setup-lib-dir
	compile_typespec \
		--client Bio::KBase::$(SERVICE_NAME)::Client \
		--py biokbase/$(SERVICE_NAME)/Client \
		--js javascript/$(SERVICE_NAME)/Client \
		--url https://kbase.us/services/gene_families \
		$(SERVICE_NAME).spec lib
	rm -f lib/$(SERVICE_NAME)*.py
	rm -f lib/$(SERVICE_NAME)*.pm

build-docs:
	mkdir -p docs
	pod2html --infile=lib/Bio/KBase/$(SERVICE_NAME)/Client.pm --outfile=docs/$(SERVICE_NAME).html
	rm -f pod2htmd.tmp
	$(ANT) javadoc

compile-java-typespec: KBaseGeneFamilies.spec
	./generate_java_classes.sh

download-thirdparty-bins:
	./download_3rd_party_bins.sh

prepare-thirdparty-dbs:	download-thirdparty-bins
	./prepare_3rd_party_dbs.sh

prepare-library-objects: prepare-thirdparty-dbs compile
	java -jar dist/$(SERVICE_NAME).jar

prepare-deploy-target:	download-thirdparty-bins

deploy-client:
	mkdir -p $(TARGET)/lib/Bio/KBase/$(SERVICE_NAME)
	mkdir -p $(TARGET)/lib/biokbase/$(SERVICE_NAME)
	mkdir -p $(TARGET)/lib/javascript/$(SERVICE_NAME)
	touch lib/biokbase/__init__.py #do not include code in biokbase/__init__.py
	touch lib/biokbase/$(SERVICE_NAME)/__init__.py
	cp lib/Bio/KBase/$(SERVICE_NAME)/Client.pm $(TARGET)/lib/Bio/KBase/$(SERVICE_NAME)/.
	cp lib/biokbase/$(SERVICE_NAME)/* $(TARGET)/lib/biokbase/$(SERVICE_NAME)/.
	cp lib/javascript/$(SERVICE_NAME)/* $(TARGET)/lib/javascript/$(SERVICE_NAME)/.
	cp dist/KBaseGeneFamilies.jar $(TARGET)/lib/.
	echo "deployed clients of $(SERVICE)."

deploy-service:	prepare-deploy-target
	@echo "Service folder: $(SERVICE_DIR)"
	mkdir -p $(SERVICE_DIR)
	cp -f ./dist/KBaseGeneFamilies.war $(SERVICE_DIR)
	cp -f ./service/glassfish_administer_service.py $(SERVICE_DIR)
	cp -f ./deploy.cfg $(SERVICE_DIR)
	echo '#!/bin/sh' > $(SERVICE_DIR)/start_service
	echo 'if [ -z "$$KB_DEPLOYMENT_CONFIG" ]' > $(SERVICE_DIR)/start_service
	echo 'then' >> $(SERVICE_DIR)/start_service
	echo ' export KB_DEPLOYMENT_CONFIG=$$KB_TOP/deployment.cfg' >> $(SERVICE_DIR)/start_service
	echo 'fi' >> $(SERVICE_DIR)/start_service
	echo "$(SERVICE_DIR)/glassfish_administer_service.py --admin $(ASADMIN)\
	 --domain $(SERVICE_NAME) --domain-dir $(SERVICE_DIR)/glassfish_domain\
	 --war $(SERVICE_DIR)/KBaseGeneFamilies.war --port $(SERVICE_PORT)\
	 --threads $(THREADPOOL_SIZE) --Xms $(MEMORY) --Xmx $(MAX_MEMORY)\
	 --noparallelgc --properties KB_DEPLOYMENT_CONFIG=\$$KB_DEPLOYMENT_CONFIG"\
	 >> $(SERVICE_DIR)/start_service
	chmod +x $(SERVICE_DIR)/start_service
	echo '#!/bin/sh' > $(SERVICE_DIR)/stop_service
	echo "$(SERVICE_DIR)/glassfish_administer_service.py --admin $(ASADMIN)\
	 --domain $(SERVICE_NAME) --domain-dir $(SERVICE_DIR)/glassfish_domain\
	 --port $(SERVICE_PORT)" >> $(SERVICE_DIR)/stop_service
	chmod +x $(SERVICE_DIR)/stop_service
	echo '#!/bin/sh' > $(SERVICE_DIR)/stop_domain
	echo "$(ASADMIN) stop-domain --domaindir $(SERVICE_DIR)/glassfish_domain $(SERVICE_NAME)"\
	 >> $(SERVICE_DIR)/stop_domain
	chmod +x $(SERVICE_DIR)/stop_domain

deploy-scripts:
	@echo "No deployment for scripts"

deploy-docs:
	$(ANT) javadoc
	rsync -a ./docs $(SERVICE_DIR)

clean:
	rm -rf ./classes
	rm -rf ./docs
