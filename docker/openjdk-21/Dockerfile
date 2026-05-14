# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jre-noble
ARG VERSION="dev"
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


# Shomei tar.gz file (from gradlew distTar)
ARG TAR_FILE
# Validate that the TAR_FILE argument is provided
RUN if [ -z "$TAR_FILE" ]; then echo "TAR_FILE build argument is required" && exit 1; fi

RUN groupadd --system shomei && \
    useradd --system --gid shomei --home-dir /opt/shomei --create-home --shell /usr/sbin/nologin shomei && \
    chmod 0755 /opt/shomei

WORKDIR /opt/shomei

# Extract the tar.gz file and rename the directory
RUN --mount=type=bind,source=${TAR_FILE},target=/tmp/shomei.tar.gz,ro \
    tar -xzf /tmp/shomei.tar.gz -C /opt/shomei --strip-components=1 && \
    chown -R shomei:shomei /opt/shomei

USER shomei

# Expose services ports
# 8888 HTTP JSON-RPC
EXPOSE 8888
# defaults for host interfaces
ENV SHOMEI_RPC_HTTP_HOST="0.0.0.0"
ENV SHOMEI_PID_PATH="/tmp/pid"
ENV OTEL_RESOURCE_ATTRIBUTES="service.name=shomei,service.version=$VERSION"
ENV OLDPATH="${PATH}"
ENV PATH="/opt/shomei/bin:${OLDPATH}"

HEALTHCHECK --start-period=5s --interval=5s --timeout=1s --retries=10 CMD bash -c ':</dev/tcp/127.0.0.1/8888'

ENTRYPOINT ["/opt/shomei/bin/shomei"]
