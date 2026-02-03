using BinaryStars.Application.Databases.DatabaseModels.Notes;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace BinaryStars.Application.Databases.DatabaseRules;

public class NoteDbModelConfiguration : IEntityTypeConfiguration<NoteDbModel>
{
    public void Configure(EntityTypeBuilder<NoteDbModel> builder)
    {
        builder.HasKey(n => n.Id);

        builder.Property(n => n.Name)
            .IsRequired()
            .HasMaxLength(500);

        builder.Property(n => n.SignedByDeviceId)
            .IsRequired();

        builder.Property(n => n.ContentType)
            .IsRequired();

        // Use JSONB type for PostgreSQL - allows efficient querying of document-like content
        builder.Property(n => n.Content)
            .IsRequired()
            .HasColumnType("jsonb");

        builder.Property(n => n.CreatedAt)
            .IsRequired();

        builder.Property(n => n.UpdatedAt)
            .IsRequired();

        // Foreign key to User with cascade delete
        builder.HasOne<UserDbModel>()
            .WithMany()
            .HasForeignKey(n => n.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Index for common queries
        builder.HasIndex(n => n.UserId);
        builder.HasIndex(n => new { n.UserId, n.CreatedAt });
        builder.HasIndex(n => n.SignedByDeviceId);
    }
}
