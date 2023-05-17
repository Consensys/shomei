
FROM ubuntu:22.04
ARG VERSION="dev"

RUN apt-get update  && \
    apt-get install --no-install-recommends -q --assume-yes ca-certificates=20* ca-certificates-java=20* && \
    apt-get install --no-install-recommends -q --assume-yes curl=7* wget=1.21* jq=1.6* net-tools=1.60* openjdk-19-jdk-headless=19* libjemalloc-dev=5.* && \
    apt-get clean  && \
    rm -rf /var/lib/apt/lists/*  && \
    adduser --disabled-password --gecos "" --home /opt/shomei shomei && \
    chown shomei:shomei /opt/shomei && \
    chmod 0755 /opt/shomei

USER shomei
WORKDIR /opt/shomei

COPY --chown=shomei:shomei shomei /opt/shomei/

# Expose services ports
# 8888 HTTP JSON-RPC
EXPOSE 8888

# defaults for host interfaces
ENV SHOMEI_RPC_HTTP_HOST 0.0.0.0
ENV SHOMEI_PID_PATH "/tmp/pid"

ENV OTEL_RESOURCE_ATTRIBUTES="service.name=shomei,service.version=$VERSION"

ENV OLDPATH="${PATH}"
ENV PATH="/opt/shomei/bin:${OLDPATH}"

ENTRYPOINT ["shomei"]
HEALTHCHECK --start-period=5s --interval=5s --timeout=1s --retries=10 CMD bash -c "[ -f /tmp/pid ]"

# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Shomei" \
      org.label-schema.description="Shomei zkevm state manager " \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/ConsenSys/shomei.git" \
      org.label-schema.vendor="ConsenSys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

