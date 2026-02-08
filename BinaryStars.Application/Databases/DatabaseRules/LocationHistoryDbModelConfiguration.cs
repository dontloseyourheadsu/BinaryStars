using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.DatabaseModels.Locations;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

public class LocationHistoryDbModelConfiguration : IEntityTypeConfiguration<LocationHistoryDbModel>
{
    public void Configure(EntityTypeBuilder<LocationHistoryDbModel> builder)
    {
        builder.HasKey(l => l.Id);

        builder.Property(l => l.DeviceId)
            .IsRequired()
            .HasMaxLength(200);

        builder.Property(l => l.Latitude).IsRequired();
        builder.Property(l => l.Longitude).IsRequired();
        builder.Property(l => l.AccuracyMeters).IsRequired(false);
        builder.Property(l => l.RecordedAt).IsRequired();

        builder.HasOne<UserDbModel>()
            .WithMany()
            .HasForeignKey(l => l.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasIndex(l => l.UserId);
        builder.HasIndex(l => l.DeviceId);
        builder.HasIndex(l => new { l.UserId, l.DeviceId, l.RecordedAt });
    }
}
