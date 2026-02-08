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

public class ApplicationDbContext : IdentityDbContext<UserDbModel, IdentityRole<Guid>, Guid>
{
    public DbSet<DeviceDbModel> Devices { get; set; }
    public DbSet<NoteDbModel> Notes { get; set; }
    public DbSet<FileTransferDbModel> FileTransfers { get; set; }
    public DbSet<LocationHistoryDbModel> LocationHistory { get; set; }

    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options)
        : base(options)
    {
    }

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
