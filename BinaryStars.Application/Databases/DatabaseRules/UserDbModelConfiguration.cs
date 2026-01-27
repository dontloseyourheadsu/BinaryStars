using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

public class UserDbModelConfiguration : IEntityTypeConfiguration<UserDbModel>
{
    public void Configure(EntityTypeBuilder<UserDbModel> builder)
    {
        // Identity configurations are usually handled by IdentityDbContext, 
        // but we can add specific ones here if needed.
        builder.Property(u => u.Role).IsRequired();
    }
}
