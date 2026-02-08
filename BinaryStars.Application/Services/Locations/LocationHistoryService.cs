using BinaryStars.Application.Databases.DatabaseModels.Locations;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Locations;
using BinaryStars.Domain;
using BinaryStars.Domain.Errors.Locations;

namespace BinaryStars.Application.Services.Locations;

public record LocationUpdateRequest(
    string DeviceId,
    double Latitude,
    double Longitude,
    double? AccuracyMeters,
    DateTimeOffset RecordedAt);

public record LocationHistoryPointResponse(
    Guid Id,
    string Title,
    DateTimeOffset RecordedAt,
    double Latitude,
    double Longitude);

public interface ILocationHistoryWriteService
{
    Task<Result> AddLocationAsync(Guid userId, LocationUpdateRequest request, CancellationToken cancellationToken);
}

public interface ILocationHistoryReadService
{
    Task<Result<List<LocationHistoryPointResponse>>> GetHistoryAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken);
}

public class LocationHistoryService : ILocationHistoryWriteService, ILocationHistoryReadService
{
    private const int DefaultLimit = 50;
    private readonly ILocationHistoryRepository _repository;
    private readonly IDeviceRepository _deviceRepository;

    public LocationHistoryService(ILocationHistoryRepository repository, IDeviceRepository deviceRepository)
    {
        _repository = repository;
        _deviceRepository = deviceRepository;
    }

    public async Task<Result> AddLocationAsync(Guid userId, LocationUpdateRequest request, CancellationToken cancellationToken)
    {
        if (userId == Guid.Empty)
            return Result.Failure(LocationErrors.UserIdCannotBeEmpty);

        if (string.IsNullOrWhiteSpace(request.DeviceId))
            return Result.Failure(LocationErrors.DeviceIdCannotBeEmpty);

        var device = await _deviceRepository.GetByIdAsync(request.DeviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result.Failure(LocationErrors.DeviceNotLinkedToUser);

        var entry = new LocationHistoryDbModel
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            DeviceId = request.DeviceId,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            AccuracyMeters = request.AccuracyMeters,
            RecordedAt = request.RecordedAt
        };

        await _repository.AddAsync(entry, cancellationToken);
        await _repository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }

    public async Task<Result<List<LocationHistoryPointResponse>>> GetHistoryAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken)
    {
        if (userId == Guid.Empty)
            return Result<List<LocationHistoryPointResponse>>.Failure(LocationErrors.UserIdCannotBeEmpty);

        if (string.IsNullOrWhiteSpace(deviceId))
            return Result<List<LocationHistoryPointResponse>>.Failure(LocationErrors.DeviceIdCannotBeEmpty);

        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result<List<LocationHistoryPointResponse>>.Failure(LocationErrors.DeviceNotLinkedToUser);

        var safeLimit = limit <= 0 ? DefaultLimit : Math.Min(limit, 200);
        var entries = await _repository.GetByUserAndDeviceAsync(userId, deviceId, safeLimit, cancellationToken);

        var responses = entries.Select((entry, index) => new LocationHistoryPointResponse(
            entry.Id,
            index == 0 ? "Last known" : "History",
            entry.RecordedAt,
            entry.Latitude,
            entry.Longitude
        )).ToList();

        return Result<List<LocationHistoryPointResponse>>.Success(responses);
    }
}
