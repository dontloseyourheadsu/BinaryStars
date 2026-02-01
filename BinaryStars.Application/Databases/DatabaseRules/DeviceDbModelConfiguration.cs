using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

public class DeviceDbModelConfiguration : IEntityTypeConfiguration<DeviceDbModel>
{
    public void Configure(EntityTypeBuilder<DeviceDbModel> builder)
    {
        builder.HasKey(d => d.Id);

        builder.Property(d => d.Name).IsRequired().HasMaxLength(100);
        builder.Property(d => d.IpAddress).IsRequired();
        builder.Property(d => d.Ipv6Address).IsRequired(false);

        builder.HasOne<UserDbModel>()
            .WithMany()
            .HasForeignKey(d => d.UserId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}
