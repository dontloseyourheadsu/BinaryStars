using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Application.Databases.DatabaseModels.Locations;
using BinaryStars.Application.Databases.DatabaseModels.Notes;
using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Application.Databases.DatabaseRules;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Identity.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.DatabaseContexts;

/// <summary>
/// Entity Framework Core context for BinaryStars application data.
/// </summary>
public class ApplicationDbContext : IdentityDbContext<UserDbModel, IdentityRole<Guid>, Guid>
{
    /// <summary>
    /// Gets or sets the device records.
    /// </summary>
    public DbSet<DeviceDbModel> Devices { get; set; }

    /// <summary>
    /// Gets or sets the note records.
    /// </summary>
    public DbSet<NoteDbModel> Notes { get; set; }

    /// <summary>
    /// Gets or sets the file transfer records.
    /// </summary>
    public DbSet<FileTransferDbModel> FileTransfers { get; set; }

    /// <summary>
    /// Gets or sets the location history records.
    /// </summary>
    public DbSet<LocationHistoryDbModel> LocationHistory { get; set; }

    /// <summary>
    /// Initializes a new instance of the <see cref="ApplicationDbContext"/> class.
    /// </summary>
    /// <param name="options">The context options.</param>
    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options)
        : base(options)
    {
    }

    /// <summary>
    /// Configures entity mappings and database rules.
    /// </summary>
    /// <param name="builder">The model builder.</param>
    protected override void OnModelCreating(ModelBuilder builder)
    {
        base.OnModelCreating(builder);

        builder.ApplyConfiguration(new UserDbModelConfiguration());
        builder.ApplyConfiguration(new DeviceDbModelConfiguration());
        builder.ApplyConfiguration(new NoteDbModelConfiguration());
        builder.ApplyConfiguration(new FileTransferDbModelConfiguration());
        builder.ApplyConfiguration(new LocationHistoryDbModelConfiguration());
    }
}
