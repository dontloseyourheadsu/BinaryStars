using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Devices;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Devices;

public class DeviceRepository : IDeviceRepository
{
    private readonly ApplicationDbContext _context;

    public DeviceRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    public Task<List<DeviceDbModel>> GetDevicesByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Devices
            .Where(d => d.UserId == userId)
            .ToListAsync(cancellationToken);
    }

    public Task<DeviceDbModel?> GetByIdAsync(string id, CancellationToken cancellationToken)
    {
        return _context.Devices
            .FirstOrDefaultAsync(d => d.Id == id, cancellationToken);
    }

    public async Task AddAsync(DeviceDbModel device, CancellationToken cancellationToken)
    {
        await _context.Devices.AddAsync(device, cancellationToken);
    }

    public Task DeleteAsync(DeviceDbModel device, CancellationToken cancellationToken)
    {
        _context.Devices.Remove(device);
        return Task.CompletedTask;
    }

    public Task<int> GetCountByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Devices.CountAsync(d => d.UserId == userId, cancellationToken);
    }

    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
