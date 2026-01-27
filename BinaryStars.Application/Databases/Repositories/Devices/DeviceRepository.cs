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
}
