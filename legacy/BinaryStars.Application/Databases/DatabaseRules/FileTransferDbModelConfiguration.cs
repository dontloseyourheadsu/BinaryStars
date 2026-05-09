using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

/// <summary>
/// Entity configuration for <see cref="FileTransferDbModel"/>.
/// </summary>
public class FileTransferDbModelConfiguration : IEntityTypeConfiguration<FileTransferDbModel>
{
    /// <summary>
    /// Configures the file transfer entity schema rules.
    /// </summary>
    /// <param name="builder">The entity type builder.</param>
    public void Configure(EntityTypeBuilder<FileTransferDbModel> builder)
    {
        builder.HasKey(t => t.Id);

        builder.Property(t => t.FileName)
            .IsRequired()
            .HasMaxLength(500);

        builder.Property(t => t.ContentType)
            .IsRequired()
            .HasMaxLength(200);

        builder.Property(t => t.SenderDeviceId)
            .IsRequired()
            .HasMaxLength(200);

        builder.Property(t => t.TargetDeviceId)
            .IsRequired()
            .HasMaxLength(200);

        builder.Property(t => t.KafkaTopic)
            .IsRequired()
            .HasMaxLength(200);

        builder.Property(t => t.KafkaAuthMode)
            .IsRequired()
            .HasMaxLength(50);

        builder.Property(t => t.EncryptionEnvelope)
            .IsRequired(false)
            .HasColumnType("jsonb");

        builder.Property(t => t.CreatedAt)
            .IsRequired();

        builder.Property(t => t.ExpiresAt)
            .IsRequired();

        builder.HasIndex(t => t.SenderUserId);
        builder.HasIndex(t => t.TargetUserId);
        builder.HasIndex(t => t.TargetDeviceId);
        builder.HasIndex(t => t.Status);
        builder.HasIndex(t => t.ExpiresAt);
    }
}
