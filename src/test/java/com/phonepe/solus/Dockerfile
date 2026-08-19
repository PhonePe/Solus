FROM eclipse-temurin:8-jdk

ENV HBASE_VERSION=2.0.1
ENV HBASE_HOME=/opt/hbase

RUN apt-get update && apt-get install -y wget net-tools procps && \
    wget https://archive.apache.org/dist/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz && \
    tar -xzvf hbase-${HBASE_VERSION}-bin.tar.gz && \
    mv hbase-${HBASE_VERSION} ${HBASE_HOME} && \
    rm hbase-${HBASE_VERSION}-bin.tar.gz

RUN echo "export JAVA_HOME=/opt/java/openjdk" >> ${HBASE_HOME}/conf/hbase-env.sh

EXPOSE 2181 16010 16020 16030

CMD ["sh", "-c", "${HBASE_HOME}/bin/hbase master start"]