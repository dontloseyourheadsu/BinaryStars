using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Devices;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Devices;

/// <summary>
/// Entity Framework repository for device persistence.
/// </summary>
public class DeviceRepository : IDeviceRepository
{
    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="DeviceRepository"/> class.
    /// </summary>
    /// <param name="context">The application database context.</param>
    public DeviceRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    /// <inheritdoc />
    public Task<List<DeviceDbModel>> GetDevicesByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Devices
            .Where(d => d.UserId == userId)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<DeviceDbModel?> GetByIdAsync(string id, CancellationToken cancellationToken)
    {
        return _context.Devices
            .FirstOrDefaultAsync(d => d.Id == id, cancellationToken);
    }

    /// <inheritdoc />
    public async Task AddAsync(DeviceDbModel device, CancellationToken cancellationToken)
    {
        await _context.Devices.AddAsync(device, cancellationToken);
    }

    /// <inheritdoc />
    public Task DeleteAsync(DeviceDbModel device, CancellationToken cancellationToken)
    {
        _context.Devices.Remove(device);
        return Task.CompletedTask;
    }

    /// <inheritdoc />
    public Task<int> GetCountByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Devices.CountAsync(d => d.UserId == userId, cancellationToken);
    }

    /// <inheritdoc />
    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
