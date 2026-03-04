using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.DatabaseModels.Notifications;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

/// <summary>
/// Entity configuration for <see cref="NotificationScheduleDbModel"/>.
/// </summary>
public class NotificationScheduleDbModelConfiguration : IEntityTypeConfiguration<NotificationScheduleDbModel>
{
    /// <summary>
    /// Configures notification schedule schema rules.
    /// </summary>
    /// <param name="builder">The entity type builder.</param>
    public void Configure(EntityTypeBuilder<NotificationScheduleDbModel> builder)
    {
        builder.HasKey(x => x.Id);

        builder.Property(x => x.SourceDeviceId).IsRequired();
        builder.Property(x => x.TargetDeviceId).IsRequired();
        builder.Property(x => x.Title).IsRequired().HasMaxLength(140);
        builder.Property(x => x.Body).IsRequired().HasMaxLength(1000);
        builder.Property(x => x.IsEnabled).HasDefaultValue(true);

        builder.HasIndex(x => new { x.UserId, x.TargetDeviceId });

        builder.HasOne<UserDbModel>()
            .WithMany()
            .HasForeignKey(x => x.UserId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}
