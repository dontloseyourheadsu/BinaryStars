using Microsoft.Extensions.Logging;
using BinaryStars.Application.Databases.DatabaseModels.Notifications;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Notifications;
using BinaryStars.Domain;

namespace BinaryStars.Application.Services.Notifications;

/// <summary>
/// Request payload for immediate notification send.
/// </summary>
public record SendNotificationRequest(string SenderDeviceId, string TargetDeviceId, string Title, string Body);

/// <summary>
/// Request payload for creating a notification schedule.
/// </summary>
public record CreateNotificationScheduleRequest(
    string SourceDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    bool IsEnabled,
    DateTimeOffset? ScheduledForUtc,
    int? RepeatMinutes);

/// <summary>
/// Request payload for updating a notification schedule.
/// </summary>
public record UpdateNotificationScheduleRequest(
    Guid ScheduleId,
    string SourceDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    bool IsEnabled,
    DateTimeOffset? ScheduledForUtc,
    int? RepeatMinutes);

/// <summary>
/// Notification schedule read model.
/// </summary>
public record NotificationScheduleResponse(
    Guid Id,
    string SourceDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    bool IsEnabled,
    DateTimeOffset? ScheduledForUtc,
    int? RepeatMinutes,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

/// <summary>
/// Read-only notification operations.
/// </summary>
public interface INotificationsReadService
{
    /// <summary>
    /// Gets schedules for a target device.
    /// </summary>
    Task<Result<List<NotificationScheduleResponse>>> GetSchedulesAsync(Guid userId, string targetDeviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Reads the pending-notification-sync flag for a device.
    /// </summary>
    Task<Result<bool>> GetPendingSyncFlagAsync(Guid userId, string deviceId, CancellationToken cancellationToken);
}

/// <summary>
/// Write notification operations.
/// </summary>
public interface INotificationsWriteService
{
    /// <summary>
    /// Creates a schedule.
    /// </summary>
    Task<Result<NotificationScheduleResponse>> CreateScheduleAsync(Guid userId, CreateNotificationScheduleRequest request, CancellationToken cancellationToken);

    /// <summary>
    /// Updates a schedule.
    /// </summary>
    Task<Result<NotificationScheduleResponse>> UpdateScheduleAsync(Guid userId, UpdateNotificationScheduleRequest request, CancellationToken cancellationToken);

    /// <summary>
    /// Deletes a schedule.
    /// </summary>
    Task<Result> DeleteScheduleAsync(Guid userId, Guid scheduleId, CancellationToken cancellationToken);

    /// <summary>
    /// Marks pending notification sync state for a device.
    /// </summary>
    Task<Result> SetPendingSyncFlagAsync(Guid userId, string deviceId, bool value, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for notification scheduling and sync-state updates.
/// </summary>
public class NotificationsService : INotificationsReadService, INotificationsWriteService
{
    private readonly ILogger<NotificationsService> _logger;

    private readonly INotificationScheduleRepository _notificationScheduleRepository;
    private readonly IDeviceRepository _deviceRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotificationsService"/> class.
    /// </summary>
    public NotificationsService(INotificationScheduleRepository notificationScheduleRepository, IDeviceRepository deviceRepository, ILogger<NotificationsService> logger)
    {
        _logger = logger;

        _notificationScheduleRepository = notificationScheduleRepository;
        _deviceRepository = deviceRepository;
    }

    /// <inheritdoc />
    public async Task<Result<List<NotificationScheduleResponse>>> GetSchedulesAsync(Guid userId, string targetDeviceId, CancellationToken cancellationToken)
    {
        var target = await _deviceRepository.GetByIdAsync(targetDeviceId, cancellationToken);
        if (target == null || target.UserId != userId)
            return Result<List<NotificationScheduleResponse>>.Failure("Invalid device.");

        var schedules = await _notificationScheduleRepository.GetByUserAndTargetDeviceAsync(userId, targetDeviceId, cancellationToken);
        return Result<List<NotificationScheduleResponse>>.Success(schedules.Select(Map).ToList());
    }

    /// <inheritdoc />
    public async Task<Result<NotificationScheduleResponse>> CreateScheduleAsync(Guid userId, CreateNotificationScheduleRequest request, CancellationToken cancellationToken)
    {
        var source = await _deviceRepository.GetByIdAsync(request.SourceDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);

        if (source == null || target == null || source.UserId != userId || target.UserId != userId)
            return Result<NotificationScheduleResponse>.Failure("Invalid device.");

        if (!IsPayloadValid(request.Title, request.Body))
            return Result<NotificationScheduleResponse>.Failure("Title and body are required.");

        if (!IsScheduleValid(request.ScheduledForUtc, request.RepeatMinutes))
            return Result<NotificationScheduleResponse>.Failure("Provide either a one-time schedule or recurring schedule settings.");

        var now = DateTimeOffset.UtcNow;
        var model = new NotificationScheduleDbModel
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            SourceDeviceId = request.SourceDeviceId,
            TargetDeviceId = request.TargetDeviceId,
            Title = NormalizeTitle(request.Title),
            Body = NormalizeBody(request.Body),
            IsEnabled = request.IsEnabled,
            ScheduledForUtc = request.ScheduledForUtc,
            RepeatMinutes = request.RepeatMinutes,
            CreatedAt = now,
            UpdatedAt = now,
        };

        await _notificationScheduleRepository.AddAsync(model, cancellationToken);
        target.HasPendingNotificationSync = true;
        await _notificationScheduleRepository.SaveChangesAsync(cancellationToken);

        return Result<NotificationScheduleResponse>.Success(Map(model));
    }

    /// <inheritdoc />
    public async Task<Result<NotificationScheduleResponse>> UpdateScheduleAsync(Guid userId, UpdateNotificationScheduleRequest request, CancellationToken cancellationToken)
    {
        var model = await _notificationScheduleRepository.GetByIdAsync(request.ScheduleId, cancellationToken);
        if (model == null)
            return Result<NotificationScheduleResponse>.Failure("Notification schedule not found.");

        if (model.UserId != userId)
            return Result<NotificationScheduleResponse>.Failure("Unauthorized.");

        var source = await _deviceRepository.GetByIdAsync(request.SourceDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);

        if (source == null || target == null || source.UserId != userId || target.UserId != userId)
            return Result<NotificationScheduleResponse>.Failure("Invalid device.");

        if (!IsPayloadValid(request.Title, request.Body))
            return Result<NotificationScheduleResponse>.Failure("Title and body are required.");

        if (!IsScheduleValid(request.ScheduledForUtc, request.RepeatMinutes))
            return Result<NotificationScheduleResponse>.Failure("Provide either a one-time schedule or recurring schedule settings.");

        model.SourceDeviceId = request.SourceDeviceId;
        model.TargetDeviceId = request.TargetDeviceId;
        model.Title = NormalizeTitle(request.Title);
        model.Body = NormalizeBody(request.Body);
        model.IsEnabled = request.IsEnabled;
        model.ScheduledForUtc = request.ScheduledForUtc?.ToUniversalTime();
        model.RepeatMinutes = request.RepeatMinutes;
        model.UpdatedAt = DateTimeOffset.UtcNow;

        target.HasPendingNotificationSync = true;
        await _notificationScheduleRepository.SaveChangesAsync(cancellationToken);

        return Result<NotificationScheduleResponse>.Success(Map(model));
    }

    /// <inheritdoc />
    public async Task<Result> DeleteScheduleAsync(Guid userId, Guid scheduleId, CancellationToken cancellationToken)
    {
        var model = await _notificationScheduleRepository.GetByIdAsync(scheduleId, cancellationToken);
        if (model == null)
            return Result.Failure("Notification schedule not found.");

        if (model.UserId != userId)
            return Result.Failure("Unauthorized.");

        var target = await _deviceRepository.GetByIdAsync(model.TargetDeviceId, cancellationToken);
        if (target != null && target.UserId == userId)
            target.HasPendingNotificationSync = true;

        await _notificationScheduleRepository.DeleteAsync(model, cancellationToken);
        await _notificationScheduleRepository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }

    /// <inheritdoc />
    public async Task<Result<bool>> GetPendingSyncFlagAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result<bool>.Failure("Invalid device.");

        return Result<bool>.Success(device.HasPendingNotificationSync);
    }

    /// <inheritdoc />
    public async Task<Result> SetPendingSyncFlagAsync(Guid userId, string deviceId, bool value, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result.Failure("Invalid device.");

        device.HasPendingNotificationSync = value;
        await _deviceRepository.SaveChangesAsync(cancellationToken);
        return Result.Success();
    }

    private static NotificationScheduleResponse Map(NotificationScheduleDbModel model)
    {
        return new NotificationScheduleResponse(
            model.Id,
            model.SourceDeviceId,
            model.TargetDeviceId,
            model.Title,
            model.Body,
            model.IsEnabled,
            model.ScheduledForUtc,
            model.RepeatMinutes,
            model.CreatedAt,
            model.UpdatedAt);
    }

    private static bool IsScheduleValid(DateTimeOffset? scheduledForUtc, int? repeatMinutes)
    {
        var hasScheduled = scheduledForUtc.HasValue;
        var hasRepeat = repeatMinutes.HasValue;

        if (!hasScheduled && !hasRepeat)
            return false;

        if (hasRepeat && repeatMinutes <= 0)
            return false;

        return !(hasScheduled && hasRepeat);
    }

    private static bool IsPayloadValid(string title, string body)
    {
        return !string.IsNullOrWhiteSpace(title) && !string.IsNullOrWhiteSpace(body);
    }

    private static string NormalizeTitle(string title)
    {
        var value = title?.Trim() ?? string.Empty;
        return value.Length > 140 ? value[..140] : value;
    }

    private static string NormalizeBody(string body)
    {
        var value = body?.Trim() ?? string.Empty;
        return value.Length > 1000 ? value[..1000] : value;
    }
}
