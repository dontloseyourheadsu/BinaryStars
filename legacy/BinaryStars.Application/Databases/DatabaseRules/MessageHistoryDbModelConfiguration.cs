using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.DatabaseModels.Messaging;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

/// <summary>
/// Entity configuration for <see cref="MessageHistoryDbModel"/>.
/// </summary>
public class MessageHistoryDbModelConfiguration : IEntityTypeConfiguration<MessageHistoryDbModel>
{
    /// <summary>
    /// Configures message history schema rules.
    /// </summary>
    public void Configure(EntityTypeBuilder<MessageHistoryDbModel> builder)
    {
        builder.HasKey(m => m.Id);

        builder.Property(m => m.SenderDeviceId)
            .IsRequired();

        builder.Property(m => m.TargetDeviceId)
            .IsRequired();

        builder.Property(m => m.Body)
            .IsRequired()
            .HasMaxLength(500);

        builder.Property(m => m.SentAt)
            .IsRequired();

        builder.Property(m => m.CreatedAt)
            .IsRequired();

        builder.HasOne<UserDbModel>()
            .WithMany()
            .HasForeignKey(m => m.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasIndex(m => new { m.UserId, m.SentAt });
        builder.HasIndex(m => new { m.UserId, m.SenderDeviceId, m.TargetDeviceId, m.SentAt });
    }
}
