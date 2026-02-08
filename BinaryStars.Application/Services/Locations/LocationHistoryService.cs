using BinaryStars.Application.Databases.DatabaseModels.Locations;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Locations;
using BinaryStars.Domain;
using BinaryStars.Domain.Errors.Locations;

namespace BinaryStars.Application.Services.Locations;

/// <summary>
/// Request payload for reporting a device location update.
/// </summary>
/// <param name="DeviceId">The device identifier.</param>
/// <param name="Latitude">The recorded latitude.</param>
/// <param name="Longitude">The recorded longitude.</param>
/// <param name="AccuracyMeters">The optional accuracy in meters.</param>
/// <param name="RecordedAt">The timestamp when the location was recorded.</param>
public record LocationUpdateRequest(
    string DeviceId,
    double Latitude,
    double Longitude,
    double? AccuracyMeters,
    DateTimeOffset RecordedAt);

/// <summary>
/// Read model returned for device location history points.
/// </summary>
/// <param name="Id">The history record identifier.</param>
/// <param name="Title">A label for UI display.</param>
/// <param name="RecordedAt">The timestamp when the location was recorded.</param>
/// <param name="Latitude">The latitude.</param>
/// <param name="Longitude">The longitude.</param>
public record LocationHistoryPointResponse(
    Guid Id,
    string Title,
    DateTimeOffset RecordedAt,
    double Latitude,
    double Longitude);

/// <summary>
/// Write-only location history operations.
/// </summary>
public interface ILocationHistoryWriteService
{
    /// <summary>
    /// Adds a new location history entry for a device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="request">The location update request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A success or failure result.</returns>
    Task<Result> AddLocationAsync(Guid userId, LocationUpdateRequest request, CancellationToken cancellationToken);
}

/// <summary>
/// Read-only location history operations.
/// </summary>
public interface ILocationHistoryReadService
{
    /// <summary>
    /// Gets location history for the specified device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="limit">The maximum number of history points to return.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A list of history points or a failure result.</returns>
    Task<Result<List<LocationHistoryPointResponse>>> GetHistoryAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for device location history.
/// </summary>
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

    /// <inheritdoc />
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

    /// <inheritdoc />
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
