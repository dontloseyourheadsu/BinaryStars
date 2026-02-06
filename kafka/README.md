# Kafka TLS/SASL (Dev)

This folder documents the local Kafka setup with TLS + SASL and the related app settings.
Do not commit real secrets or private keys to the repository.

## Files

- `kafka/secrets/ca.pem`, `kafka/secrets/client.pem`, `kafka/secrets/client.key` are used by the API client.
- `kafka/secrets/broker.pem`, `kafka/secrets/broker.p12` are used by the broker.
- `kafka/secrets/kafka_jaas.conf` configures broker auth.
- `kafka/secrets/kafka_client.properties` is used by the CLI to create SCRAM users.

## How It Works (Current)

Kafka runs in KRaft mode with TLS + SASL enabled on all broker listeners. The
broker listens on four ports:

- `9092` for the KRaft controller (PLAINTEXT).
- `9093` for internal broker traffic (SASL_SSL, SCRAM-SHA-512).
- `9094` for external client traffic (SASL_SSL, SCRAM-SHA-512).
- `9095` for OAuth bearer traffic (SASL_SSL, OAUTHBEARER).

Certificates and auth are mounted from `kafka/secrets` into the container at
`/etc/kafka/secrets`. The broker uses `broker.p12` as its keystore and
`ca.pem` as the truststore. Client certs are required on all TLS listeners.
The JAAS config for SCRAM and OAuth is loaded via
`KAFKA_OPTS=-Djava.security.auth.login.config=/etc/kafka/secrets/kafka_jaas.conf`.

The advertised listeners are:

- `INTERNAL://binarystars.kafka:9093`
- `EXTERNAL://localhost:9094`
- `OAUTH://localhost:9095`

Kafka is considered healthy when port `9093` accepts TCP connections.

## Generate TLS Certificates

Run these from the repo root to generate dev-only certificates:

```bash
mkdir -p kafka/secrets kafka/certs
cd kafka/certs

openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/CN=binarystars-ca" -out ca.pem

openssl genrsa -out broker.key 4096
openssl req -new -key broker.key -subj "/CN=binarystars.kafka" -out broker.csr
openssl x509 -req -in broker.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -out broker.pem -days 3650 -sha256
openssl pkcs8 -topk8 -nocrypt -in broker.key -out broker.pkcs8.key
openssl pkcs12 -export -in broker.pem -inkey broker.pkcs8.key -certfile ca.pem \
  -out broker.p12 -password pass:brokerpass

openssl genrsa -out client.key 4096
openssl req -new -key client.key -subj "/CN=binarystars-client" -out client.csr
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -out client.pem -days 3650 -sha256

mv ca.pem ca.key broker.pem broker.p12 client.pem client.key ../secrets/
rm -f *.csr *.srl
```

## Create Auth Config Files

Create `kafka/secrets/kafka_jaas.conf`:

```conf
KafkaServer {
  org.apache.kafka.common.security.scram.ScramLoginModule required
  username="broker"
  password="broker-secret";

  org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required
  unsecuredLoginStringClaim_sub="broker";
};
```

Create `kafka/secrets/kafka_client.properties`:

```properties
security.protocol=SASL_SSL
sasl.mechanism=SCRAM-SHA-512
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="broker" password="broker-secret";
ssl.truststore.type=PEM
ssl.truststore.location=/etc/kafka/secrets/ca.pem
ssl.keystore.type=PEM
ssl.keystore.certificate.chain=/etc/kafka/secrets/client.pem
ssl.keystore.key=/etc/kafka/secrets/client.key
```

## Start Kafka (Docker Compose)

```bash
docker compose up -d binarystars.kafka
```

Kafka is running in KRaft mode (no ZooKeeper). The cluster ID is defined in
[docker-compose.yaml](docker-compose.yaml) as `KAFKA_KRAFT_CLUSTER_ID`.

## Create SCRAM Users

After the broker starts, create the app user:

```bash
docker exec -it binarystars.kafka kafka-configs --bootstrap-server binarystars.kafka:9093 \
  --command-config /etc/kafka/secrets/kafka_client.properties \
  --alter --add-config 'SCRAM-SHA-512=[password=binarystars]' \
  --entity-type users --entity-name binarystars
```

## App Configuration

Update `BinaryStars.Api/appsettings.json` or environment variables:

```json
"Kafka": {
  "BootstrapServers": "binarystars.kafka:9093",
  "Topic": "binarystars.transfers",
  "Security": {
    "UseTls": true,
    "UseSasl": true,
    "CaPath": "kafka/secrets/ca.pem",
    "ClientCertPath": "kafka/secrets/client.pem",
    "ClientKeyPath": "kafka/secrets/client.key"
  },
  "Scram": {
    "Username": "binarystars",
    "Password": "binarystars"
  }
}
```

If you are using local Testcontainers for integration tests, set `Kafka:Security:UseSasl` to `false` and `Kafka:Security:UseTls` to `false`.

## Topic Retention (Dev Defaults)

Use delete + compact with a 1-hour retention window for packet cleanup:

```bash
docker exec -it binarystars.kafka kafka-configs --bootstrap-server binarystars.kafka:9093 \
  --command-config /etc/kafka/secrets/kafka_client.properties \
  --alter --entity-type topics --entity-name binarystars.transfers \
  --add-config cleanup.policy=compact,delete,retention.ms=3600000
```

## OAuth Bearer

The API uses the BinaryStars JWT as the OAuth bearer token when Kafka auth mode is `OauthBearer`.

## Notes

- These certificates are for local development only.
- For production, replace with managed CA-signed certs and a proper OAuth provider.
