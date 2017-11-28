FROM alpine:edge

RUN apk add --no-cache openjdk8-jre

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION

LABEL \
org.label-schema.schema-version=1.0 \
org.label-schema.name=mauzr-cep \
org.label-schema.vcs-url=https://github.com/eqrx/mauzr-cep \
org.label-schema.vcs-ref=$VCS_REF \
org.label-schema.version=$VERSION \
net.eqrx.mauzr.cep.version=$VERSION \
net.eqrx.mauzr.cep.vcs-ref=$VCS_REF

COPY target/cep-1-jar-with-dependencies.jar /opt/mauzr-cep.jar
