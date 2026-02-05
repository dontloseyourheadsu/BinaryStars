# Kafka TLS/SASL (Dev)

This folder documents the local Kafka setup with TLS + SASL and the related app settings.
Do not commit real secrets or private keys to the repository.

## Files

- `kafka/secrets/ca.pem`, `kafka/secrets/client.pem`, `kafka/secrets/client.key` are used by the API client.
- `kafka/secrets/broker.pem`, `kafka/secrets/broker.key` are used by the broker.
- `kafka/secrets/kafka_jaas.conf` configures broker auth.
- `kafka/secrets/kafka_client.properties` is used by the CLI to create SCRAM users.

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

openssl genrsa -out client.key 4096
openssl req -new -key client.key -subj "/CN=binarystars-client" -out client.csr
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -out client.pem -days 3650 -sha256

mv ca.pem ca.key broker.pem broker.key client.pem client.key ../secrets/
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
docker-compose up -d binarystars.zookeeper binarystars.kafka
```

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
