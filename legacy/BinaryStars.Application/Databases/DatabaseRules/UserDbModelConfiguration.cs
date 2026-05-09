using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

/// <summary>
/// Entity configuration for <see cref="UserDbModel"/>.
/// </summary>
public class UserDbModelConfiguration : IEntityTypeConfiguration<UserDbModel>
{
    /// <summary>
    /// Configures the user entity schema rules.
    /// </summary>
    /// <param name="builder">The entity type builder.</param>
    public void Configure(EntityTypeBuilder<UserDbModel> builder)
    {
        // Identity configurations are usually handled by IdentityDbContext, 
        // but we can add specific ones here if needed.
        builder.Property(u => u.Role).IsRequired();
    }
}
