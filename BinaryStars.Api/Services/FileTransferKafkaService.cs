using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using Confluent.Kafka;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

public record FileTransferKafkaPublishResult(int PacketCount, int? Partition, long? StartOffset, long? EndOffset);

public class FileTransferKafkaService
{
    private readonly KafkaSettings _settings;
    private readonly KafkaClientFactory _clientFactory;

    public FileTransferKafkaService(IOptions<KafkaSettings> settings, KafkaClientFactory clientFactory)
    {
        _settings = settings.Value;
        _clientFactory = clientFactory;
    }

    public async Task<FileTransferKafkaPublishResult> PublishFromFileAsync(
        FileTransferDbModel transfer,
        string filePath,
        KafkaAuthMode authMode,
        string? oauthToken,
        int chunkSizeBytes,
        CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await using var stream = File.OpenRead(filePath);

        var buffer = new byte[chunkSizeBytes];
        var packetIndex = 0;
        var offset = 0L;
        long? startOffset = null;
        long? endOffset = null;
        int? partition = null;

        int bytesRead;
        while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length, cancellationToken)) > 0)
        {
            var payload = new byte[bytesRead];
            Buffer.BlockCopy(buffer, 0, payload, 0, bytesRead);

            var key = BuildPacketKey(transfer.Id, packetIndex);
            var isLast = offset + bytesRead >= transfer.SizeBytes;
            var headers = BuildHeaders(transfer.Id, packetIndex, offset, bytesRead, transfer.SizeBytes, isLast);

            var result = await producer.ProduceAsync(
                transfer.KafkaTopic,
                new Message<string, byte[]>
                {
                    Key = key,
                    Value = payload,
                    Headers = headers
                },
                cancellationToken);

            partition ??= result.Partition.Value;
            startOffset ??= result.Offset.Value;
            endOffset = result.Offset.Value;

            packetIndex++;
            offset += bytesRead;
        }

        producer.Flush(TimeSpan.FromSeconds(10));

        return new FileTransferKafkaPublishResult(packetIndex, partition, startOffset, endOffset);
    }

    public async Task StreamToAsync(
        FileTransferDbModel transfer,
        Stream output,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        if (!transfer.KafkaPartition.HasValue || !transfer.KafkaStartOffset.HasValue || !transfer.KafkaEndOffset.HasValue)
            throw new InvalidOperationException("Transfer does not have Kafka offsets.");

        using var consumer = _clientFactory.CreateConsumer($"transfer-{transfer.Id}", authMode, oauthToken);
        var topicPartition = new TopicPartition(transfer.KafkaTopic, new Partition(transfer.KafkaPartition.Value));
        consumer.Assign(new TopicPartitionOffset(topicPartition, new Offset(transfer.KafkaStartOffset.Value)));

        var expectedPackets = transfer.PacketCount;
        var receivedPackets = 0;

        if (expectedPackets <= 0)
            return;

        while (!cancellationToken.IsCancellationRequested)
        {
            var consumeResult = consumer.Consume(TimeSpan.FromSeconds(2));
            if (consumeResult == null)
            {
                if (receivedPackets >= expectedPackets)
                    break;

                continue;
            }

            if (consumeResult.Offset > transfer.KafkaEndOffset.Value)
                break;

            if (consumeResult.Message?.Value == null)
                continue;

            if (!IsTransferPacket(consumeResult.Message, transfer.Id))
                continue;

            await output.WriteAsync(consumeResult.Message.Value, 0, consumeResult.Message.Value.Length, cancellationToken);
            receivedPackets++;

            if (receivedPackets >= expectedPackets)
                break;
        }
    }

    public async Task DeleteTransferPacketsAsync(
        FileTransferDbModel transfer,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        if (transfer.PacketCount <= 0)
            return;

        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);

        for (var packetIndex = 0; packetIndex < transfer.PacketCount; packetIndex++)
        {
            var key = BuildPacketKey(transfer.Id, packetIndex);
            var headers = BuildHeaders(transfer.Id, packetIndex, 0, 0, transfer.SizeBytes, true);
            await producer.ProduceAsync(
                transfer.KafkaTopic,
                new Message<string, byte[]>
                {
                    Key = key,
                    Value = null!,
                    Headers = headers
                },
                cancellationToken);
        }

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    private static string BuildPacketKey(Guid transferId, int packetIndex)
    {
        return $"{transferId:D}:{packetIndex:D8}";
    }

    private static Headers BuildHeaders(Guid transferId, int packetIndex, long offset, int length, long totalBytes, bool isLast)
    {
        var headers = new Headers();
        headers.Add("transferId", Encoding.UTF8.GetBytes(transferId.ToString("D")));
        headers.Add("packetIndex", Encoding.UTF8.GetBytes(packetIndex.ToString()));
        headers.Add("offset", Encoding.UTF8.GetBytes(offset.ToString()));
        headers.Add("length", Encoding.UTF8.GetBytes(length.ToString()));
        headers.Add("totalBytes", Encoding.UTF8.GetBytes(totalBytes.ToString()));
        headers.Add("isLast", Encoding.UTF8.GetBytes(isLast ? "1" : "0"));
        return headers;
    }

    private static bool IsTransferPacket(Message<string, byte[]> message, Guid transferId)
    {
        if (message.Key?.StartsWith(transferId.ToString("D"), StringComparison.OrdinalIgnoreCase) == true)
            return true;

        var header = message.Headers?.FirstOrDefault(h => h.Key == "transferId");
        if (header == null)
            return false;

        var value = Encoding.UTF8.GetString(header.GetValueBytes());
        return value.Equals(transferId.ToString("D"), StringComparison.OrdinalIgnoreCase);
    }
}
