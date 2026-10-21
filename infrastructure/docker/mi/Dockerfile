# WSO2 Micro Integrator runtime for the Jamii Savings integrations.
# Adds the PostgreSQL JDBC driver used by AccountsDataService. CARs are not baked
# in: they are deployed by scripts/deploy-mi.sh, so the same image serves
# every environment and every build.
ARG MI_VERSION=4.6.0
FROM wso2/wso2mi:${MI_VERSION}

ARG POSTGRES_CONNECTOR_VERSION=42.7.3
ADD --chown=wso2carbon:wso2 \
    https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_CONNECTOR_VERSION}/postgresql-${POSTGRES_CONNECTOR_VERSION}.jar \
    ${WSO2_SERVER_HOME}/lib/

COPY --chown=wso2carbon:wso2 deployment.toml ${WSO2_SERVER_HOME}/conf/deployment.toml

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
    CMD curl -sf http://localhost:9201/healthz || exit 1